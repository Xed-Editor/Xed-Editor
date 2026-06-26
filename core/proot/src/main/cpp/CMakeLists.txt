cmake_minimum_required(VERSION 3.10)
project(proot C ASM)

# Compatibility flags and headers
add_definitions(-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -D__STDC_WANT_LIB_EXT1__=1)
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${CMAKE_CURRENT_SOURCE_DIR}/talloc)

# Compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra -O2 -g0")
# Reproducibility flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=. -fno-ident")
set(CMAKE_ASM_FLAGS "${CMAKE_ASM_FLAGS} -ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=. -g0")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,noexecstack -Wl,--build-id=none")

# Tools from environment (NDK)
find_program(OBJCOPY_TOOL NAMES ${CMAKE_OBJCOPY} $ENV{OBJCOPY} llvm-objcopy objcopy)
find_program(OBJDUMP_TOOL NAMES ${CMAKE_OBJDUMP} $ENV{OBJDUMP} llvm-objdump objdump)
find_program(STRIP_TOOL NAMES ${CMAKE_STRIP} $ENV{STRIP} llvm-strip strip)
find_program(READELF_TOOL NAMES ${CMAKE_READELF} llvm-readelf readelf)

if(NOT OBJCOPY_TOOL OR NOT OBJDUMP_TOOL OR NOT STRIP_TOOL OR NOT READELF_TOOL)
    message(FATAL_ERROR "Could not find required binary tools (objcopy, objdump, strip, readelf)")
endif()

# Feature Detection (build.h)
include(CheckCSourceCompiles)

add_definitions(-DPROOT_UNBUNDLE_LOADER)

function(check_feature FEATURE_NAME SOURCE_CODE DEFINE_VAR)
    check_c_source_compiles("${SOURCE_CODE}" ${DEFINE_VAR})
    if(${${DEFINE_VAR}})
        set(${DEFINE_VAR}_STR "#define HAVE_${FEATURE_NAME}" PARENT_SCOPE)
    else()
        set(${DEFINE_VAR}_STR "" PARENT_SCOPE)
    endif()
endfunction()

check_feature(PROCESS_VM "
#define _GNU_SOURCE
#include <sys/uio.h>
int main(void) {
    process_vm_readv(0, (const struct iovec *)0, 0, (const struct iovec *)0, 0, 0);
    return 0;
}" HAVE_PROCESS_VM)

check_feature(SECCOMP_FILTER "
#include <linux/seccomp.h>
#include <linux/filter.h>
#include <linux/audit.h>
#include <sys/ptrace.h>
#include <sys/prctl.h>
int main(void) {
    prctl(PR_SET_SECCOMP, SECCOMP_MODE_FILTER, 0);
    return 0;
}" HAVE_SECCOMP_FILTER)

# Version Detection
execute_process(
    COMMAND git describe --tags --dirty --abbrev=8 --always
    OUTPUT_VARIABLE GIT_VERSION
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_QUIET
)
if(NOT GIT_VERSION)
    set(GIT_VERSION "unknown")
endif()

# Generate build.h
set(BUILD_H_CONTENT "/* This file is auto-generated, edit at your own risk.  */\n")
set(BUILD_H_CONTENT "${BUILD_H_CONTENT}#ifndef BUILD_H\n#define BUILD_H\n")
set(BUILD_H_CONTENT "${BUILD_H_CONTENT}#undef VERSION\n#define VERSION \"${GIT_VERSION}\"\n")
set(BUILD_H_CONTENT "${BUILD_H_CONTENT}${HAVE_PROCESS_VM_STR}\n")
set(BUILD_H_CONTENT "${BUILD_H_CONTENT}${HAVE_SECCOMP_FILTER_STR}\n")
set(BUILD_H_CONTENT "${BUILD_H_CONTENT}#endif /* BUILD_H */\n")
file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/build.h "${BUILD_H_CONTENT}")
include_directories(${CMAKE_CURRENT_BINARY_DIR})

# Architecture-specific info from arch.h
function(get_arch_define DEFINE_NAME VAR_NAME EXTRA_CFLAGS)
    set(FLAGS "")
    if(CMAKE_C_COMPILER_TARGET)
        list(APPEND FLAGS "--target=${CMAKE_C_COMPILER_TARGET}")
    endif()
    if(CMAKE_SYSROOT)
        list(APPEND FLAGS "--sysroot=${CMAKE_SYSROOT}")
    endif()
    separate_arguments(C_FLAGS_LIST NATIVE_COMMAND "${CMAKE_C_FLAGS}")
    list(APPEND FLAGS ${C_FLAGS_LIST})
    if(EXTRA_CFLAGS)
        separate_arguments(EXTRA_FLAGS_LIST NATIVE_COMMAND "${EXTRA_CFLAGS}")
        list(APPEND FLAGS ${EXTRA_FLAGS_LIST})
    endif()

    execute_process(
        COMMAND ${CMAKE_C_COMPILER} ${FLAGS} -E -dM -DNO_LIBC_HEADER ${CMAKE_CURRENT_SOURCE_DIR}/arch.h
        OUTPUT_VARIABLE PREPROCESSOR_OUTPUT
        ERROR_VARIABLE PREPROCESSOR_ERROR
    )
    if(PREPROCESSOR_OUTPUT MATCHES "#define ${DEFINE_NAME} ([^\n]+)")
        set(VAL "${CMAKE_MATCH_1}")
        string(STRIP "${VAL}" VAL)
        set(${VAR_NAME} "${VAL}" PARENT_SCOPE)
    else()
        set(${VAR_NAME} "" PARENT_SCOPE)
    endif()
endfunction()

# We always set these to TRUE for the sake of setting up build rules.
# The actual activation happens in the C code via #ifdef.
set(HAS_POKEDATA_WORKAROUND TRUE)

get_arch_define(HAS_LOADER_32BIT HAS_LOADER_32BIT_VAL "")
if(HAS_LOADER_32BIT_VAL)
    set(HAS_LOADER_32BIT TRUE)
else()
    set(HAS_LOADER_32BIT FALSE)
endif()

get_arch_define(LOADER_ADDRESS LOADER_ADDRESS_64 "")
get_arch_define(LOADER_ARCH_CFLAGS LOADER_ARCH_CFLAGS_64 "")

if(ANDROID)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(M32_FLAG "--target=armv7a-linux-androideabi${ANDROID_PLATFORM_LEVEL}")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(M32_FLAG "--target=i686-linux-android${ANDROID_PLATFORM_LEVEL}")
    else()
        set(M32_FLAG "-m32")
    endif()
else()
    set(M32_FLAG "-m32")
endif()

if(HAS_LOADER_32BIT)
    get_arch_define(LOADER_ADDRESS LOADER_ADDRESS_32 "${M32_FLAG}")
    get_arch_define(LOADER_ARCH_CFLAGS LOADER_ARCH_CFLAGS_32 "${M32_FLAG}")
endif()

# Check if 32-bit loader can actually be built
if(HAS_LOADER_32BIT)
    if(ANDROID)
        set(COMPILER_SUPPORTS_M32 true)
    else()
        include(CheckCCompilerFlag)
        check_c_compiler_flag("${M32_FLAG}" COMPILER_SUPPORTS_M32)
    endif()
    if(NOT COMPILER_SUPPORTS_M32 OR LOADER_ADDRESS_32 STREQUAL "")
        message(STATUS "Disabling 32-bit loader: ${M32_FLAG} not supported or address unknown")
        set(HAS_LOADER_32BIT false)
    endif()
endif()

# Talloc sources
set(TALLOC_SOURCES
    talloc/talloc.c
    talloc/replace.c
    talloc/closefrom.c
)

# PRoot sources
set(PROOT_SOURCES
    cli/cli.c
    cli/proot.c
    cli/note.c
    execve/enter.c
    execve/exit.c
    execve/shebang.c
    execve/elf.c
    execve/ldso.c
    execve/auxv.c
    execve/aoxp.c
    path/binding.c
    path/glue.c
    path/canon.c
    path/f2fs-bug.c
    path/path.c
    path/proc.c
    path/temp.c
    syscall/seccomp.c
    syscall/syscall.c
    syscall/chain.c
    syscall/enter.c
    syscall/exit.c
    syscall/sysnum.c
    syscall/socket.c
    syscall/heap.c
    syscall/rlimit.c
    tracee/tracee.c
    tracee/mem.c
    tracee/reg.c
    tracee/event.c
    tracee/seccomp.c
    tracee/statx.c
    ptrace/ptrace.c
    ptrace/user.c
    ptrace/wait.c
    extension/extension.c
    extension/ashmem_memfd/ashmem_memfd.c
    extension/kompat/kompat.c
    extension/fake_id0/chown.c
    extension/fake_id0/chroot.c
    extension/fake_id0/getsockopt.c
    extension/fake_id0/sendmsg.c
    extension/fake_id0/socket.c
    extension/fake_id0/open.c
    extension/fake_id0/unlink.c
    extension/fake_id0/rename.c
    extension/fake_id0/chmod.c
    extension/fake_id0/utimensat.c
    extension/fake_id0/access.c
    extension/fake_id0/exec.c
    extension/fake_id0/link.c
    extension/fake_id0/symlink.c
    extension/fake_id0/mk.c
    extension/fake_id0/stat.c
    extension/fake_id0/helper_functions.c
    extension/fake_id0/fake_id0.c
    extension/hidden_files/hidden_files.c
    extension/mountinfo/mountinfo.c
    extension/port_switch/port_switch.c
    extension/sysvipc/sysvipc.c
    extension/sysvipc/sysvipc_msg.c
    extension/sysvipc/sysvipc_sem.c
    extension/sysvipc/sysvipc_shm.c
    extension/link2symlink/link2symlink.c
    extension/fix_symlink_size/fix_symlink_size.c
)

# We need a probe object to determine objcopy parameters
add_library(cli_obj OBJECT cli/cli.c)
set_target_properties(cli_obj PROPERTIES COMPILE_FLAGS "-I${CMAKE_CURRENT_BINARY_DIR}")

# Build loaders
function(build_loader SUFFIX EXTRA_CFLAGS LOADER_ADDR)
    set(LOADER_TARGET loader_${SUFFIX})
    if(SUFFIX STREQUAL "64")
        set(LOADER_NAME loader)
    else()
        set(LOADER_NAME loader32)
    endif()

    add_executable(${LOADER_TARGET} loader/loader.c loader/assembly.S)
    set_target_properties(${LOADER_TARGET} PROPERTIES 
        COMPILE_FLAGS "-fPIC -ffreestanding -g0 -ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=. -ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=. ${EXTRA_CFLAGS}"
        LINK_FLAGS "-static -nostdlib ${EXTRA_CFLAGS} -Wl,-Ttext=${LOADER_ADDR},--rosegment,-z,noexecstack"
        RUNTIME_OUTPUT_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/loader
        OUTPUT_NAME ${LOADER_NAME}
        PREFIX "lib"
        SUFFIX ".so"
    )

endfunction()

build_loader("64" "${LOADER_ARCH_CFLAGS_64}" "${LOADER_ADDRESS_64}")

if(HAS_LOADER_32BIT)
    build_loader("-m32" "${LOADER_ARCH_CFLAGS_32}" "${LOADER_ADDRESS_32}")
endif()

if(HAS_POKEDATA_WORKAROUND)
    # Loader info generation
    set(LOADER_INFO_C "${CMAKE_CURRENT_BINARY_DIR}/loader/loader-info.c")
    add_custom_command(
        OUTPUT "${LOADER_INFO_C}"
        COMMAND ${READELF_TOOL} -s "$<TARGET_FILE:loader_64>" | awk -f "${CMAKE_CURRENT_SOURCE_DIR}/loader/loader-info.awk" > "${LOADER_INFO_C}"
        DEPENDS loader_64
        WORKING_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}"
        VERBATIM
    )
    list(APPEND PROOT_SOURCES "${LOADER_INFO_C}")
endif()

add_executable(proot ${PROOT_SOURCES} ${TALLOC_SOURCES})
add_dependencies(proot cli_obj loader_64)
if(HAS_LOADER_32BIT)
    add_dependencies(proot loader_-m32)
endif()

target_link_libraries(proot dl)

# Rename the output to libproot.so for Android packaging
set_target_properties(proot PROPERTIES
    OUTPUT_NAME "proot"
    PREFIX "lib"
    SUFFIX ".so"
)

add_custom_command(
    TARGET proot POST_BUILD
    COMMAND ${STRIP_TOOL} --strip-unneeded $<TARGET_FILE:proot>
    COMMAND ${STRIP_TOOL} $<TARGET_FILE:loader_64>
    COMMENT "Stripping libproot.so and loader"
)

if(HAS_LOADER_32BIT)
    add_custom_command(
        TARGET proot POST_BUILD
        COMMAND ${STRIP_TOOL} $<TARGET_FILE:loader_-m32>
        COMMENT "Stripping loader32"
    )
endif()

install(TARGETS proot DESTINATION bin)

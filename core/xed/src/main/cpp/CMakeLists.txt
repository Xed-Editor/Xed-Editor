cmake_minimum_required(VERSION 3.10)
project(termux-exec C)

# Set default compiler to clang if not specified
if(NOT CMAKE_C_COMPILER)
    set(CMAKE_C_COMPILER clang)
endif()

# Set C standard
set(CMAKE_C_STANDARD 17)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Default values for configuration variables
set(TERMUX_BASE_DIR "/data/data/com.termux/files" CACHE STRING "Termux base directory")
set(TERMUX_PREFIX "" CACHE STRING "Termux prefix")

# Options for build variants
option(SANITIZE "Enable AddressSanitizer" OFF)
option(HOST_BUILD "Enable host build flags" OFF)

# Source files
set(C_SOURCE
        src/termux-exec.c
        src/exec-variants.c
        src/termux-readlink.c
)

# Create shared library
add_library(termux-exec SHARED ${C_SOURCE})

# Set output name to match original
set_target_properties(termux-exec PROPERTIES
        OUTPUT_NAME "termux-exec"
        PREFIX "lib"
        SUFFIX ".so"
)

# Compiler flags
target_compile_options(termux-exec PRIVATE
        -Wall
        -Wextra
        -Werror
        -Wshadow
        -fvisibility=hidden
)

# Conditional compilation flags
if(SANITIZE)
    target_compile_options(termux-exec PRIVATE
            -O1
            -g
            -fsanitize=address
            -fno-omit-frame-pointer
    )
    target_link_options(termux-exec PRIVATE
            -fsanitize=address
    )
else()
    target_compile_options(termux-exec PRIVATE -O2)
endif()

if(HOST_BUILD)
    target_compile_options(termux-exec PRIVATE
            -Wno-error=tautological-pointer-compare
    )
endif()

# Preprocessor definitions
target_compile_definitions(termux-exec PRIVATE
        TERMUX_BASE_DIR="${TERMUX_BASE_DIR}"
)

if(TERMUX_PREFIX)
    target_compile_definitions(termux-exec PRIVATE
            TERMUX_PREFIX="${TERMUX_PREFIX}"
    )
endif()

# Installation
install(TARGETS termux-exec
        LIBRARY DESTINATION lib
)
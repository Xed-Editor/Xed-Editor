cmake_minimum_required(VERSION 3.22.1)
project("link2symlink")

add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        link2symlink.cpp)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=."
        "-ffile-prefix-map=${CMAKE_CURRENT_BINARY_DIR}=."
        "-g0"
        "-fno-ident"
        "-fno-exceptions"
        "-fno-rtti"
        "-Oz"
        "-DNDEBUG")

target_link_libraries(${CMAKE_PROJECT_NAME}
        android log)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-Wl,-s"
        "-Wl,--build-id=none"
        "-Wl,--gc-sections"
        "-Wl,-z,noexecstack")

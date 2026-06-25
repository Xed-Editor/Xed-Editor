cmake_minimum_required(VERSION 3.22.1)
project("link2symlink")
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        link2symlink.cpp)

add_compile_options("-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
add_compile_options("-fno-ident")

target_link_libraries(${CMAKE_PROJECT_NAME}
        android log)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-s" "-Wl,--build-id=none")

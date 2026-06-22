cmake_minimum_required(VERSION 3.22.1)
project("link2symlink")
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        link2symlink.cpp)

# Ensure reproducible builds by mapping absolute source paths to relative ones in debug info
if (CMAKE_C_COMPILER_ID MATCHES "GNU|Clang" OR CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    add_compile_options("-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
endif()


if(ANDROID)
    target_link_libraries(${CMAKE_PROJECT_NAME}
            android
            log)
endif()
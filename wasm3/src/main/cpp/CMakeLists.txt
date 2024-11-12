cmake_minimum_required(VERSION 3.22.1)

project("wasm3")


# Modified optimization flags - removed LTO and adjusted other flags
add_definitions(-DANDROID
        -Wno-format-security
        -fomit-frame-pointer
        -fno-stack-check
        -fno-stack-protector)

# Gather all source files
file(GLOB M3_SRC FOLLOW_SYMLINKS "m3/*.c" "*.c")

# Create shared library with all source files
add_library(${CMAKE_PROJECT_NAME} SHARED
        ${M3_SRC}
        wasm3.cpp)

# Removed -flto from link flags and reduced optimization level
set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES
        LINK_FLAGS "-O3")

# Link required libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)
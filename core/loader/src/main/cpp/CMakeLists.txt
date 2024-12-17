cmake_minimum_required(VERSION 3.22.1)

project("loader")

add_library(${CMAKE_PROJECT_NAME} SHARED
        script.h
        assembly.S
        arch.h
        attribute.h
        compat.h
        loader.c)

target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log)
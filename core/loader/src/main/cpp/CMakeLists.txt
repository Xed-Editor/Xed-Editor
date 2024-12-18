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
        android)

add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
        COMMENT "Stripping binary to reduce size")
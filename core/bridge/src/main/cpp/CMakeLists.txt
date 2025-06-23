cmake_minimum_required(VERSION 3.22.1)
project("bridge")

add_executable(bridge
        bridge.cpp
        socket.cpp
)

set_target_properties(bridge PROPERTIES
        OUTPUT_NAME "bridge"
        PREFIX "lib"
        SUFFIX ".so"
)


target_compile_options(bridge PRIVATE
        "-ffile-prefix-map=${CMAKE_SOURCE_DIR}=."
)

target_link_options(bridge PRIVATE
        "-Wl,--build-id=none"
        "-s"
)

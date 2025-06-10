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

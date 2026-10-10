cmake_minimum_required(VERSION 3.22.1)

project("nexus_presentation_engine")

include_directories(include)

add_library(
    nexus_presentation_engine
    SHARED
    lokit_jni_bridge.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(android-lib android)

target_link_libraries(
    nexus_presentation_engine
    ${log-lib}
    ${jnigraphics-lib}
    ${android-lib}
)

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

# Enforce 16 KB ELF segment alignment for Android 15+ compatibility
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
add_link_options("-Wl,-z,max-page-size=16384")

target_link_options(
    nexus_presentation_engine
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)

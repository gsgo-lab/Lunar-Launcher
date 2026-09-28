cmake_minimum_required(VERSION 3.22.1)
project(lunar_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(lunar_jvm SHARED lunar_jvm.cpp)
add_library(lunar_graphics SHARED lunar_graphics.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(lunar_jvm ${log-lib} ${android-lib} dl)
target_link_libraries(lunar_graphics ${log-lib} ${android-lib} EGL GLESv3)

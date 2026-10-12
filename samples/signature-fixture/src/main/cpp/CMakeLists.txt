cmake_minimum_required(VERSION 3.22)
project(signature_fixture LANGUAGES CXX)
add_library(signature_fixture SHARED signature_fixture.cpp)
target_link_libraries(signature_fixture PRIVATE z)

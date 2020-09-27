cmake_minimum_required(VERSION 3.4.1)

add_library(native-lib SHARED native-lib.cpp)
add_library(jni_utils SHARED jni_utils.cpp)
  
find_library(log-lib log)

target_link_libraries(native-lib ${log-lib})
target_link_libraries(jni_utils ${log-lib})
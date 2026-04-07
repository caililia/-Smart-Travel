cmake_minimum_required(VERSION 3.10.2)
project("ncnn_yolov8")

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wno-error=unused-variable -Wno-error=unused-parameter")

add_library(ncnn STATIC IMPORTED)
set_target_properties(ncnn PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libncnn.a)

add_library(ncnn_yolov8 SHARED yolo_v8ncnn.cpp)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(ncnn_yolov8 PRIVATE
        ${CMAKE_SOURCE_DIR}/src/main/cpp/include # 如果你把 ncnn headers 放这里
        )

target_link_libraries(ncnn_yolov8 ${log-lib} ${android-lib} ncnn)

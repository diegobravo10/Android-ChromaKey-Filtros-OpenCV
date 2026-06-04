# CMakeLists.txt — Práctica UPS-GIIATA, Parte 1-A
cmake_minimum_required(VERSION 3.22.1)
project("aplicacionsec")

# Ruta al SDK nativo de OpenCV 4.11.0
# Puede sobreescribirse con -DOpenCV_DIR=<ruta> desde app/build.gradle.kts
if(NOT DEFINED OpenCV_DIR)
    set(OpenCV_DIR "/home/user/librerias/OpenCV-android-sdk/sdk/native/jni")
endif()

# Módulos usados: core (Mat, tipos base) + imgproc (cvtColor, split, merge, addWeighted, resize)
find_package(OpenCV 4.11 REQUIRED core imgproc)

# Biblioteca nativa compartida
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

# C++17 con RTTI y excepciones (requeridos por OpenCV)
target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -std=c++17
        -frtti
        -fexceptions
        -O2)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${OpenCV_INCLUDE_DIRS})

# log: __android_log_print → Logcat
find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${OpenCV_LIBS}
        ${log-lib})

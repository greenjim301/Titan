# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
             native-lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/k_util/k_errno.cpp
             src/main/cpp/k_util/k_event.cpp
             src/main/cpp/k_util/k_handler.cpp
             src/main/cpp/k_util/k_mutex.cpp
             src/main/cpp/k_util/k_sockaddr.cpp
             src/main/cpp/k_util/k_socket.cpp
             src/main/cpp/k_util/k_string.cpp
             src/main/cpp/k_util/k_thread_task.cpp
             src/main/cpp/k_util/k_util.cpp
             src/main/cpp/k_rtp_sender.cpp
             src/main/cpp/k_media_sender.cpp
             src/main/cpp/k_server_conn.cpp
             src/main/cpp/k_server_conn_handler.cpp
             src/main/cpp/libyuv/source/compare.cc
             src/main/cpp/libyuv/source/compare_common.cc
             src/main/cpp/libyuv/source/compare_gcc.cc
             src/main/cpp/libyuv/source/compare_msa.cc
             src/main/cpp/libyuv/source/compare_neon.cc
             src/main/cpp/libyuv/source/compare_neon64.cc
             src/main/cpp/libyuv/source/convert.cc
             src/main/cpp/libyuv/source/convert_argb.cc
             src/main/cpp/libyuv/source/convert_from.cc
             src/main/cpp/libyuv/source/convert_from_argb.cc
             src/main/cpp/libyuv/source/convert_to_argb.cc
             src/main/cpp/libyuv/source/convert_to_i420.cc
             src/main/cpp/libyuv/source/cpu_id.cc
             src/main/cpp/libyuv/source/planar_functions.cc
             src/main/cpp/libyuv/source/rotate.cc
             src/main/cpp/libyuv/source/rotate_any.cc
             src/main/cpp/libyuv/source/rotate_argb.cc
             src/main/cpp/libyuv/source/rotate_common.cc
             src/main/cpp/libyuv/source/rotate_gcc.cc
             src/main/cpp/libyuv/source/rotate_msa.cc
             src/main/cpp/libyuv/source/rotate_neon.cc
             src/main/cpp/libyuv/source/rotate_neon64.cc
             src/main/cpp/libyuv/source/row_any.cc
             src/main/cpp/libyuv/source/row_common.cc
             src/main/cpp/libyuv/source/row_gcc.cc
             src/main/cpp/libyuv/source/row_msa.cc
             src/main/cpp/libyuv/source/row_neon.cc
             src/main/cpp/libyuv/source/row_neon64.cc
             src/main/cpp/libyuv/source/scale.cc
             src/main/cpp/libyuv/source/scale_any.cc
             src/main/cpp/libyuv/source/scale_argb.cc
             src/main/cpp/libyuv/source/scale_common.cc
             src/main/cpp/libyuv/source/scale_gcc.cc
             src/main/cpp/libyuv/source/scale_msa.cc
             src/main/cpp/libyuv/source/scale_neon.cc
             src/main/cpp/libyuv/source/scale_neon64.cc
             src/main/cpp/libyuv/source/video_common.cc
             src/main/cpp/jsoncpp/jsoncpp.cpp
             src/main/cpp/native-lib.cpp )

include_directories(src/main/cpp/
src/main/cpp/libyuv/include/
src/main/cpp/jsoncpp/)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       native-lib

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
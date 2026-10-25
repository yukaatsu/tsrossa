# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file LICENSE.rst or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION ${CMAKE_VERSION}) # this file comes with cmake

# If CMAKE_DISABLE_SOURCE_CHANGES is set to true and the source directory is an
# existing directory in our source tree, calling file(MAKE_DIRECTORY) on it
# would cause a fatal error, even though it would be a no-op.
if(NOT EXISTS "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-src")
  file(MAKE_DIRECTORY "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-src")
endif()
file(MAKE_DIRECTORY
  "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-build"
  "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix"
  "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix/tmp"
  "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix/src/libusb-populate-stamp"
  "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix/src"
  "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix/src/libusb-populate-stamp"
)

set(configSubDirs )
foreach(subDir IN LISTS configSubDirs)
    file(MAKE_DIRECTORY "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix/src/libusb-populate-stamp/${subDir}")
endforeach()
if(cfgdir)
  file(MAKE_DIRECTORY "/home/yuka/Documents/Project/Music Player Mobile/app/src/main/cpp/build/_deps/libusb-subbuild/libusb-populate-prefix/src/libusb-populate-stamp${cfgdir}") # cfgdir has leading slash
endif()

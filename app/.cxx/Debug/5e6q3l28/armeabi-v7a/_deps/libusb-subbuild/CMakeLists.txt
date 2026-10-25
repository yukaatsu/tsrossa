# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION 3.22.1-g37088a8)

# We name the project and the target for the ExternalProject_Add() call
# to something that will highlight to the user what we are working on if
# something goes wrong and an error message is produced.

project(libusb-populate NONE)


# Pass through things we've already detected in the main project to avoid
# paying the cost of redetecting them again in ExternalProject_Add()
set(GIT_EXECUTABLE [==[/usr/bin/git]==])
set(GIT_VERSION_STRING [==[2.50.1 (Apple Git-155)]==])
set_property(GLOBAL PROPERTY _CMAKE_FindGit_GIT_EXECUTABLE_VERSION
  [==[/usr/bin/git;2.50.1 (Apple Git-155)]==]
)


include(ExternalProject)
ExternalProject_Add(libusb-populate
                     "UPDATE_DISCONNECTED" "False" "GIT_REPOSITORY" "https://github.com/libusb/libusb.git" "GIT_TAG" "master"
                    SOURCE_DIR          "/Users/yuka/Projects/tsrossa-ujicoba/app/.cxx/Debug/5e6q3l28/armeabi-v7a/_deps/libusb-src"
                    BINARY_DIR          "/Users/yuka/Projects/tsrossa-ujicoba/app/.cxx/Debug/5e6q3l28/armeabi-v7a/_deps/libusb-build"
                    CONFIGURE_COMMAND   ""
                    BUILD_COMMAND       ""
                    INSTALL_COMMAND     ""
                    TEST_COMMAND        ""
                    USES_TERMINAL_DOWNLOAD  YES
                    USES_TERMINAL_UPDATE    YES
)



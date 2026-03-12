cmake_minimum_required(VERSION 3.16)

project(DTECH_MUSIC VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

# Find Qt6 components
find_package(Qt6 REQUIRED COMPONENTS Core Gui Widgets Network Sql Multimedia)

# Determine the executable name based on the user request
set(EXEC_NAME "DTECH_MUSIC")

# Source files
set(PROJECT_SOURCES
    src/main/cpp/main.cpp
    src/main/cpp/MainWindow.cpp
    src/main/cpp/MainWindow.h
    src/main/cpp/DatabaseManager.cpp
    src/main/cpp/DatabaseManager.h
    src/main/cpp/NetworkClient.cpp
    src/main/cpp/NetworkClient.h
    src/main/cpp/MusicPlayer.cpp
    src/main/cpp/MusicPlayer.h
    src/main/cpp/InnerTubeClient.cpp
    src/main/cpp/InnerTubeClient.h
)

if(${CMAKE_VERSION} VERSION_LESS "3.19.0")
    # Add executable with WIN32 so a console window doesn't appear
    add_executable(${EXEC_NAME} WIN32 ${PROJECT_SOURCES})
else()
    # Add executable with WIN32 so a console window doesn't appear
    add_executable(${EXEC_NAME} WIN32 ${PROJECT_SOURCES})
    set_property(TARGET ${EXEC_NAME} PROPERTY CXX_STANDARD 17)
endif()

# Link Qt libraries
target_link_libraries(${EXEC_NAME} PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Widgets
    Qt6::Network
    Qt6::Sql
    Qt6::Multimedia
)

# Deployment setup - use windeployqt automatically during CMake build if available
if(WIN32)
    # Get the location of windeployqt
    get_target_property(_qmake_executable Qt6::qmake IMPORTED_LOCATION)
    get_filename_component(_qt_bin_dir "${_qmake_executable}" DIRECTORY)
    find_program(WINDEPLOYQT_EXECUTABLE windeployqt HINTS "${_qt_bin_dir}")

    if(WINDEPLOYQT_EXECUTABLE)
        add_custom_command(TARGET ${EXEC_NAME} POST_BUILD
            COMMAND "${WINDEPLOYQT_EXECUTABLE}"
            ARGS --release --no-translations --compiler-runtime "$<TARGET_FILE:${EXEC_NAME}>"
            COMMENT "Running windeployqt..."
        )
    endif()
endif()

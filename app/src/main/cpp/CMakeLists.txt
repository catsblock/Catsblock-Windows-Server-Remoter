cmake_minimum_required(VERSION 3.22.1)
project(wsr C)
file(GLOB_RECURSE WSR_LIBS "${WSR_DEPS}/lib/*.a")
add_library(wsr SHARED wsr.c)
target_include_directories(wsr PRIVATE
    ${WSR_DEPS}/include
    ${WSR_DEPS}/include/freerdp3
    ${WSR_DEPS}/include/winpr3)
target_link_libraries(wsr
    -Wl,--start-group ${WSR_LIBS} -Wl,--end-group
    log android jnigraphics z m)

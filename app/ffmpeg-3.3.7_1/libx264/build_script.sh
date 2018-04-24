#!/bin/bash
NDK=/home/qianxiangsen/android-ndk-r15c
PLATFORM=$NDK/platforms/android-19/arch-arm
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
PREFIX=./android/arm

EXTRA_CFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=neon -D__ARM_ARCH_7__ -D__ARM_ARCH_7A__"

function build_one
{
./configure \
--prefix=$PREFIX \
--enable-static \
--enable-pic \
--enable-strip \
--host=arm-linux-androideabi \
--cross-prefix=$TOOLCHAIN/bin/arm-linux-androideabi- \
--sysroot=$PLATFORM \
--extra-cflags="-Os -fpic $EXTRA_CFLAGS" \
--extra-ldflags="" \

$ADDITIONAL_CONFIGURE_FLAG
make clean
make -j4
make install

}
build_one


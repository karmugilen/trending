#!/bin/sh
# For aarch64 hosts (like a phone), where the NDK's x86-64 clang cannot run.
# Host clang drives the NDK sysroot. Gradle passes ANDROID_NDK_HOME.
ndk=${ANDROID_NDK_HOME:-/root/Android/Sdk/ndk/27.2.12479018}
sysroot=$(echo "$ndk"/toolchains/llvm/prebuilt/*/sysroot | cut -d' ' -f1)
exec /usr/bin/clang --target=aarch64-linux-android26 --sysroot="$sysroot" -fuse-ld=lld "$@"

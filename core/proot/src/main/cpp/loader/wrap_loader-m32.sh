#!/bin/sh
set -e
CLI_OBJ="/home/rohit/Projects/proot/proot/CMakeFiles/cli_obj.dir/cli/cli.c.o"
OUTPUT_TARGET=$(/home/rohit/Android/Sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump -f "$CLI_OBJ" | grep 'file format' | awk '{print $4}')
ARCH=$(/home/rohit/Android/Sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objdump -f "$CLI_OBJ" | grep architecture | cut -f 1 -d , | awk '{print $2}')
/home/rohit/Android/Sdk/ndk/28.0.13004108/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-objcopy --input-target=binary --output-target=$OUTPUT_TARGET --binary-architecture $ARCH loader-m32.exe loader-m32-wrapped.o

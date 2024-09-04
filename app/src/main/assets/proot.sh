PREFIX_PATH=/data/data/com.rk.xededitor
FILE_PATH="$PREFIX_PATH/shell"


if [ -s "$FILE_PATH" ]; then
    START_SHELL=$(cat "$FILE_PATH")
else
    START_SHELL="/bin/sh"
fi

export LD_LIBRARY_PATH=$PREFIX_PATH/root/lib
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
$PREFIX_PATH/root/bin/proot -b /sdcard:/sdcard -b /storage:/storage -S $PREFIX_PATH/rootfs "$START_SHELL"i
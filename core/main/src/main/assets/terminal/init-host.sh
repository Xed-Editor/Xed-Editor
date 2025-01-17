ARGS="--kill-on-exit"
ARGS="$ARGS  -w $PWD"

for data_dir in /data/app /data/dalvik-cache \
	/data/misc/apexdata/com.android.art/dalvik-cache; do
	if [ -e "$data_dir" ]; then
		ARGS="$ARGS -b ${data_dir}"
	fi
done
unset data_dir

for system_mnt in /apex /odm /product /system /system_ext /vendor \
	/linkerconfig/ld.config.txt \
	/linkerconfig/com.android.art/ld.config.txt \
	/plat_property_contexts /property_contexts; do

	if [ -e "$system_mnt" ]; then
		system_mnt=$(realpath "$system_mnt")
		ARGS="$ARGS -b ${system_mnt}"
	fi
done
unset system_mnt


if [ -d /storage ]; then
	ARGS="$ARGS -b /storage:/storage"
fi
ARGS="$ARGS -b /sdcard:/sdcard"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b /proc/self/fd:/dev/fd"
ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b /sys"

if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
	mkdir -p "$PREFIX/local/alpine/tmp"
	chmod 1777 "$PREFIX/local/alpine/tmp"
fi
ARGS="$ARGS -b $PREFIX/local/alpine/tmp:/dev/shm"

ARGS="$ARGS -r $PREFIX/local/alpine"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS -L"

$LINKER $PREFIX/local/bin/proot $ARGS sh $PREFIX/local/bin/init "$@"

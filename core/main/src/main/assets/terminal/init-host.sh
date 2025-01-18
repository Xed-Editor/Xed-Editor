ARGS="--kill-on-exit"
ARGS="$ARGS -w $PWD"

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


if ls -1U /storage > /dev/null 2>&1; then
	ARGS="$ARGS -b /storage"
	ARGS="$ARGS -b /storage/emulated/0:/sdcard"
else
	if ls -1U /storage/self/primary/ > /dev/null 2>&1; then
		storage_path="/storage/self/primary"
	elif ls -1U /storage/emulated/0/ > /dev/null 2>&1; then
		storage_path="/storage/emulated/0"
	elif ls -1U /sdcard/ > /dev/null 2>&1; then
		storage_path="/sdcard"
	else
		storage_path=""
	fi

	if [ -n "$storage_path" ]; then
		ARGS="$ARGS -b ${storage_path}:/sdcard"
		ARGS="$ARGS -b ${storage_path}:/storage/emulated/0"
		ARGS="$ARGS -b ${storage_path}:/storage/self/primary"
	fi
fi

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
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

$LINKER $PREFIX/local/bin/proot $ARGS sh $PREFIX/local/bin/init "$@"

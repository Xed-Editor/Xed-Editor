ARGS="--kill-on-exit"
ARGS="$ARGS  -w $PWD"
ARGS="$ARGS -b /system:/system"
ARGS="$ARGS -b /vendor:/vendor"
ARGS="$ARGS -b /data:/data"

if [ -d /apex ]; then
	ARGS="$ARGS -b /apex:/apex"
fi

if [ -e "/linkerconfig/ld.config.txt" ]; then
	ARGS="$ARGS -b /linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt"
fi

if [ -f /property_contexts ]; then
	ARGS="$ARGS -b /property_contexts:/property_contexts"
fi

if [ -d /storage ]; then
	ARGS="$ARGS -b /storage:/storage"
fi
ARGS="$ARGS -b /sdcard:/sdcard"

for f in dev proc; do
	ARGS="$ARGS -b /$f:/$f"
done

ARGS="$ARGS  -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc/self/fd:/dev/fd"
ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
ARGS="$ARGS -b $PREFIX:$PREFIX"

# Bind /tmp to /dev/shm.
if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
	mkdir -p "$PREFIX/local/alpine/tmp"
	chmod 1777 "$PREFIX/local/alpine/tmp"
fi
ARGS="$ARGS -b $PREFIX/local/alpine/tmp:/dev/shm" "$@"


ARGS="$ARGS -r $PREFIX/local/alpine"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"

$PREFIX/local/bin/proot $ARGS sh $PREFIX/local/bin/init "$@"

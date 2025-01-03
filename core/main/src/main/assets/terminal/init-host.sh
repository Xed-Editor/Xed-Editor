# Kill processes on exit to avoid hanging on exit
ARGS="--kill-on-exit"

ARGS="$ARGS  -w $PWD"

# For the /system/bin/linker(64) to be found:
ARGS="$ARGS -b /system:/system"

# On some devices /vendor is required for termux packages to work correctly
# See https://github.com/termux/proot/issues/2#issuecomment-303995382
ARGS="$ARGS -b /vendor:/vendor"

# Bind /data to include system folders such as /data/misc. Also $PREFIX
ARGS="$ARGS -b /data:/data"


# Android 10 needs /apex for /system/bin/linker:
# https://github.com/termux/proot/issues/95#issuecomment-584779998
if [ -d /apex ]; then
	ARGS="$ARGS -b /apex:/apex"
fi

# Android 11.
if [ -e "/linkerconfig/ld.config.txt" ]; then
	ARGS="$ARGS -b /linkerconfig/ld.config.txt:/linkerconfig/ld.config.txt"
fi

if [ -f /property_contexts ]; then
	# Used by getprop (see https://github.com/termux/termux-packages/issues/1076)
	# but does not exist on Android 8.
	ARGS="$ARGS -b /property_contexts:/property_contexts"
fi

# Expose external and internal storage:
if [ -d /storage ]; then
	ARGS="$ARGS -b /storage:/storage"
fi

# Mimic traditional Linux file system hierarchy- system dirs:
for f in dev proc; do
	ARGS="$ARGS -b /$f:/$f"
done

# Mimic traditional Linux file system hierarchy- system dirs:
ARGS="$ARGS  -b /dev/urandom:/dev/random -b /proc/self/fd:/dev/fd -b /proc/self/fd/0:/dev/stdin -b /proc/self/fd/1:/dev/stdout -b /proc/self/fd/2:/dev/stderr -b $PREFIX:$PREFIX"

# Root of the file system:
ARGS="$ARGS -r $PREFIX/local/alpine"

#Grant fake root
ARGS="$ARGS -0"

#Replace hard links with symlinks, pretending they are really hardlinks
ARGS="$ARGS --link2symlink"

echo $PREFIX/local/bin/proot $ARGS sh $PREFIX/local/bin/init "$@"

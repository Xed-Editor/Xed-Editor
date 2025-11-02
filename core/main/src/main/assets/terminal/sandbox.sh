clear
ARGS="$ARGS -r /"
ARGS="--kill-on-exit"
ARGS="$ARGS -w /"

ARGS="$ARGS -b $PRIVATE_DIR:/data/data/com.termux"

chmod -R +x $LOCAL/bin

$LINKER $LOCAL/bin/proot $ARGS /bin/sh $PRIVATE_DIR/local/bin/init "$@"
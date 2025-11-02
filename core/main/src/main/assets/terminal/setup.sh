set -e

source "$LOCAL/bin/utils"

info "Extracting the Termux rootfs…"

mkdir -p $PRIVATE_DIR/files/usr
mkdir -p $PRIVATE_DIR/files/home

cd $PRIVATE_DIR/files/usr

tar -xf "$TMP_DIR"/termux.tar

rm "$TMP_DIR"/termux.tar
# DO NOT REMOVE THIS FILE JUST DON'T, TRUST ME
touch $LOCAL/.terminal_setup_ok_DO_NOT_REMOVE

clear

if [ $# -gt 0 ]; then
    sh $@
else
    clear
    sh $LOCAL/bin/sandbox
fi

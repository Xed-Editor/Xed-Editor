export PREFIX=/data/data/com.termux/files/usr
export LD_PRELOAD=/data/data/com.termux/files/usr/lib/libtermux-exec.so
export HOME=/data/data/com.termux/files/home
export PATH=$PREFIX/bin:/system/bin
cd $HOME

if [ $# -eq 0 ]; then
    exec $PREFIX/bin/login
else
    exec "$@"
fi

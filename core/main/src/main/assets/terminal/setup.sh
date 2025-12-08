set -e

mkdir -p $PRIVATE_DIR/files
cd $PRIVATE_DIR/files
tar -xvf $TMPDIR/sandbox.tar

reset

sh $PRIVATE_DIR/local/bin/sandbox

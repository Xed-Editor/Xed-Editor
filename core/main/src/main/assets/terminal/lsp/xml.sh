set -e

cd $PRIVATE_DIR/local
curl -LO https://download.eclipse.org/staging/2025-09/plugins/org.eclipse.lemminx.uber-jar_0.31.0.jar
apt install default-jdk
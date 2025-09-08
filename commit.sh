cd soraX || exit 1
git add .
git commit -m "$1"
cd .. || exec 1
git add .
git commit -m "$1"

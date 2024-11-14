INSTALLED_ROOTFS_DIR="/data/data/com.rk.xededitor/rootfs"


# Add user to passwd
echo "aid_$(id -un):x:$(id -u):$(id -g):Termux:/:/sbin/nologin" >> \
    "$INSTALLED_ROOTFS_DIR/etc/passwd"

# Add user to shadow with placeholder values
echo "aid_$(id -un):*:18446:0:99999:7:::" >> \
    "$INSTALLED_ROOTFS_DIR/etc/shadow"

# Add user to group file
# Save current IFS
OLD_IFS="$IFS"
IFS=" "

# Get groups as space-separated list
groups=$(id -Gn)

for group_name in $groups; do
    # Get single group ID
    group_id=$(id -g "$group_name")
    echo "aid_$group_name:x:$group_id:root,aid_$(id -un)" >> \
        "$INSTALLED_ROOTFS_DIR/etc/group"
    
    # Check gshadow file existence without local keyword
    if [ -f "$INSTALLED_ROOTFS_DIR/etc/gshadow" ]; then
        echo "aid_$group_name:*::root,aid_$(id -un)" >> \
            "$INSTALLED_ROOTFS_DIR/etc/gshadow"
    fi
done

# Restore original IFS
IFS="$OLD_IFS"
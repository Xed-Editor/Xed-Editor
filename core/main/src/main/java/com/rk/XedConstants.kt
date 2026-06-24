package com.rk

object XedConstants {
    private const val ROOTFS_BASE = "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu"

    const val ROOTFS_ARM = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-armhf.tar.gz"
    const val ROOTFS_ARM64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-arm64.tar.gz"
    const val ROOTFS_X64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-amd64.tar.gz"


    const val EXTENSION_API_BASE="https://xed-editor.app/api/extensions"
    const val THEMES_API_BASE="https://xed-editor.app/api/themes"
    const val ICONPACKS_API_BASE="https://xed-editor.app/api/icon-packs"
}

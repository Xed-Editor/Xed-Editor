package com.rk.terminal

private const val ROOTFS_BASE = "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu"

const val ROOTFS_ARM = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-armhf.tar.gz"
const val ROOTFS_ARM64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-arm64.tar.gz"
const val ROOTFS_X64 = "$ROOTFS_BASE/ubuntu-base-24.04.3-base-amd64.tar.gz"

/**
 * Rootfs bundled into the Play Store flavour, so no download is needed on first run.
 *
 * The file lives in the app's `playstore` source set (`app/src/playstore/assets/`) so that only that
 * flavour carries the extra ~29 MB; the community build keeps downloading [ROOTFS_ARM64] instead.
 */
const val BUNDLED_ROOTFS_ASSET = "arm64-v8a/ubuntu.rootfs"

/** The only ABI the Play Store flavour ships a bundled rootfs for. */
const val BUNDLED_ROOTFS_ABI = "arm64-v8a"

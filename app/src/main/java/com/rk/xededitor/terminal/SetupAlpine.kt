package com.rk.xededitor.terminal

import android.content.Context
import android.os.Build
import com.jaredrummler.ktsh.Shell
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.resources.strings
import com.rk.runner.commonUtils
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SetupAlpine(val terminal: Terminal, val runnable: Runnable) {
    
    private lateinit var loadingPopup: LoadingPopup
    
    fun init() {
        if (PreferencesData.getBoolean(PreferencesKeys.FAIL_SAFE, false) || File(terminal.filesDir.parentFile, "root/bin/proot").exists()) {
            runnable.run()
            return
        }
        
        loadingPopup = LoadingPopup(terminal, null).setMessage(rkUtils.getString(strings.wait_pkg))
        
        if (File(terminal.filesDir, "bootstrap.tar").exists().not()) {
            loadingPopup.show()
            downloadRootfs()
        }
    }
    
    fun getaarchName(): String {
        return when (getArch()) {
            AARCH.X86_64 -> {
                "x86_64"
            }
            
            AARCH.X86 -> {
                "x86"
            }
            
            AARCH.AARCH64 -> {
                "arm64"
            }
            
            AARCH.ARMV7A -> {
                "arm32"
            }
            
            AARCH.ARMHF -> {
                "armhf32"
            }
            
            AARCH.NONE -> {
                
                throw RuntimeException(rkUtils.getString(strings.unsupported_aarch))
            }
        }
    }
    
    @OptIn(DelicateCoroutinesApi::class)
    private fun downloadRootfs() {
        val archName = getaarchName()
        
        val url = "https://raw.githubusercontent.com/Xed-Editor/Karbon-Packages/main/$archName.tar"
        
        DefaultScope.launch(Dispatchers.IO) {
            if (terminal.cacheDir.exists().not()) {
                terminal.cacheDir.mkdirs()
            }
            
            val complete = Runnable {
                DefaultScope.launch(Dispatchers.Default) {
                    val rootfsDir = File(terminal.filesDir.parentFile, "rootfs")
                    if (rootfsDir.exists().not()) {
                        rootfsDir.mkdirs()
                    } else {
                        rootfsDir.deleteRecursively()
                    }
                    
                    val proot = File(terminal.filesDir.parentFile, "proot")
                    if (proot.exists()) {
                        proot.delete()
                    }
                    
                    val bootstrap = File(terminal.cacheDir.absolutePath, "bootstrap.tar")
                    
                    exctractAssets(
                        terminal,
                        "proot.sh",
                        "${terminal.filesDir.parentFile!!.absolutePath}/proot.sh",
                    )
                    
                    Shell("sh").apply {
                        run(
                            "tar -xvf ${bootstrap.absolutePath} -C ${bootstrap.parentFile!!.absolutePath}"
                        )
                        
                        val rootfs = File(
                            File(bootstrap.parentFile!!.absolutePath, getaarchName()),
                            "rootfs.tar.gz",
                        )
                        
                        val targetRootfs = File(terminal.filesDir.parentFile, "rootfs").apply {
                            if (exists().not()) {
                                mkdirs()
                            }
                        }
                        run("tar -xvf ${rootfs.absolutePath} -C $targetRootfs")
                        
                        val proot = File(
                            File(bootstrap.parentFile!!.absolutePath, getaarchName()),
                            "proot.tar.gz",
                        )
                        
                        run(
                            "tar -xvf ${proot.absolutePath} -C ${bootstrap.parentFile!!.absolutePath}"
                        )
                        run(
                            "mv ${bootstrap.parentFile!!.absolutePath}/root ${terminal.filesDir.parentFile!!.absolutePath}"
                        )
                        run(
                            "chmod +x ${terminal.filesDir.parentFile!!.absolutePath}/root/bin/proot"
                        )
                        run("rm -rf ${terminal.cacheDir.absolutePath}/*")
                        // fix internet
                        run(
                            "echo \"nameserver 8.8.8.8\" > ${terminal.filesDir.parentFile!!.absolutePath}/rootfs/etc/resolv.conf"
                        )
                        run(
                            "echo \"nameserver 8.8.4.4\" >> ${terminal.filesDir.parentFile!!.absolutePath}/rootfs/etc/resolv.conf"
                        )
                        
                        
                        val rootfsPath = "${terminal.filesDir.parentFile!!.absolutePath}/rootfs"
                        
                        run("chmod +rw ${rootfsPath}/proc")
                        run("chmod +rw ${rootfsPath}/dev")
                        
                        if (File("$rootfsPath/proc/.loadavg").exists().not()) {
                            File("$rootfsPath/proc").listFiles()?.forEach { f -> f.deleteRecursively() }
                            File("$rootfsPath/dev").listFiles()?.forEach { f -> f.deleteRecursively() }
                            
                            File("$rootfsPath/proc/.loadavg").writeText("0.12 0.07 0.02 2/165 765")
                            File("$rootfsPath/proc/.vmstat").writeText(
                                """nr_free_pages 41852
nr_zone_inactive_anon 752692
nr_zone_active_anon 639602
nr_zone_inactive_file 104897
nr_zone_active_file 154773
nr_zone_unevictable 36242
nr_zone_write_pending 39
nr_mlock 856
nr_bounce 0
nr_zspages 136810
nr_free_cma 0
numa_hit 12123712
numa_miss 0
numa_foreign 0
numa_interleave 2685
numa_local 12123712
numa_other 0
nr_inactive_anon 752692
nr_active_anon 639602
nr_inactive_file 104897
nr_active_file 154773
nr_unevictable 36242
nr_slab_reclaimable 36769
nr_slab_unreclaimable 38467
nr_isolated_anon 0
nr_isolated_file 0
workingset_nodes 23335
workingset_refault_anon 71827
workingset_refault_file 849611
workingset_activate_anon 56501
workingset_activate_file 643525
workingset_restore_anon 1028
workingset_restore_file 405694
workingset_nodereclaim 3650
nr_anon_pages 1314925
nr_mapped 161323
nr_file_pages 373610
nr_dirty 39
nr_writeback 0
nr_writeback_temp 0
nr_shmem 111066
nr_shmem_hugepages 0
nr_shmem_pmdmapped 0
nr_file_hugepages 0
nr_file_pmdmapped 0
nr_anon_transparent_hugepages 0
nr_vmscan_write 639734
nr_vmscan_immediate_reclaim 938
nr_dirtied 257761
nr_written 890136
nr_kernel_misc_reclaimable 0
nr_foll_pin_acquired 617
nr_foll_pin_released 617
nr_kernel_stack 28288
nr_page_table_pages 18293
nr_swapcached 2214
nr_dirty_threshold 53315
nr_dirty_background_threshold 26625
pgpgin 9484651
pgpgout 1137229
pswpin 71827
pswpout 639734
pgalloc_dma 256
pgalloc_dma32 3928448
pgalloc_normal 8211892
pgalloc_movable 0
allocstall_dma 0
allocstall_dma32 0
allocstall_normal 1047
allocstall_movable 2384
pgskip_dma 0
pgskip_dma32 0
pgskip_normal 353376
pgskip_movable 0
pgfree 12842526
pgactivate 1886656
pgdeactivate 1622708
pglazyfree 0
pgfault 7059480
pgmajfault 103965
pglazyfreed 0
pgrefill 1989759
pgreuse 358871
pgsteal_kswapd 2605540
pgsteal_direct 212892
pgdemote_kswapd 0
pgdemote_direct 0
pgscan_kswapd 5990649
pgscan_direct 334257
pgscan_direct_throttle 0
pgscan_anon 3197528
pgscan_file 3127378
pgsteal_anon 640409
pgsteal_file 2178023
zone_reclaim_failed 0
pginodesteal 0
slabs_scanned 1867920
kswapd_inodesteal 91166
kswapd_low_wmark_hit_quickly 268
kswapd_high_wmark_hit_quickly 155
pageoutrun 852
pgrotated 334
drop_pagecache 0
drop_slab 0
oom_kill 0
numa_pte_updates 0
numa_huge_pte_updates 0
numa_hint_faults 0
numa_hint_faults_local 0
numa_pages_migrated 0
pgmigrate_success 599174
pgmigrate_fail 1764408
thp_migration_success 0
thp_migration_fail 0
thp_migration_split 0
compact_migrate_scanned 9487402
compact_free_scanned 34393113
compact_isolated 3007847
compact_stall 0
compact_fail 0
compact_success 0
compact_daemon_wake 419
compact_daemon_migrate_scanned 117847
compact_daemon_free_scanned 782110
htlb_buddy_alloc_success 0
htlb_buddy_alloc_fail 0
unevictable_pgs_culled 2393102
unevictable_pgs_scanned 3341312
unevictable_pgs_rescued 2262777
unevictable_pgs_mlocked 994
unevictable_pgs_munlocked 138
unevictable_pgs_cleared 0
unevictable_pgs_stranded 0
thp_fault_alloc 0
thp_fault_fallback 0
thp_fault_fallback_charge 0
thp_collapse_alloc 0
thp_collapse_alloc_failed 0
thp_file_alloc 0
thp_file_fallback 0
thp_file_fallback_charge 0
thp_file_mapped 0
thp_split_page 0
thp_split_page_failed 0
thp_deferred_split_page 0
thp_split_pmd 0
thp_split_pud 0
thp_zero_page_alloc 0
thp_zero_page_alloc_failed 0
thp_swpout 0
thp_swpout_fallback 0
balloon_inflate 0
balloon_deflate 0
balloon_migrate 0
swap_ra 9915
swap_ra_hit 8783
direct_map_level2_splits 173
direct_map_level3_splits 1
nr_unstable 0""".trimIndent()
                            )
                            
                            
                            File(rootfsPath + "/proc/.stat").writeText(
                                """
                                cpu  222775 1937 51274 934411 4836 0 976 0 0 0
                                cpu0 55366 666 13225 233769 1310 0 436 0 0 0
                                cpu1 52448 361 11666 235986 1218 0 346 0 0 0
                                cpu2 56821 466 12592 233599 1171 0 158 0 0 0
                                cpu3 58140 442 13790 231055 1135 0 35 0 0 0
                                intr 6641020 16 5471 0 0 0 0 0 0 0 386 0 0 752203 0 0 0 5 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 693 254759 0 541287 41 26711 1415 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
                                ctxt 15418836
                                btime 1730612763
                                processes 10198
                                procs_running 2
                                procs_blocked 0
                                softirq 4391653 725136 460379 8 107721 268934 0 5897 1641071 42 1182465
                            """.trimIndent()
                            )
                        }
                        
                        
                        shutdown()
                    }
                    
                    exctractAssets(
                        terminal,
                        "init.sh",
                        "${terminal.filesDir.parentFile!!.absolutePath}/rootfs/init.sh",
                    )
                    
                    commonUtils.exctractAssets(terminal) {}
                    
                    withContext(Dispatchers.Main) {
                        loadingPopup.hide()
                        runnable.run()
                    }
                }
            }
            
            val failure = Runnable {
                DefaultScope.launch(Dispatchers.Main) {
                    rkUtils.toast(rkUtils.getString(strings.pkg_download_failed))
                    loadingPopup.hide()
                    terminal.finish()
                }
            }
            
            rkUtils.downloadFile(
                url,
                terminal.cacheDir.absolutePath,
                "bootstrap.tar",
                complete,
                failure,
            )
        }
    }
    
    private enum class AARCH {
        ARMHF, ARMV7A, AARCH64, X86, X86_64, NONE,
    }
    
    private fun getArch(): AARCH {
        val supportedAbis = Build.SUPPORTED_ABIS
        
        // app maybe running in a emulator with multi abi support so x86_64 is the best choice
        // from most to least preferred abi
        return if (supportedAbis.contains("x86_64")) {
            AARCH.X86_64
        } else if (supportedAbis.contains("x86")) {
            AARCH.X86
        } else if (supportedAbis.contains("arm64-v8a")) {
            AARCH.AARCH64
        } else if (supportedAbis.contains("armeabi-v7a")) {
            if (isHardFloat()) {
                AARCH.ARMHF
            } else {
                AARCH.ARMV7A
            }
        } else {
            AARCH.NONE
        }
    }
    
    private fun isHardFloat(): Boolean {
        val result: Shell.Command.Result
        Shell("sh").apply {
            result = run("cat /proc/cpuinfo\n")
            shutdown()
        }
        val sb = StringBuilder()
        result.stdout.forEach { line -> sb.append(line) }
        sb.toString().apply {
            if (contains("vfp") || contains("vfpv3")) {
                return true
            }
        }
        return false
    }
    
    fun exctractAssets(context: Context, assetFileName: String, outputFilePath: String) {
        val assetManager = context.assets
        val outputFile = File(outputFilePath)
        
        try {
            // Open the asset file as an InputStream
            assetManager.open(assetFileName).use { inputStream ->
                // Create an output file and its parent directories if they don't exist
                outputFile.parentFile?.mkdirs()
                
                // Write the input stream to the output file
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            println("${rkUtils.getString(strings.copy_failed)}: ${e.message}")
        }
    }
}

package com.rk.terminal

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import com.rk.file.child
import com.rk.file.localDir
import com.rk.utils.logError
import kotlinx.coroutines.*
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

object StatUpdater {
    private var updateJob: Job? = null
    private val numCores = Runtime.getRuntime().availableProcessors()
    private val coreTicks = Array(numCores) { LongArray(8) { Random.nextLong(1000, 50000) } }
    private val globalTicks = LongArray(8)

    fun start(context: Context) {
        if (updateJob != null && updateJob?.isActive == true) return

        updateJob = CoroutineScope(Dispatchers.IO).launch {
            val statFile = localDir(context).child("stat")
            val vmstatFile = localDir(context).child("vmstat")
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()

            var lastSystemClock = SystemClock.elapsedRealtime()
            val initialTicks = getAppCpuTicks()
            var lastAppUserTicks = initialTicks.first
            var lastAppSystemTicks = initialTicks.second

            while (isActive) {
                try {
                    // 1. Generate CPU stat data using real UID CPU usage
                    val elapsedRealtime = SystemClock.elapsedRealtime()
                    val dtMs = (elapsedRealtime - lastSystemClock).coerceAtLeast(1L)
                    lastSystemClock = elapsedRealtime

                    val currentTicks = getAppCpuTicks()
                    var userDelta = (currentTicks.first - lastAppUserTicks).coerceAtLeast(0L)
                    var systemDelta = (currentTicks.second - lastAppSystemTicks).coerceAtLeast(0L)
                    lastAppUserTicks = currentTicks.first
                    lastAppSystemTicks = currentTicks.second

                    // Add a tiny simulated background system load of 0.5% - 2% to make it look alive
                    val expectedTicksPerCore = (dtMs / 10L).coerceAtLeast(1L) // 100 HZ (1 tick = 10ms)
                    val systemBackgroundLoad = 0.005 + Random.nextDouble() * 0.015
                    val bgTicks = (expectedTicksPerCore * numCores * systemBackgroundLoad).toLong()
                    val bgUserTicks = (bgTicks * 0.7).toLong()
                    val bgSystemTicks = bgTicks - bgUserTicks
                    
                    userDelta += bgUserTicks
                    systemDelta += bgSystemTicks

                    val totalExpectedTicks = expectedTicksPerCore * numCores
                    var activeUser = userDelta
                    var activeSystem = systemDelta
                    if (activeUser + activeSystem > totalExpectedTicks) {
                        val scale = totalExpectedTicks.toDouble() / (activeUser + activeSystem)
                        activeUser = (activeUser * scale).toLong()
                        activeSystem = (activeSystem * scale).toLong()
                    }

                    // Distribute ticks across cores
                    var remainingUser = activeUser
                    var remainingSystem = activeSystem

                    for (core in 0 until numCores) {
                        val totalRemaining = remainingUser + remainingSystem
                        val coreActive = totalRemaining.coerceAtMost(expectedTicksPerCore)
                        
                        val coreUser = if (totalRemaining > 0) {
                            (coreActive * remainingUser) / totalRemaining
                        } else 0L
                        
                        val coreSystem = coreActive - coreUser
                        val coreIdle = expectedTicksPerCore - coreActive

                        coreTicks[core][0] += coreUser      // user
                        coreTicks[core][1] += 0L            // nice
                        coreTicks[core][2] += coreSystem    // system
                        coreTicks[core][3] += coreIdle      // idle
                        coreTicks[core][4] += 0L            // iowait
                        coreTicks[core][5] += 0L            // irq
                        coreTicks[core][6] += 0L            // softirq
                        coreTicks[core][7] += 0L            // steal
                        
                        remainingUser -= coreUser
                        remainingSystem -= coreSystem
                    }

                    // Distribute any leftover to core 0 to keep stats strictly accurate
                    if (remainingUser > 0) {
                        coreTicks[0][0] += remainingUser
                    }
                    if (remainingSystem > 0) {
                        coreTicks[0][2] += remainingSystem
                    }

                    // Reset global ticks accumulation
                    for (i in 0..7) {
                        globalTicks[i] = 0
                    }
                    // Accumulate global
                    for (core in 0 until numCores) {
                        for (i in 0..7) {
                            globalTicks[i] += coreTicks[core][i]
                        }
                    }

                    val statBuilder = StringBuilder()
                    
                    // Global CPU line
                    statBuilder.append("cpu")
                    for (i in 0..7) {
                        statBuilder.append(" ").append(globalTicks[i])
                    }
                    statBuilder.append(" 0 0\n")

                    // Individual core lines
                    for (core in 0 until numCores) {
                        statBuilder.append("cpu").append(core)
                        for (i in 0..7) {
                            statBuilder.append(" ").append(coreTicks[core][i])
                        }
                        statBuilder.append(" 0 0\n")
                    }

                    // Standard extra fields
                    statBuilder.append("intr 127541 38 290 0 0 0 0 4 0 1 0 0\n")
                    statBuilder.append("ctxt ").append(System.currentTimeMillis() / 1000).append("\n")
                    statBuilder.append("btime ").append(System.currentTimeMillis() / 1000 - 3600).append("\n")
                    statBuilder.append("processes ").append(100 + Random.nextInt(50)).append("\n")
                    statBuilder.append("procs_running ").append(1 + Random.nextInt(4)).append("\n")
                    statBuilder.append("procs_blocked 0\n")
                    statBuilder.append("softirq 75663 0 5903 6 25375 10774 0 243 11685 0 21677\n")

                    if (!statFile.exists()) {
                        statFile.createNewFile()
                    }
                    statFile.writeText(statBuilder.toString())

                    // 2. Generate Memory vmstat data using real system memory info
                    activityManager.getMemoryInfo(memoryInfo)
                    val freePages = memoryInfo.availMem / 4096
                    val totalPages = memoryInfo.totalMem / 4096
                    val usedPages = (totalPages - freePages).coerceAtLeast(0)

                    val vmstatBuilder = StringBuilder()
                    vmstatBuilder.append("nr_free_pages ").append(freePages).append("\n")
                    vmstatBuilder.append("nr_zone_inactive_anon ").append((usedPages * 0.25).toLong()).append("\n")
                    vmstatBuilder.append("nr_zone_active_anon ").append((usedPages * 0.15).toLong()).append("\n")
                    vmstatBuilder.append("nr_zone_inactive_file ").append((usedPages * 0.20).toLong()).append("\n")
                    vmstatBuilder.append("nr_zone_active_file ").append((usedPages * 0.35).toLong()).append("\n")
                    vmstatBuilder.append("nr_unevictable ").append((usedPages * 0.05).toLong()).append("\n")
                    vmstatBuilder.append("nr_zone_write_pending 0\n")
                    vmstatBuilder.append("nr_mlock 0\n")
                    vmstatBuilder.append("nr_bounce 0\n")
                    vmstatBuilder.append("nr_zspages 0\n")
                    vmstatBuilder.append("nr_free_cma 0\n")
                    vmstatBuilder.append("numa_hit 1259626\n")
                    vmstatBuilder.append("numa_miss 0\n")
                    vmstatBuilder.append("numa_foreign 0\n")
                    vmstatBuilder.append("numa_interleave 720\n")
                    vmstatBuilder.append("numa_local 1259626\n")
                    vmstatBuilder.append("numa_other 0\n")
                    vmstatBuilder.append("nr_inactive_anon ").append((usedPages * 0.25).toLong()).append("\n")
                    vmstatBuilder.append("nr_active_anon ").append((usedPages * 0.15).toLong()).append("\n")
                    vmstatBuilder.append("nr_inactive_file ").append((usedPages * 0.20).toLong()).append("\n")
                    vmstatBuilder.append("nr_active_file ").append((usedPages * 0.35).toLong()).append("\n")
                    vmstatBuilder.append("nr_unevictable ").append((usedPages * 0.05).toLong()).append("\n")
                    vmstatBuilder.append("nr_slab_reclaimable ").append(8000 + Random.nextInt(500)).append("\n")
                    vmstatBuilder.append("nr_slab_unreclaimable ").append(7000 + Random.nextInt(500)).append("\n")
                    vmstatBuilder.append(remainingVmstatFields)

                    if (!vmstatFile.exists()) {
                        vmstatFile.createNewFile()
                    }
                    vmstatFile.writeText(vmstatBuilder.toString())
                } catch (e: Exception) {
                    logError(e)
                }

                delay(1000.milliseconds)
            }
        }
    }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }

    private fun getAppCpuTicks(): Pair<Long, Long> {
        var userTicks = 0L
        var systemTicks = 0L
        var readSuccess = false
        try {
            val procDir = File("/proc")
            val files = procDir.listFiles()
            if (files != null) {
                for (file in files) {
                    val name = file.name
                    if (file.isDirectory && name.all { it.isDigit() }) {
                        try {
                            val statFile = File(file, "stat")
                            if (statFile.exists()) {
                                val line = statFile.readText().trim()
                                val lastCloseParen = line.lastIndexOf(')')
                                if (lastCloseParen != -1 && lastCloseParen + 2 < line.length) {
                                    val rest = line.substring(lastCloseParen + 2)
                                    val tokens = rest.split(' ')
                                    if (tokens.size >= 13) {
                                        val utime = tokens[11].toLongOrNull() ?: 0L
                                        val stime = tokens[12].toLongOrNull() ?: 0L
                                        userTicks += utime
                                        systemTicks += stime
                                        readSuccess = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip processes we don't own (SELinux blocks reading stat of other UIDs)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logError(e)
        }

        // Fallback to /proc/self/stat if full scanning is unavailable
        if (!readSuccess) {
            try {
                val selfStat = File("/proc/self/stat")
                if (selfStat.exists()) {
                    val line = selfStat.readText().trim()
                    val lastCloseParen = line.lastIndexOf(')')
                    if (lastCloseParen != -1 && lastCloseParen + 2 < line.length) {
                        val rest = line.substring(lastCloseParen + 2)
                        val tokens = rest.split(' ')
                        if (tokens.size >= 13) {
                            userTicks = tokens[11].toLongOrNull() ?: 0L
                            systemTicks = tokens[12].toLongOrNull() ?: 0L
                        }
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
        return Pair(userTicks, systemTicks)
    }

    private val remainingVmstatFields = """
        nr_isolated_anon 0
        nr_isolated_file 0
        workingset_nodes 0
        workingset_refault_anon 0
        workingset_refault_file 0
        workingset_activate_anon 0
        workingset_activate_file 0
        workingset_restore_anon 0
        workingset_restore_file 0
        workingset_nodereclaim 0
        nr_anon_pages 7723
        nr_mapped 8905
        nr_file_pages 253569
        nr_dirty 0
        nr_writeback 0
        nr_writeback_temp 0
        nr_shmem 178741
        nr_shmem_hugepages 0
        nr_shmem_pmdmapped 0
        nr_file_hugepages 0
        nr_file_pmdmapped 0
        nr_anon_transparent_hugepages 1
        nr_vmscan_write 0
        nr_vmscan_immediate_reclaim 0
        nr_dirtied 0
        nr_written 0
        nr_throttled_written 0
        nr_kernel_misc_reclaimable 0
        nr_foll_pin_acquired 0
        nr_foll_pin_released 0
        nr_kernel_stack 2780
        nr_page_table_pages 344
        nr_sec_page_table_pages 0
        nr_swapcached 0
        pgpromote_success 0
        pgpromote_candidate 0
        nr_dirty_threshold 356564
        nr_dirty_background_threshold 178064
        pgpgin 890508
        pgpgout 0
        pswpin 0
        pswpout 0
        pgalloc_dma 272
        pgalloc_dma32 261
        pgalloc_normal 1328079
        pgalloc_movable 0
        pgalloc_device 0
        allocstall_dma 0
        allocstall_dma32 0
        allocstall_normal 0
        allocstall_movable 0
        allocstall_device 0
        pgskip_dma 0
        pgskip_dma32 0
        pgskip_normal 0
        pgskip_movable 0
        pgskip_device 0
        pgfree 3077011
        pgactivate 0
        pgdeactivate 0
        pglazyfree 0
        pgfault 176973
        pgmajfault 488
        pglazyfreed 0
        pgrefill 0
        pgreuse 19230
        pgsteal_kswapd 0
        pgsteal_direct 0
        pgsteal_khugepaged 0
        pgdemote_kswapd 0
        pgdemote_direct 0
        pgdemote_khugepaged 0
        pgscan_kswapd 0
        pgscan_direct 0
        pgscan_khugepaged 0
        pgscan_direct_throttle 0
        pgscan_anon 0
        pgscan_file 0
        pgsteal_anon 0
        pgsteal_file 0
        zone_reclaim_failed 0
        pginodesteal 0
        slabs_scanned 0
        kswapd_inodesteal 0
        kswapd_low_wmark_hit_quickly 0
        kswapd_high_wmark_hit_quickly 0
        pageoutrun 0
        pgrotated 0
        drop_pagecache 0
        drop_slab 0
        oom_kill 0
        numa_pte_updates 0
        numa_huge_pte_updates 0
        numa_hint_faults 0
        numa_hint_faults_local 0
        numa_pages_migrated 0
        pgmigrate_success 0
        pgmigrate_fail 0
        thp_migration_success 0
        thp_migration_fail 0
        thp_migration_split 0
        compact_migrate_scanned 0
        compact_free_scanned 0
        compact_isolated 0
        compact_stall 0
        compact_fail 0
        compact_success 0
        compact_daemon_wake 0
        compact_daemon_migrate_scanned 0
        compact_daemon_free_scanned 0
        htlb_buddy_alloc_success 0
        htlb_buddy_alloc_fail 0
        cma_alloc_success 0
        cma_alloc_fail 0
        unevictable_pgs_culled 27002
        unevictable_pgs_scanned 0
        unevictable_pgs_rescued 744
        unevictable_pgs_mlocked 744
        unevictable_pgs_munlocked 744
        unevictable_pgs_cleared 0
        unevictable_pgs_stranded 0
        thp_fault_alloc 13
        thp_fault_fallback 0
        thp_fault_fallback_charge 0
        thp_collapse_alloc 4
        thp_collapse_alloc_failed 0
        thp_file_alloc 0
        thp_file_fallback 0
        thp_file_fallback_charge 0
        thp_file_mapped 0
        thp_split_page 0
        thp_split_page_failed 0
        thp_deferred_split_page 1
        thp_split_pmd 1
        thp_scan_exceed_none_pte 0
        thp_scan_exceed_swap_pte 0
        thp_scan_exceed_share_pte 0
        thp_split_pud 0
        thp_zero_page_alloc 0
        thp_zero_page_alloc_failed 0
        thp_swpout 0
        thp_swpout_fallback 0
        balloon_inflate 0
        balloon_deflate 0
        balloon_migrate 0
        swap_ra 0
        swap_ra_hit 0
        ksm_swpin_copy 0
        cow_ksm 0
        zswpin 0
        zswpout 0
        direct_map_level2_splits 29
        direct_map_level3_splits 0
        nr_unstable 0
    """.trimIndent()
}

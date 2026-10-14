package com.droiddeck.launcher.core

import com.droiddeck.launcher.gpu.GpuInfo
import com.droiddeck.launcher.gpu.SocNames

object CpuAllocationDefaults {
    data class Allocation(val client: String, val game: String)

    val device: Allocation? by lazy {
        forDevice(SocNames.model(), GpuInfo.systemProperty("ro.board.platform"),
            CpuCores.all.associateWith(CpuCores::maxGhz))
    }

    fun forDevice(model: String, platform: String, frequencies: Map<Int, Double?>): Allocation? {
        val chip = model.trim().uppercase().substringBefore('-')
        val plat = platform.trim().lowercase()
        // Expected cluster sizes, ascending by frequency: (little, mid, prime).
        val layout = when {
            chip in setOf("SM8550", "QCS8550") || (chip.isEmpty() && plat == "kalama") -> listOf(3, 4, 1)
            // Dimensity 9300+: 1x X4 @ 3.4 GHz + 3x A720 @ 2.85 GHz + 4x A520 @ 2.0 GHz.
            chip == "MT6989" || (chip.isEmpty() && plat == "mt6989") -> listOf(4, 3, 1)
            else -> return null
        }
        if (frequencies.size != 8 || frequencies.keys.any { it < 0 } ||
            frequencies.values.any { it == null || !it.isFinite() || it <= 0.0 }) return null
        val clusters = frequencies.entries.groupBy { it.value }.toSortedMap(compareBy { it ?: 0.0 }).values.toList()
        if (clusters.map { it.size } != layout) return null
        return Allocation(
            client = CpuCores.format(clusters[2].map { it.key }.toSet()),
            game = CpuCores.format(clusters[1].map { it.key }.toSet()),
        )
    }
}

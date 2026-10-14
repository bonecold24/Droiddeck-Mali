package com.droiddeck.launcher.gpu

import java.io.File

/** One Vulkan memory heap as reported for the Mali GPU. */
data class MaliMemoryHeap(val name: String, val sizeBytes: Long, val deviceLocal: Boolean = true)

/**
 * The detected Mali device profile: GPU id, kbase kernel interface, memory heaps.
 *
 * Built from sysfs/file dumps by [parse] so host unit tests can drive it with
 * fixtures; [detect] does the best-effort live read. Both never throw: when the
 * kernel interface differs from the tested one (or is unreadable), fields degrade
 * to null/empty with an explanatory [notes] entry, and the caller falls through
 * the [MaliFallbackChain] instead of crashing.
 *
 * Pure JVM (no Android dependencies).
 */
data class MaliDeviceProfile(
    /** The kbase GPU id, e.g. 0xC8700000 on the 14T Pro; null when not readable. */
    val gpuId: Long?,
    /** e.g. "Mali-G720"; "Mali (unknown model)" when nothing names it. */
    val gpuName: String,
    /** /dev/mali0 exists. */
    val kbaseNodePresent: Boolean,
    /** /dev/mali0 is readable by the app. */
    val kbaseNodeReadable: Boolean,
    /** kbase UAPI version, e.g. "1.21"; null when not exposed (sysfs has no standard node). */
    val kbaseUapi: String?,
    val heaps: List<MaliMemoryHeap>,
    /** Why fields are missing: the graceful-degradation record. */
    val notes: List<String>,
) {
    /** The 14T Pro's GPU id (Dimensity 9300+, Immortalis-G720 MC12) - REPORTED by upstream PanProbe. */
    val isG720: Boolean get() = gpuId == EXPECTED_G720_GPU_ID
    val hasKnownGpu: Boolean get() = gpuId != null
    val hasKbase: Boolean get() = kbaseNodePresent

    companion object {
        const val EXPECTED_G720_GPU_ID = 0xC8700000L

        const val MALI_DEVICE = "/dev/mali0"
        private val GPUINFO_PATHS = listOf(
            "/sys/class/misc/mali0/device/gpuinfo",
            "/sys/devices/platform/mali/gpuinfo",
        )
        private val UEVENT_PATHS = listOf(
            "/sys/class/misc/mali0/device/uevent",
            "/sys/devices/platform/mali/uevent",
        )

        /**
         * Build a profile from file dumps: keys are file paths, values their text
         * contents (null = missing/unreadable). Recognised keys:
         * - "/dev/mali0": "" when the node exists; "/dev/mali0:access": "r"/"rw"/"none".
         * - the gpuinfo/uevent paths above; "gl_renderer" (GL fallback name);
         *   "kbase_uapi" (native probe result, e.g. "1.21");
         *   "vulkan_heaps" (lines "name|sizeBytes|deviceLocal(1/0)").
         * Never throws.
         */
        @JvmStatic
        fun parse(files: Map<String, String?>): MaliDeviceProfile {
            val notes = mutableListOf<String>()
            val kbaseNodePresent = files[MALI_DEVICE] != null
            val kbaseNodeReadable = (files["$MALI_DEVICE:access"] ?: "").contains('r')
            if (!kbaseNodePresent) notes.add(
                "/dev/mali0 is absent: no Mali kbase kernel driver on this device.")
            else if (!kbaseNodeReadable) notes.add(
                "/dev/mali0 exists but is not readable by the app; the driver cannot open the GPU.")

            val gpuinfoText = GPUINFO_PATHS.firstNotNullOfOrNull { files[it] }?.takeIf { it.isNotEmpty() }
            val nameSource = gpuinfoText ?: files["gl_renderer"]?.takeIf { it.isNotEmpty() }
            val gpuName = nameSource
                ?.let { Regex("""Mali-[A-Z]?\d+(?:-[A-Za-z]+)?(?: MC\d+)?""").find(it)?.value }
                ?: "Mali (unknown model)"
            if (nameSource == null) notes.add("No gpuinfo or GL renderer string names the GPU.")

            val idText = gpuinfoText.orEmpty()
            val labeled = Regex("""(?i)(?:gpu[_ ]id)\s*[:=]?\s*(0[xX][0-9a-fA-F]+)""")
                .find(idText)?.groupValues?.get(1)
            val anyHex = labeled
                ?: Regex("""0[xX][0-9a-fA-F]{7,8}""").find(idText)?.value
            val gpuId = anyHex?.removePrefix("0x")?.removePrefix("0X")?.toLongOrNull(16)
            if (gpuId == null && idText.isNotEmpty()) notes.add(
                "No GPU id found in the gpuinfo text; cannot confirm the exact Mali model.")

            val kbaseUapi = files["kbase_uapi"]?.takeIf { it.isNotBlank() }
                ?: UEVENT_PATHS.firstNotNullOfOrNull { files[it] }?.let { uevent ->
                    Regex("""(?i)\bversion\s*[=:\s]+\s*([0-9]+(?:\.[0-9]+)*)""")
                        .find(uevent)?.groupValues?.get(1)
                }
            if (kbaseUapi == null && kbaseNodePresent) notes.add(
                "kbase UAPI version is not exposed in sysfs; a native ioctl probe is needed to " +
                    "confirm it (NEEDS-REAL-MALI). The kernel interface is treated as untested.")

            val heaps = files["vulkan_heaps"]?.lines()?.mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size < 2) null
                else MaliMemoryHeap(parts[0], parts[1].toLongOrNull() ?: 0L, parts.getOrElse(2) { "1" } == "1")
            }.orEmpty()
            if (heaps.isEmpty()) notes.add(
                "Memory heap layout is unknown without a Vulkan device query on the phone (NEEDS-REAL-MALI).")

            return MaliDeviceProfile(
                gpuId = gpuId, gpuName = gpuName,
                kbaseNodePresent = kbaseNodePresent, kbaseNodeReadable = kbaseNodeReadable,
                kbaseUapi = kbaseUapi, heaps = heaps, notes = notes,
            )
        }

        /**
         * Best-effort live detection: /dev/mali0 presence/access plus the sysfs
         * gpuinfo/uevent dumps. Never throws; on a non-Mali host it reports
         * "no kbase here" with notes.
         *
         * Note: /dev/mali0 is only checked for existence/access, never read -
         * reading a device node can block.
         */
        @JvmStatic
        fun detect(): MaliDeviceProfile {
            val files = mutableMapOf<String, String?>()
            val earlyNotes = mutableListOf<String>()
            try {
                val node = File(MALI_DEVICE)
                val exists = try {
                    node.exists()
                } catch (e: Exception) {
                    false
                }
                files[MALI_DEVICE] = if (exists) "" else null
                if (exists) {
                    val access = try {
                        (if (node.canRead()) "r" else "") + (if (node.canWrite()) "w" else "")
                    } catch (e: Exception) {
                        ""
                    }
                    files["$MALI_DEVICE:access"] = access.ifEmpty { "none" }
                }
                for (p in GPUINFO_PATHS + UEVENT_PATHS) {
                    files[p] = try {
                        val f = File(p)
                        if (!f.isFile) null else f.readText().trim().takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        earlyNotes.add("Could not read $p (${e.message}); treating it as unavailable.")
                        null
                    }
                }
            } catch (e: Exception) {
                earlyNotes.add("Device profile detection failed (${e.message}); treating the GPU as unknown.")
            }
            val profile = parse(files)
            return profile.copy(notes = earlyNotes + profile.notes)
        }
    }
}

package com.droiddeck.launcher.gpu

import org.json.JSONObject
import java.io.File

/**
 * Validation of an installed Linux Vulkan driver directory as a usable glibc
 * driver for the Linux guest.
 *
 * A selection is only usable when ALL of these hold:
 *  - the driver directory exists and holds the driver library,
 *  - the library is a 64-bit AArch64 ELF object,
 *  - the library links glibc (`libc.so.6`) — a bionic/Android object
 *    (`libc.so`, `liblog.so`, …) can never be loaded by a glibc process,
 *  - `icd.json` exists, parses as JSON, and carries a non-empty
 *    `ICD.library_path` (pointing at a real file) and `ICD.api_version`.
 *
 * Pure JVM (no Android dependencies) so host unit tests cover it; the Android
 * managers delegate here.
 */
object LinuxDriverValidation {

    data class Result(val valid: Boolean, val reason: String)

    /** Name of the Vulkan ICD manifest inside an installed driver directory. */
    const val ICD_NAME = "icd.json"

    /**
     * Validate [driverDir] holding the driver library [libraryName].
     */
    @JvmStatic
    fun validateDriverDir(driverDir: File, libraryName: String): Result {
        if (!driverDir.isDirectory) return Result(false, "driver directory missing: ${driverDir.path}")
        if (libraryName.isEmpty()) return Result(false, "driver library name is empty")
        val lib = File(driverDir, libraryName)
        if (!lib.isFile) return Result(false, "driver library missing: ${lib.path}")
        if (!ElfInspect.isAarch64Elf(lib)) {
            return Result(false, "$libraryName is not a 64-bit AArch64 ELF object")
        }
        if (!ElfInspect.linksGlibc(lib)) {
            return Result(
                false,
                "$libraryName does not link glibc (no libc.so.6) — " +
                    "a bionic/Android object cannot be loaded by Linux guest processes",
            )
        }
        return validateIcdJson(File(driverDir, ICD_NAME))
    }

    /**
     * Validate a Vulkan ICD manifest: parses as JSON and carries a usable
     * `ICD.library_path` and `ICD.api_version`.
     */
    @JvmStatic
    fun validateIcdJson(icdFile: File): Result {
        if (!icdFile.isFile) return Result(false, "icd.json missing: ${icdFile.path}")
        val json = try {
            JSONObject(icdFile.readText())
        } catch (e: Exception) {
            return Result(false, "icd.json is not valid JSON: ${e.message}")
        }
        val icd = json.optJSONObject("ICD")
            ?: return Result(false, "icd.json has no \"ICD\" object")
        val libraryPath = icd.optString("library_path", "")
        if (libraryPath.isEmpty()) {
            return Result(false, "icd.json \"ICD.library_path\" is empty")
        }
        val apiVersion = icd.optString("api_version", "")
        if (apiVersion.isEmpty()) {
            return Result(false, "icd.json \"ICD.api_version\" is empty")
        }
        if (!File(libraryPath).isFile) {
            return Result(false, "icd.json \"ICD.library_path\" does not exist: $libraryPath")
        }
        return Result(true, "ok")
    }
}

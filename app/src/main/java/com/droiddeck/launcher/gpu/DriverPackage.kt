package com.droiddeck.launcher.gpu

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * A versioned Mali driver package (the layout the mission's driver build produces):
 *
 *   libvulkan_panfrost.so, panvk_kbase_icd.aarch64.json, meta.json, MANIFEST.json,
 *   SHA256SUMS.txt, SOURCE.json, BUILD-PROVENANCE.txt
 *
 * Pure JVM (no Android dependencies) so the parsing and the SHA-256 verification run in
 * host unit tests. The Android side ([LinuxVulkanDriverManager][com.droiddeck.launcher.gpu.LinuxVulkanDriverManager])
 * only extracts the zip and picks the install location; everything that decides "is this
 * package intact?" lives here.
 *
 * Verification policy (see [verify]):
 * - MANIFEST.json must exist, parse, and name a 64-hex binarySha256 plus a non-empty files[].
 * - Every files[] entry must be present in the package (else: incomplete package).
 * - Every SHA256SUMS.txt entry must be present and match (else: tampered/corrupt).
 * - The driver .so must match MANIFEST.json's binarySha256 (else: tampered/corrupt).
 * - Manifest files with no SHA256SUMS.txt coverage, and extra files not listed in the
 *   manifest, are reported as warnings, not failures: the real RC3 package only checksums
 *   the .so, and rejecting it for that would reject our own build.
 */
class DriverPackageException(message: String) : RuntimeException(message)

/** The outcome of [DriverPackage.verify]: the parsed package plus non-fatal warnings. */
data class DriverPackageReport(val pkg: DriverPackage, val warnings: List<String>)

data class DriverPackage(
    val name: String,
    val packageVersion: String,
    val binarySha256: String,
    /** e.g. "android-aarch64-bionic" (app side) or "linux-aarch64-glibc" (Linux userland). */
    val abi: String,
    /** e.g. "panvk-kbase". */
    val backend: String,
    /** e.g. "g720-v12-csf". */
    val profile: String,
    val libraryName: String,
    /** From the ICD manifest, e.g. "1.4.363". */
    val apiVersion: String,
    /** MANIFEST.json files[]. */
    val files: List<String>,
    /** BUILD-PROVENANCE.txt, trimmed ("" when absent). */
    val provenance: String,
    /** meta.json "validation" note ("" when absent). */
    val validation: String,
    /** The ICD manifest's file name inside the package. */
    val icdFileName: String,
) {
    /** First 8 hex chars of the binary hash: the disambiguator in versioned install ids. */
    val shaPrefix: String get() = binarySha256.take(8)
    val isGlibc: Boolean get() = abi.contains("glibc", ignoreCase = true)
    val isBionic: Boolean get() = !isGlibc &&
        (abi.contains("bionic", ignoreCase = true) || abi.startsWith("android", ignoreCase = true))

    companion object {
        private val MANIFEST_NAMES = setOf("MANIFEST.json", "meta.json", "SHA256SUMS.txt")

        /** Parse only; use [verify] when integrity matters. */
        @JvmStatic
        fun parse(dir: File): DriverPackage {
            val manifestFile = File(dir, "MANIFEST.json")
            if (!manifestFile.isFile) throw DriverPackageException(
                "Not a versioned driver package: MANIFEST.json is missing in \"${dir.name}\".")
            val manifest = try {
                JSONObject(manifestFile.readText())
            } catch (e: Exception) {
                throw DriverPackageException("MANIFEST.json is not valid JSON: ${e.message}")
            }
            val binarySha256 = manifest.optString("binarySha256", "")
            if (!binarySha256.matches(Regex("[0-9a-fA-F]{64}"))) throw DriverPackageException(
                "MANIFEST.json \"binarySha256\" is missing or malformed; cannot verify the driver binary.")
            val files = manifest.optJSONArray("files")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }.orEmpty()
            if (files.isEmpty()) throw DriverPackageException("MANIFEST.json lists no files.")

            val metaFile = File(dir, "meta.json")
            if (!metaFile.isFile) throw DriverPackageException(
                "Not a versioned driver package: meta.json is missing in \"${dir.name}\".")
            val meta = try {
                JSONObject(metaFile.readText())
            } catch (e: Exception) {
                throw DriverPackageException("meta.json is not valid JSON: ${e.message}")
            }
            val name = meta.optString("name", "")
            if (name.isBlank()) throw DriverPackageException("meta.json has no \"name\".")
            val packageVersion = meta.optString("packageVersion", "")
            if (packageVersion.isBlank()) throw DriverPackageException("meta.json has no \"packageVersion\".")
            val libraryName = meta.optString("libraryName", "")
            if (libraryName.isBlank()) throw DriverPackageException("meta.json has no \"libraryName\".")

            val icdFileName = files.firstOrNull { it.endsWith(".json") && it !in MANIFEST_NAMES && isIcd(dir, it) }
                ?: throw DriverPackageException(
                    "No Vulkan ICD manifest found in the package (looked for a *.json with an \"ICD\" object).")
            val apiVersion = try {
                JSONObject(File(dir, icdFileName).readText()).getJSONObject("ICD").optString("api_version", "")
            } catch (e: Exception) {
                throw DriverPackageException("$icdFileName is not a valid ICD manifest: ${e.message}")
            }
            val provenance = File(dir, "BUILD-PROVENANCE.txt").takeIf { it.isFile }?.readText()?.trim().orEmpty()
            return DriverPackage(
                name = name, packageVersion = packageVersion, binarySha256 = binarySha256.lowercase(),
                abi = meta.optString("abi", ""), backend = meta.optString("backend", ""),
                profile = meta.optString("profile", ""), libraryName = libraryName,
                apiVersion = apiVersion, files = files, provenance = provenance,
                validation = meta.optString("validation", ""), icdFileName = icdFileName,
            )
        }

        private fun isIcd(dir: File, name: String): Boolean = try {
            JSONObject(File(dir, name).readText()).has("ICD")
        } catch (e: Exception) {
            false
        }

        /**
         * Parse and verify a package directory. Throws [DriverPackageException] with a
         * user-readable reason on the first integrity failure.
         */
        @JvmStatic
        fun verify(dir: File): DriverPackageReport {
            val pkg = parse(dir)
            val warnings = mutableListOf<String>()

            for (name in pkg.files) {
                if (!File(dir, name).isFile) throw DriverPackageException(
                    "Incomplete driver package: MANIFEST.json lists \"$name\" but it is not in the package.")
            }

            val sumsFile = File(dir, "SHA256SUMS.txt")
            val sums = if (sumsFile.isFile) parseSha256Sums(sumsFile.readText()) else emptyMap()
            for ((name, expected) in sums) {
                val f = File(dir, name)
                if (!f.isFile) throw DriverPackageException(
                    "Incomplete driver package: SHA256SUMS.txt lists \"$name\" but it is not in the package.")
                val actual = sha256Hex(f)
                if (actual != expected) throw DriverPackageException(
                    "SHA-256 mismatch for \"$name\": SHA256SUMS.txt says $expected but the file hashes to " +
                        "$actual. The file may be corrupted or tampered with; the package was not installed.")
            }

            val so = File(dir, pkg.libraryName)
            val soSha = sha256Hex(so)
            if (soSha != pkg.binarySha256) throw DriverPackageException(
                "SHA-256 mismatch for \"${pkg.libraryName}\": MANIFEST.json binarySha256 is ${pkg.binarySha256} " +
                    "but the file hashes to $soSha. The driver binary may be corrupted or tampered with; " +
                    "the package was not installed.")

            for (name in pkg.files) {
                if (name !in sums) warnings.add(
                    "No SHA-256 checksum covers \"$name\" (SHA256SUMS.txt); this file's integrity is not verified.")
            }
            val listed = pkg.files.toSet() + setOf("icd.json", "meta.json")
            dir.listFiles { f -> f.isFile }?.forEach { f ->
                if (f.name !in listed) warnings.add(
                    "Extra file \"${f.name}\" is not listed in MANIFEST.json; it was ignored.")
            }
            return DriverPackageReport(pkg, warnings)
        }

        /**
         * Parse a SHA256SUMS.txt body ("<64 hex>  <file>" per line, "*"-binary marker
         * tolerated). Malformed non-empty lines throw: a damaged checksum file must not
         * silently pass.
         */
        @JvmStatic
        fun parseSha256Sums(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            text.lines().forEachIndexed { i, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val parts = line.split(Regex("\\s+"))
                if (parts.size != 2 || !parts[0].matches(Regex("[0-9a-fA-F]{64}"))) throw DriverPackageException(
                    "SHA256SUMS.txt line ${i + 1} is malformed: \"$raw\".")
                out[parts[1].removePrefix("*")] = parts[0].lowercase()
            }
            return out
        }

        @JvmStatic
        fun sha256Hex(f: File): String {
            if (!f.isFile) throw DriverPackageException("\"${f.name}\" is missing from the package.")
            val md = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { ins ->
                val buf = ByteArray(1 shl 16)
                var r: Int
                while (ins.read(buf).also { r = it } > 0) md.update(buf, 0, r)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

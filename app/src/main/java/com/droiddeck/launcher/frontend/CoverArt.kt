package com.droiddeck.launcher.frontend

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Box art for the emulators' games, found and kept on the device: the front end shows a disc image
 * with nothing but its emulator's logo otherwise.
 *
 * In order: the name of the file against RetroArch's thumbnail collection for the system
 * (libretro-thumbnails, named after the standard disc-dump names - "Need for Speed - Underground 2
 * (USA)"), first exactly and then loosely (tags dropped, the same region preferred) against the
 * collection's list of names; then the disc's own ID, read from the image - PS2 serials against
 * xlenore/ps2-covers (what PCSX2 and ARMSX2 download from), GameCube/Wii IDs against GameTDB (what
 * Dolphin uses). A cover is downloaded once into files/covers; a game none of them has is not
 * asked about again for a few days.
 */
object CoverArt {
    private const val TAG = "CoverArt"
    private const val MISS_RETRY_MS = 3L * 24 * 3600 * 1000
    private const val INDEX_TTL_MS = 14L * 24 * 3600 * 1000
    /** Downloads in one pass, so a first visit to a big library does not hold the network for long. */
    private const val MAX_PER_PASS = 40

    /** The emulator's systems in libretro-thumbnails, in the order they are tried. */
    private val collections = mapOf(
        "rpcs3" to listOf("Sony_-_PlayStation_3"),
        "armsx2" to listOf("Sony_-_PlayStation_2"),
        "duckstation" to listOf("Sony_-_PlayStation"),
        "ppsspp" to listOf("Sony_-_PlayStation_Portable"),
        "dolphin" to listOf("Nintendo_-_GameCube", "Nintendo_-_Wii"),
        "melonds" to listOf("Nintendo_-_Nintendo_DS"),
        "cemu" to listOf("Nintendo_-_Wii_U"),
    )

    private fun root(context: Context) = File(context.filesDir, "covers")

    /** The file name, as the cover is kept under: a game keeps its art when its folder moves. */
    private fun keyOf(rom: Library.Rom): String =
        rom.hostPath.name.replace(Regex("[^A-Za-z0-9._() -]"), "_").take(150)

    private fun coverFile(context: Context, rom: Library.Rom) = File(root(context), "${rom.emulatorId}/${keyOf(rom)}.img")
    private fun missFile(context: Context, rom: Library.Rom) = File(root(context), "${rom.emulatorId}/${keyOf(rom)}.miss")

    /** A cover already on the device for this game, or null. */
    fun cached(context: Context, rom: Library.Rom): File? = coverFile(context, rom).takeIf { it.isFile && it.length() > 0 }

    private fun wanted(context: Context, rom: Library.Rom): Boolean {
        if (rom.art != null || cached(context, rom) != null || rom.emulatorId !in collections) return false
        val miss = missFile(context, rom)
        return !(miss.isFile && System.currentTimeMillis() - miss.lastModified() < MISS_RETRY_MS)
    }

    /**
     * Looks for covers for the games that have none, on the calling (background) thread. True when
     * at least one was found, so the caller rebuilds the list to show it.
     */
    fun fetchMissing(context: Context, roms: List<Library.Rom>): Boolean {
        var found = false
        var tries = 0
        for (rom in roms) {
            if (!wanted(context, rom)) continue
            if (++tries > MAX_PER_PASS) break
            val url = try { findUrl(context, rom) } catch (e: Exception) { Log.w(TAG, "${rom.name}: $e"); null }
            val target = coverFile(context, rom)
            if (url != null && download(url, target)) {
                Log.i(TAG, "${rom.name}: $url")
                missFile(context, rom).delete()
                found = true
            } else {
                missFile(context, rom).apply { parentFile?.mkdirs(); writeText(url ?: "") }
            }
        }
        return found
    }

    private fun findUrl(context: Context, rom: Library.Rom): String? {
        val name = rom.hostPath.nameWithoutExtension.removeSuffix(".dec")
        for (collection in collections.getValue(rom.emulatorId)) {
            // Exactly the dump name first: no listing needed.
            val exact = thumbnailUrl(collection, name)
            if (exists(exact)) return exact
            // Then loosely, against the collection's names.
            nearest(name, names(context, collection))?.let { return thumbnailUrl(collection, it) }
        }
        // The disc's own ID.
        return when (rom.emulatorId) {
            "armsx2" -> ps2Serial(rom.hostPath)?.let { "https://raw.githubusercontent.com/xlenore/ps2-covers/main/covers/default/$it.jpg" }
            "dolphin" -> nintendoDiscId(rom.hostPath)?.let { id ->
                val region = when (id.getOrNull(3)) { 'P' -> "EN"; 'J' -> "JA"; 'K' -> "KO"; else -> "US" }
                "https://art.gametdb.com/wii/cover/$region/$id.png"
            }
            else -> null
        }?.takeIf { exists(it) }
    }

    /** libretro-thumbnails replaces these in a title's file name. */
    private fun thumbName(title: String) = title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")

    private fun thumbnailUrl(collection: String, title: String) =
        "https://raw.githubusercontent.com/libretro-thumbnails/$collection/master/Named_Boxarts/" +
            URLEncoder.encode("${thumbName(title)}.png", "UTF-8").replace("+", "%20")

    // ---- loose matching ------------------------------------------------------------------------

    private fun tags(name: String): List<String> = Regex("[(\\[]([^)\\]]*)[)\\]]").findAll(name).map { it.groupValues[1].lowercase() }.toList()

    /** A title without its tags, articles and punctuation: "Legend of Zelda, The (USA)" = "legend of zelda". */
    private fun base(name: String): String = name
        .replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), " ")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(' ').filter { it.isNotEmpty() && it != "the" && it != "a" }
        .joinToString(" ")

    private fun nearest(name: String, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val want = base(name)
        if (want.isEmpty()) return null
        val same = candidates.filter { base(it) == want }
        if (same.isEmpty()) return null
        val region = tags(name).firstOrNull { it in setOf("usa", "europe", "japan", "world", "korea") } ?: "usa"
        return same.firstOrNull { region in tags(it) } ?: same.firstOrNull { "usa" in tags(it) } ?: same.first()
    }

    /** The titles in a collection's Named_Boxarts (no extension), kept for a fortnight. */
    private fun names(context: Context, collection: String): List<String> {
        val cache = File(root(context), "index-$collection.txt")
        if (cache.isFile && System.currentTimeMillis() - cache.lastModified() < INDEX_TTL_MS) return cache.readLines()
        val list = try {
            val top = JSONArray(get("https://api.github.com/repos/libretro-thumbnails/$collection/contents/") ?: return cachedOr(cache))
            val sha = (0 until top.length()).map { top.getJSONObject(it) }.firstOrNull { it.optString("name") == "Named_Boxarts" }
                ?.optString("sha") ?: return cachedOr(cache)
            val tree = JSONObject(get("https://api.github.com/repos/libretro-thumbnails/$collection/git/trees/$sha") ?: return cachedOr(cache))
                .getJSONArray("tree")
            (0 until tree.length()).map { tree.getJSONObject(it).optString("path") }
                .filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }
        } catch (e: Exception) {
            Log.w(TAG, "index $collection: $e"); return cachedOr(cache)
        }
        cache.parentFile?.mkdirs()
        cache.writeText(list.joinToString("\n"))
        return list
    }

    private fun cachedOr(cache: File): List<String> = if (cache.isFile) cache.readLines() else emptyList()

    // ---- disc IDs ------------------------------------------------------------------------------

    /** "SLUS-21065" from a PS2 .iso's SYSTEM.CNF (BOOT2 = cdrom0:\SLUS_210.65;1), or null. */
    private fun ps2Serial(file: File): String? {
        if (!file.name.endsWith(".iso", ignoreCase = true)) return null
        RandomAccessFile(file, "r").use { f ->
            fun sector(lba: Long, length: Int): ByteArray = ByteArray(length).also { f.seek(lba * 2048); f.readFully(it) }
            val pvd = sector(16, 2048)
            if (String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") return null
            fun le32(b: ByteArray, at: Int) = (b[at].toLong() and 0xff) or ((b[at + 1].toLong() and 0xff) shl 8) or
                ((b[at + 2].toLong() and 0xff) shl 16) or ((b[at + 3].toLong() and 0xff) shl 24)
            val rootLba = le32(pvd, 156 + 2)
            val rootSize = le32(pvd, 156 + 10).toInt().coerceAtMost(64 * 1024)
            val dir = sector(rootLba, rootSize)
            var at = 0
            while (at < dir.size) {
                val len = dir[at].toInt() and 0xff
                if (len == 0) { at = (at / 2048 + 1) * 2048; continue }
                val nameLen = dir[at + 32].toInt() and 0xff
                val name = String(dir, at + 33, nameLen, Charsets.US_ASCII)
                if (name.startsWith("SYSTEM.CNF", ignoreCase = true)) {
                    val cnf = String(sector(le32(dir, at + 2), le32(dir, at + 10).toInt().coerceAtMost(4096)), Charsets.US_ASCII)
                    val m = Regex("BOOT2\\s*=\\s*cdrom0:\\\\?([A-Z]{4})_(\\d{3})\\.(\\d{2})").find(cnf) ?: return null
                    return "${m.groupValues[1]}-${m.groupValues[2]}${m.groupValues[3]}"
                }
                at += len
            }
        }
        return null
    }

    /** The six-character ID of a GameCube/Wii disc image (.iso/.gcm, or .wbfs), or null. */
    private fun nintendoDiscId(file: File): String? {
        val offset = when (file.extension.lowercase()) { "iso", "gcm" -> 0L; "wbfs" -> 0x200L; else -> return null }
        RandomAccessFile(file, "r").use { f ->
            val b = ByteArray(6); f.seek(offset); f.readFully(b)
            val id = String(b, Charsets.US_ASCII)
            return id.takeIf { it.all { c -> c.isLetterOrDigit() } }
        }
    }

    // ---- network -------------------------------------------------------------------------------

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "DroidDeck")
        }

    private fun exists(url: String): Boolean = try {
        val c = open(url, "HEAD"); val ok = c.responseCode == 200; c.disconnect(); ok
    } catch (e: Exception) { false }

    private fun get(url: String): String? = try {
        val c = open(url)
        if (c.responseCode != 200) { c.disconnect(); null } else c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) { Log.w(TAG, "$url: $e"); null }

    /** Saves the image at [url] to [target] if it really is an image. */
    private fun download(url: String, target: File): Boolean = try {
        val c = open(url)
        if (c.responseCode != 200) { c.disconnect(); false } else {
            target.parentFile?.mkdirs()
            val tmp = File(target.path + ".part")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            if (bounds.outWidth > 0 && tmp.renameTo(target)) true else { tmp.delete(); false }
        }
    } catch (e: Exception) { Log.w(TAG, "$url: $e"); false }
}

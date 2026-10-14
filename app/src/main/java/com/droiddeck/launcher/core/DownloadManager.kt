package com.droiddeck.launcher.core

import android.util.Log
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hardened download pipeline for large archives (Linux rootfs/runtime, Proton
 * builds, components).
 *
 * Compared to [Downloader.downloadFile], this adds, in one testable place:
 * - SHA-256 verification of the finished archive ([Spec.expectedSha256]),
 * - resume of interrupted downloads via HTTP Range (partial state lives in a
 *   `<name>.part` file; the destination is only touched by an atomic rename),
 * - a pre-download storage-space check with a clear error,
 * - retry with exponential backoff for transient network/5xx failures,
 * - atomic move into place, so a failed or killed download never leaves a
 *   corrupt file at the destination path.
 *
 * Network, storage and sleep are seams ([Fetcher], [SpaceChecker], [Sleeper])
 * so every behaviour is unit-testable without network; see DownloadManagerTest.
 * Blocking: call from a worker thread.
 */
object DownloadManager {
    private const val TAG = "DownloadManager"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 1 shl 16
    private const val PROGRESS_REPORT_BYTES = 1L shl 20
    private const val HTTP_RANGE_NOT_SATISFIABLE = 416

    /** A single open HTTP response. */
    interface FetchSession : Closeable {
        val responseCode: Int
        /** Remaining bytes, or -1 if the server did not say. */
        val contentLength: Long
        val input: InputStream
    }

    /** Opens [url] for reading, resuming at [offset] via `Range` when > 0. */
    interface Fetcher {
        @Throws(IOException::class)
        fun open(url: String, offset: Long): FetchSession
    }

    interface SpaceChecker {
        fun usableBytes(directory: File): Long
    }

    interface Sleeper {
        @Throws(InterruptedException::class)
        fun sleep(millis: Long)
    }

    interface Logger {
        fun w(tag: String, message: String, cause: Throwable?)
    }

    enum class Phase { DOWNLOADING, VERIFYING }

    data class Spec(
        val url: String,
        val destination: File,
        /** Hex SHA-256 the finished archive must match; null skips verification. */
        val expectedSha256: String? = null,
        /**
         * Advertised archive size in bytes (e.g. from the release catalog), when
         * known. Enables the storage check *before* any network traffic; the
         * already-downloaded `.part` bytes are subtracted.
         */
        val expectedBytes: Long? = null,
        val maxAttempts: Int = 4,
        val baseBackoffMs: Long = 1_000L,
        val maxBackoffMs: Long = 30_000L,
        /** 0..1 fraction, or -1f when the total size is unknown. */
        val progress: ((fraction: Float) -> Unit)? = null,
        val phaseListener: ((phase: Phase) -> Unit)? = null,
    )

    enum class FailureReason {
        /** Transport failure (after retries are exhausted). */
        NETWORK,
        /** Non-retryable HTTP status. */
        HTTP,
        INSUFFICIENT_SPACE,
        CHECKSUM_MISMATCH,
        /** Thread interrupted; never retried. */
        INTERRUPTED,
        /** Disk write failed (e.g. no space left mid-download). */
        WRITE_ERROR,
        /** The atomic rename into place failed. */
        INSTALL_FAILED,
    }

    sealed interface Result {
        data object Success : Result
        data class Failure(val reason: FailureReason, val detail: String) : Result
    }

    @JvmOverloads
    @JvmStatic
    fun download(
        spec: Spec,
        fetcher: Fetcher = defaultFetcher(),
        space: SpaceChecker = defaultSpaceChecker(),
        sleeper: Sleeper = defaultSleeper(),
        logger: Logger = androidLogger,
    ): Result {
        require(spec.maxAttempts >= 1) { "maxAttempts must be >= 1" }
        val dir = spec.destination.absoluteFile.parentFile ?: File(".")
        if (!dir.isDirectory) dir.mkdirs()
        val part = File(dir, spec.destination.name + ".part")

        // Pre-flight storage check: fail with a clear error before any network
        // when the catalog-advertised size is known.
        if (spec.expectedBytes != null) {
            val have = if (part.isFile) part.length() else 0L
            val required = (spec.expectedBytes - have).coerceAtLeast(0L)
            val usable = space.usableBytes(dir)
            if (usable < required) {
                return Result.Failure(
                    FailureReason.INSUFFICIENT_SPACE,
                    "need $required more bytes for ${spec.destination.name} " +
                        "(archive ${spec.expectedBytes} bytes, $have already downloaded), " +
                        "but only $usable usable in $dir",
                )
            }
        }

        var attempt = 0
        while (true) {
            attempt++
            if (Thread.currentThread().isInterrupted) {
                return Result.Failure(FailureReason.INTERRUPTED, "interrupted before attempt $attempt")
            }
            when (val outcome = attemptOnce(spec, part, dir, fetcher, space, logger)) {
                is AttemptOutcome.Done -> return outcome.result
                is AttemptOutcome.Retry -> {
                    logger.w(TAG, "attempt $attempt failed (${outcome.cause.message}); backing off", outcome.cause)
                    if (attempt >= spec.maxAttempts) {
                        return Result.Failure(FailureReason.NETWORK, "gave up after $attempt attempts: ${outcome.cause.message}")
                    }
                    try {
                        sleeper.sleep(backoffDelayMs(spec, attempt))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return Result.Failure(FailureReason.INTERRUPTED, "interrupted during backoff")
                    }
                }
            }
        }
    }

    private sealed interface AttemptOutcome {
        data class Done(val result: Result) : AttemptOutcome
        data class Retry(val cause: IOException) : AttemptOutcome
    }

    private fun backoffDelayMs(spec: Spec, attempt: Int): Long {
        var delay = spec.baseBackoffMs
        repeat(attempt - 1) { delay = (delay * 2).coerceAtMost(spec.maxBackoffMs) }
        return delay
    }

    private fun attemptOnce(
        spec: Spec,
        part: File,
        dir: File,
        fetcher: Fetcher,
        space: SpaceChecker,
        logger: Logger,
    ): AttemptOutcome {
        var offset = if (part.isFile) part.length() else 0L
        var allowRangeRestart = true
        while (true) {
            val session: FetchSession = try {
                fetcher.open(spec.url, offset)
            } catch (e: InterruptedIOException) {
                Thread.currentThread().interrupt()
                return AttemptOutcome.Done(Result.Failure(FailureReason.INTERRUPTED, "interrupted while opening connection"))
            } catch (e: IOException) {
                return AttemptOutcome.Retry(e)
            }
            val code = session.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE && offset > 0 && allowRangeRestart) {
                // Stale over-long part file: drop it and start over once.
                logger.w(TAG, "server answered 416; dropping stale ${part.name}", null)
                session.close()
                part.delete()
                offset = 0
                allowRangeRestart = false
                continue
            }
            session.use {
                val appending: Boolean = when {
                    code == HttpURLConnection.HTTP_PARTIAL -> true
                    code == HttpURLConnection.HTTP_OK || code in 200..299 -> {
                        if (offset > 0) {
                            // Server ignored Range: restart from scratch.
                            logger.w(TAG, "server ignored Range (HTTP $code); restarting ${part.name}", null)
                            part.delete()
                            offset = 0
                        }
                        false
                    }
                    code in 500..599 -> return AttemptOutcome.Retry(IOException("HTTP $code"))
                    else -> return AttemptOutcome.Done(
                        Result.Failure(FailureReason.HTTP, "unexpected HTTP $code for ${spec.url}"),
                    )
                }
                val remaining = session.contentLength
                if (remaining >= 0) {
                    val usable = space.usableBytes(dir)
                    if (usable < remaining) {
                        return AttemptOutcome.Done(
                            Result.Failure(
                                FailureReason.INSUFFICIENT_SPACE,
                                "need $remaining bytes but only $usable usable in $dir",
                            ),
                        )
                    }
                }
                val total = if (remaining >= 0) offset + remaining else -1L
                spec.phaseListener?.invoke(Phase.DOWNLOADING)
                val outcome = streamBody(spec, part, session.input, offset, total, appending, logger)
                if (outcome != null) return outcome
                // Verify before the atomic move.
                if (spec.expectedSha256 != null) {
                    spec.phaseListener?.invoke(Phase.VERIFYING)
                    val actual = Hashes.sha256(part)
                    if (!actual.equals(spec.expectedSha256, ignoreCase = true)) {
                        part.delete()
                        return AttemptOutcome.Done(
                            Result.Failure(
                                FailureReason.CHECKSUM_MISMATCH,
                                "SHA-256 mismatch for ${part.name}: expected ${spec.expectedSha256}, got $actual",
                            ),
                        )
                    }
                }
                return if (part.renameTo(spec.destination)) {
                    AttemptOutcome.Done(Result.Success)
                } else {
                    AttemptOutcome.Done(
                        Result.Failure(FailureReason.INSTALL_FAILED, "could not move ${part.name} to ${spec.destination}"),
                    )
                }
            }
        }
    }

    /**
     * Streams the response body into [part]. Returns null on success, or the
     * terminal [AttemptOutcome] when the body is incomplete/interrupted.
     */
    private fun streamBody(
        spec: Spec,
        part: File,
        input: InputStream,
        offset: Long,
        total: Long,
        appending: Boolean,
        logger: Logger,
    ): AttemptOutcome? {
        var written = offset
        var lastReport = written
        try {
            FileOutputStream(part, appending).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                val buffered = BufferedInputStream(input, BUFFER_SIZE)
                while (true) {
                    if (Thread.currentThread().isInterrupted) {
                        Thread.currentThread().interrupt()
                        return AttemptOutcome.Done(Result.Failure(FailureReason.INTERRUPTED, "interrupted mid-download"))
                    }
                    val n = try {
                        buffered.read(buffer)
                    } catch (e: InterruptedIOException) {
                        Thread.currentThread().interrupt()
                        return AttemptOutcome.Done(Result.Failure(FailureReason.INTERRUPTED, "interrupted mid-download"))
                    } catch (e: IOException) {
                        return AttemptOutcome.Retry(e)
                    }
                    if (n < 0) break
                    try {
                        out.write(buffer, 0, n)
                    } catch (e: IOException) {
                        return AttemptOutcome.Done(
                            Result.Failure(FailureReason.WRITE_ERROR, "write to ${part.name} failed: ${e.message}"),
                        )
                    }
                    written += n
                    if (spec.progress != null && written - lastReport > PROGRESS_REPORT_BYTES) {
                        lastReport = written
                        spec.progress.invoke(if (total > 0) written.toFloat() / total else -1f)
                    }
                }
            }
        } catch (e: IOException) {
            // open() on the part file itself failed.
            return AttemptOutcome.Done(Result.Failure(FailureReason.WRITE_ERROR, "cannot write ${part.name}: ${e.message}"))
        }
        if (total >= 0 && written != total) {
            logger.w(TAG, "short body: got $written of $total bytes", null)
            return AttemptOutcome.Retry(IOException("short body: got $written of $total bytes"))
        }
        spec.progress?.invoke(1f)
        return null
    }

    // ---- default seams (production) ----

    @JvmStatic
    fun defaultFetcher(): Fetcher = object : Fetcher {
        override fun open(url: String, offset: Long): FetchSession {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "DroidDeck-Android")
            if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
            // Do NOT connect explicitly: responseCode triggers it; the session
            // owns the connection until closed.
            return object : FetchSession {
                override val responseCode: Int get() = connection.responseCode
                override val contentLength: Long get() = connection.contentLengthLong
                override val input: InputStream get() = connection.inputStream
                override fun close() = connection.disconnect()
            }
        }
    }

    @JvmStatic
    fun defaultSpaceChecker(): SpaceChecker = object : SpaceChecker {
        override fun usableBytes(directory: File): Long = directory.usableSpace
    }

    @JvmStatic
    fun defaultSleeper(): Sleeper = object : Sleeper {
        override fun sleep(millis: Long) = Thread.sleep(millis)
    }

    private val androidLogger = object : Logger {
        override fun w(tag: String, message: String, cause: Throwable?) {
            if (cause != null) Log.w(tag, message, cause) else Log.w(tag, message)
        }
    }
}

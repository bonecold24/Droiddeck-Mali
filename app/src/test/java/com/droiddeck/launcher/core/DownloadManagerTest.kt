package com.droiddeck.launcher.core

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for DownloadManager. No network: a fake Fetcher serves local byte
 * arrays with HTTP Range semantics, and space/sleep are stubbed.
 */
class DownloadManagerTest {
    private val data = ByteArray(256 * 1024) { (it % 251).toByte() }
    private val sha256 = Hashes.sha256(tempFile(data))

    private fun tempDir(): File = Files.createTempDirectory("dlm").toFile()

    private fun tempFile(bytes: ByteArray): File =
        Files.createTempFile("dlm", ".bin").toFile().apply { writeBytes(bytes); deleteOnExit() }

    // ---- fakes ----

    private class FakeSession(
        override val responseCode: Int,
        bytes: ByteArray,
        override val contentLength: Long,
        stream: InputStream? = null,
    ) : DownloadManager.FetchSession {
        override val input: InputStream = stream ?: ByteArrayInputStream(bytes)
        override fun close() = Unit
    }

    private class FakeFetcher(
        val data: ByteArray,
        val supportsRange: Boolean = true,
    ) : DownloadManager.Fetcher {
        sealed interface Script {
            data object Ok : Script
            data class Throw(val e: IOException) : Script
            data class Code(val code: Int) : Script
        }

        val script = ArrayDeque<Script>()
        val offsets = mutableListOf<Long>()
        var unknownLength = false

        override fun open(url: String, offset: Long): DownloadManager.FetchSession {
            offsets += offset
            when (val next = if (script.isEmpty()) Script.Ok else script.removeFirst()) {
                is Script.Throw -> throw next.e
                is Script.Code -> return FakeSession(next.code, ByteArray(0), 0)
                Script.Ok -> Unit
            }
            if (offset == 0L) {
                return FakeSession(200, data, if (unknownLength) -1 else data.size.toLong())
            }
            if (!supportsRange) {
                // Server ignores Range: full 200 response.
                return FakeSession(200, data, data.size.toLong())
            }
            if (offset >= data.size) return FakeSession(416, ByteArray(0), 0)
            val rest = data.copyOfRange(offset.toInt(), data.size)
            return FakeSession(206, rest, if (unknownLength) -1 else rest.size.toLong())
        }
    }

    private class FakeSpace(val usable: Long) : DownloadManager.SpaceChecker {
        override fun usableBytes(directory: File): Long = usable
    }

    private class FakeSleeper(val interruptOnSleep: Boolean = false) : DownloadManager.Sleeper {
        val delays = mutableListOf<Long>()
        override fun sleep(millis: Long) {
            delays += millis
            if (interruptOnSleep) throw InterruptedException("test interrupt")
        }
    }

    private fun spec(
        dir: File,
        name: String = "archive.tar.zst",
        expectedSha256: String? = sha256,
        expectedBytes: Long? = data.size.toLong(),
        maxAttempts: Int = 4,
        fetcher: FakeFetcher = FakeFetcher(data),
        space: DownloadManager.SpaceChecker = FakeSpace(Long.MAX_VALUE),
        sleeper: FakeSleeper = FakeSleeper(),
        progress: ((Float) -> Unit)? = null,
        phase: ((DownloadManager.Phase) -> Unit)? = null,
    ) = DownloadManager.Spec(
        url = "https://example.invalid/archive.tar.zst",
        destination = File(dir, name),
        expectedSha256 = expectedSha256,
        expectedBytes = expectedBytes,
        maxAttempts = maxAttempts,
        progress = progress,
        phaseListener = phase,
    ) to Triple(fetcher, space, sleeper)

    private fun runDownload(
        spec: DownloadManager.Spec,
        fetcher: FakeFetcher,
        space: DownloadManager.SpaceChecker,
        sleeper: FakeSleeper,
    ): DownloadManager.Result =
        DownloadManager.download(
            spec, fetcher, space, sleeper,
            logger = object : DownloadManager.Logger {
                override fun w(tag: String, message: String, cause: Throwable?) = Unit
            },
        )

    // ---- tests ----

    @Test fun downloadsFullyAndVerifiesSha256() {
        val dir = tempDir()
        val (s, parts) = spec(dir)
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertArrayEquals(data, s.destination.readBytes())
        assertFalse(File(dir, "archive.tar.zst.part").exists())
        assertEquals(listOf(0L), fetcher.offsets)
    }

    @Test fun checksumMismatchDeletesPartAndFailsWithoutTouchingDestination() {
        val dir = tempDir()
        val old = File(dir, "archive.tar.zst").apply { writeBytes(ByteArray(8)) }
        val (s, parts) = spec(dir, expectedSha256 = "0".repeat(64))
        val (fetcher, space, sleeper) = parts
        val result = runDownload(s, fetcher, space, sleeper)
        assertTrue(result is DownloadManager.Result.Failure)
        assertEquals(DownloadManager.FailureReason.CHECKSUM_MISMATCH, (result as DownloadManager.Result.Failure).reason)
        assertArrayEquals(ByteArray(8), old.readBytes())
        assertFalse(File(dir, "archive.tar.zst.part").exists())
    }

    @Test fun checksumIsSkippedWhenNotProvided() {
        val dir = tempDir()
        val (s, parts) = spec(dir, expectedSha256 = null)
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertArrayEquals(data, s.destination.readBytes())
    }

    @Test fun resumesPartialDownloadWithRangeRequest() {
        val dir = tempDir()
        val half = data.size / 2
        File(dir, "archive.tar.zst.part").writeBytes(data.copyOfRange(0, half))
        val (s, parts) = spec(dir)
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(listOf(half.toLong()), fetcher.offsets)
        assertArrayEquals(data, s.destination.readBytes())
    }

    @Test fun restartsFromScratchWhenServerIgnoresRange() {
        val dir = tempDir()
        File(dir, "archive.tar.zst.part").writeBytes(data.copyOfRange(0, 1024))
        val fetcher = FakeFetcher(data, supportsRange = false)
        val (s, parts) = spec(dir, fetcher = fetcher)
        val (_, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        // One request (the Range was sent, the server answered 200); no second round-trip.
        assertEquals(listOf(1024L), fetcher.offsets)
        assertArrayEquals(data, s.destination.readBytes())
    }

    @Test fun rangeNotSatisfiableDeletesStalePartAndRestarts() {
        val dir = tempDir()
        // Part is longer than the resource: server answers 416.
        File(dir, "archive.tar.zst.part").writeBytes(data + ByteArray(16))
        val (s, parts) = spec(dir)
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(listOf(data.size.toLong() + 16, 0L), fetcher.offsets)
        assertArrayEquals(data, s.destination.readBytes())
    }

    @Test fun insufficientSpaceFailsBeforeAnyNetwork() {
        val dir = tempDir()
        val (s, parts) = spec(dir, space = FakeSpace(1024))
        val (fetcher, space, sleeper) = parts
        val result = runDownload(s, fetcher, space, sleeper)
        assertTrue(result is DownloadManager.Result.Failure)
        assertEquals(DownloadManager.FailureReason.INSUFFICIENT_SPACE, (result as DownloadManager.Result.Failure).reason)
        assertTrue(fetcher.offsets.isEmpty())
        assertTrue(result.detail.contains("1024"))
    }

    @Test fun spaceCheckAccountsForAlreadyDownloadedBytes() {
        val dir = tempDir()
        val half = data.size / 2
        File(dir, "archive.tar.zst.part").writeBytes(data.copyOfRange(0, half))
        // Only the remaining half must fit.
        val (s, parts) = spec(dir, space = FakeSpace((data.size - half).toLong()))
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
    }

    @Test fun unknownLengthSkipsSpaceCheckButStillDownloads() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply { unknownLength = true }
        val (s, parts) = spec(dir, fetcher = fetcher, space = FakeSpace(0), expectedBytes = null)
        val (_, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertArrayEquals(data, s.destination.readBytes())
    }

    @Test fun retriesNetworkFailuresWithExponentialBackoff() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply {
            script.add(FakeFetcher.Script.Throw(IOException("reset by peer")))
            script.add(FakeFetcher.Script.Throw(IOException("timeout")))
        }
        val sleeper = FakeSleeper()
        val (s, parts) = spec(dir, fetcher = fetcher, sleeper = sleeper)
        val (_, space, _) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(listOf(0L, 0L, 0L), fetcher.offsets)
        assertEquals(listOf(1_000L, 2_000L), sleeper.delays)
    }

    @Test fun backoffIsCapped() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply {
            repeat(5) { script.add(FakeFetcher.Script.Throw(IOException("down"))) }
        }
        val sleeper = FakeSleeper()
        val (s, parts) = spec(dir, fetcher = fetcher, sleeper = sleeper, maxAttempts = 6)
        val (_, space, _) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), sleeper.delays)
    }

    @Test fun givesUpAfterMaxAttempts() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply {
            repeat(10) { script.add(FakeFetcher.Script.Throw(IOException("down"))) }
        }
        val sleeper = FakeSleeper()
        val (s, parts) = spec(dir, fetcher = fetcher, sleeper = sleeper, maxAttempts = 3)
        val (_, space, _) = parts
        val result = runDownload(s, fetcher, space, sleeper)
        assertTrue(result is DownloadManager.Result.Failure)
        assertEquals(DownloadManager.FailureReason.NETWORK, (result as DownloadManager.Result.Failure).reason)
        assertEquals(3, fetcher.offsets.size)
        assertEquals(listOf(1_000L, 2_000L), sleeper.delays)
    }

    @Test fun http404FailsImmediatelyWithoutRetry() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply { script.add(FakeFetcher.Script.Code(404)) }
        val sleeper = FakeSleeper()
        val (s, parts) = spec(dir, fetcher = fetcher, sleeper = sleeper)
        val (_, space, _) = parts
        val result = runDownload(s, fetcher, space, sleeper)
        assertTrue(result is DownloadManager.Result.Failure)
        assertEquals(DownloadManager.FailureReason.HTTP, (result as DownloadManager.Result.Failure).reason)
        assertTrue((result as DownloadManager.Result.Failure).detail.contains("404"))
        assertEquals(1, fetcher.offsets.size)
        assertTrue(sleeper.delays.isEmpty())
    }

    @Test fun http500IsRetriedThenSucceeds() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply {
            script.add(FakeFetcher.Script.Code(500))
            script.add(FakeFetcher.Script.Code(503))
        }
        val sleeper = FakeSleeper()
        val (s, parts) = spec(dir, fetcher = fetcher, sleeper = sleeper)
        val (_, space, _) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(3, fetcher.offsets.size)
        assertEquals(listOf(1_000L, 2_000L), sleeper.delays)
    }

    @Test fun interruptDuringBackoffStopsRetrying() {
        val dir = tempDir()
        val fetcher = FakeFetcher(data).apply { script.add(FakeFetcher.Script.Throw(IOException("reset"))) }
        val sleeper = FakeSleeper(interruptOnSleep = true)
        val (s, parts) = spec(dir, fetcher = fetcher, sleeper = sleeper)
        val (_, space, _) = parts
        val result = runDownload(s, fetcher, space, sleeper)
        assertTrue(result is DownloadManager.Result.Failure)
        assertEquals(DownloadManager.FailureReason.INTERRUPTED, (result as DownloadManager.Result.Failure).reason)
        assertEquals(1, fetcher.offsets.size)
        // The interrupt flag is restored on the calling thread.
        assertTrue(Thread.currentThread().isInterrupted)
        Thread.interrupted() // clear so later tests are unaffected
    }

    @Test fun interruptDuringBodyAbortsWithoutRetry() {
        val dir = tempDir()
        val started = java.util.concurrent.CountDownLatch(1)
        val blockingStream = object : InputStream() {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                started.countDown()
                try {
                    while (!Thread.currentThread().isInterrupted) Thread.sleep(50)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                throw InterruptedIOException("interrupted in test")
            }

            override fun read(): Int = read(ByteArray(1), 0, 1)
        }
        val fetcher = object : DownloadManager.Fetcher {
            override fun open(url: String, offset: Long): DownloadManager.FetchSession =
                FakeSession(200, ByteArray(0), data.size.toLong(), blockingStream)
        }
        val s = DownloadManager.Spec(
            url = "https://example.invalid/x",
            destination = File(dir, "archive.tar.zst"),
            expectedSha256 = null,
        )
        var result: DownloadManager.Result? = null
        val worker = Thread {
            result = DownloadManager.download(
                s, fetcher, FakeSpace(Long.MAX_VALUE), FakeSleeper(),
                logger = object : DownloadManager.Logger {
                    override fun w(tag: String, message: String, cause: Throwable?) = Unit
                },
            )
        }
        worker.start()
        assertTrue(started.await(10, java.util.concurrent.TimeUnit.SECONDS))
        worker.interrupt()
        worker.join(10_000)
        assertTrue(result is DownloadManager.Result.Failure)
        assertEquals(DownloadManager.FailureReason.INTERRUPTED, (result as DownloadManager.Result.Failure).reason)
    }

    @Test fun progressReachesOneAndIsMonotonic() {
        val dir = tempDir()
        val fractions = mutableListOf<Float>()
        val (s, parts) = spec(dir, progress = { fractions += it })
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertTrue(fractions.isNotEmpty())
        assertEquals(1f, fractions.last())
        assertTrue(fractions.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun phasesAreReported() {
        val dir = tempDir()
        val phases = mutableListOf<DownloadManager.Phase>()
        val (s, parts) = spec(dir, phase = { phases += it })
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(
            listOf(DownloadManager.Phase.DOWNLOADING, DownloadManager.Phase.VERIFYING),
            phases,
        )
    }

    @Test fun noVerifyPhaseWhenChecksumSkipped() {
        val dir = tempDir()
        val phases = mutableListOf<DownloadManager.Phase>()
        val (s, parts) = spec(dir, expectedSha256 = null, phase = { phases += it })
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertEquals(listOf(DownloadManager.Phase.DOWNLOADING), phases)
    }

    @Test fun successfulDownloadReplacesExistingDestinationAtomically() {
        val dir = tempDir()
        val dest = File(dir, "archive.tar.zst").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val (s, parts) = spec(dir)
        val (fetcher, space, sleeper) = parts
        assertEquals(DownloadManager.Result.Success, runDownload(s, fetcher, space, sleeper))
        assertArrayEquals(data, dest.readBytes())
        assertFalse(File(dir, "archive.tar.zst.part").exists())
    }
}

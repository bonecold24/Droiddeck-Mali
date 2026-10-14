package com.droiddeck.launcher.session

import java.io.File

/**
 * Game process lifecycle: start / monitor / kill with no orphaned processes.
 *
 * All OS interaction goes through [GameProcessSpawner] so unit tests use a
 * fake; on the device [ProcessBuilderSpawner] wraps java.lang.Process.
 */
interface GameProcessHandle {
    val label: String
    fun isAlive(): Boolean
    fun destroy()
    fun destroyForcibly()
    /** Null while still running or when the exit status is unknown. */
    fun exitCode(): Int?
}

interface GameProcessSpawner {
    fun spawn(command: List<String>, env: Map<String, String>, workDir: File?): GameProcessHandle
}

class GameProcessManager(private val spawner: GameProcessSpawner) {
    private val lock = Any()
    private val running = linkedMapOf<String, GameProcessHandle>()
    private val lastCodes = linkedMapOf<String, Int?>()

    fun start(appId: String, command: List<String>, env: Map<String, String>, workDir: File?): GameProcessHandle {
        require(command.isNotEmpty()) { "empty command" }
        synchronized(lock) {
            running.remove(appId)?.let { killHandle(it, 0) }
            lastCodes.remove(appId)
            val handle = spawner.spawn(command, env, workDir)
            running[appId] = handle
            return handle
        }
    }

    fun isRunning(appId: String): Boolean = synchronized(lock) { running[appId]?.isAlive() == true }

    fun liveCount(): Int = synchronized(lock) { running.values.count { it.isAlive() } }

    /** Last known exit code, surviving reap(). Null while running or unknown. */
    fun exitCodeOf(appId: String): Int? = synchronized(lock) {
        running[appId]?.exitCode() ?: lastCodes[appId]
    }

    /** Reap finished handles. Returns the number of live handles remaining. */
    fun reap(): Int = synchronized(lock) {
        val it = running.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (!e.value.isAlive()) {
                rememberCode(e.key, e.value.exitCode())
                it.remove()
            }
        }
        running.values.count { it.isAlive() }
    }

    /**
     * SIGTERM-equivalent, then SIGKILL-equivalent after [graceMs] if the
     * process is still alive. Returns the exit code when known.
     */
    fun kill(appId: String, graceMs: Long = 2000): Int? = synchronized(lock) {
        val handle = running.remove(appId) ?: return null
        val code = killHandle(handle, graceMs)
        rememberCode(appId, code)
        return code
    }

    private fun rememberCode(appId: String, code: Int?) {
        lastCodes[appId] = code
        while (lastCodes.size > 32) lastCodes.remove(lastCodes.keys.first())
    }

    /** Kill everything tracked. Returns the number of handles killed. */
    fun killAll(graceMs: Long = 2000): Int = synchronized(lock) {
        val handles = running.values.toList()
        running.clear()
        handles.forEach { killHandle(it, graceMs) }
        handles.size
    }

    private fun killHandle(handle: GameProcessHandle, graceMs: Long): Int? {
        if (!handle.isAlive()) return handle.exitCode()
        handle.destroy()
        val deadline = System.currentTimeMillis() + graceMs.coerceAtLeast(0)
        while (handle.isAlive() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (handle.isAlive()) handle.destroyForcibly()
        return handle.exitCode()
    }
}

/** Device spawner backed by java.lang.Process. */
class ProcessBuilderSpawner : GameProcessSpawner {
    override fun spawn(command: List<String>, env: Map<String, String>, workDir: File?): GameProcessHandle {
        val builder = ProcessBuilder(command)
        if (workDir != null) builder.directory(workDir)
        val processEnv = builder.environment()
        env.forEach { (k, v) -> processEnv[k] = v }
        builder.redirectErrorStream(false)
        val process = builder.start()
        return object : GameProcessHandle {
            override val label: String = command.firstOrNull().orEmpty()
            override fun isAlive(): Boolean = process.isAlive
            override fun destroy() = process.destroy()
            override fun destroyForcibly() = process.destroyForcibly().let { Unit }
            override fun exitCode(): Int? = if (process.isAlive) null else runCatching { process.exitValue() }.getOrNull()
        }
    }
}

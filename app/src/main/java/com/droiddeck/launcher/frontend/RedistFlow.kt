package com.droiddeck.launcher.frontend

import java.io.File

/**
 * Redistributables install flow (DirectX, VC++ runtimes, ...).
 *
 * [plan] reads the game's Steam appmanifest (see [SteamRedists]) and returns
 * one step per missing component; [run] installs them through an [Installer].
 * The real installer lives on the device (winetricks-style component install
 * into the game's prefix); unit tests use a fake.
 */
object RedistFlow {
    enum class StepState { PENDING, INSTALLING, DONE, FAILED, SKIPPED }

    data class Step(
        val component: String,
        val label: String,
        var state: StepState = StepState.PENDING,
        var error: String? = null,
    )

    interface Installer {
        fun isInstalled(component: String): Boolean
        /** Returns null on success, or a human-readable error. */
        fun install(component: String, onProgress: (Int) -> Unit): String?
    }

    fun plan(gameDir: File?, appId: Int, installer: Installer): List<Step> =
        SteamRedists.detect(gameDir, appId)
            .filterNot { installer.isInstalled(it.componentName) }
            .map { Step(component = it.componentName, label = it.label) }

    /**
     * Installs every step in order, continuing past failures. Returns true
     * only if every step finished DONE (or the plan was empty).
     */
    fun run(
        steps: List<Step>,
        installer: Installer,
        onStep: (Step) -> Unit = {},
        onProgress: (Step, Int) -> Unit = { _, _ -> },
    ): Boolean {
        var ok = true
        for (step in steps) {
            step.state = StepState.INSTALLING
            onStep(step)
            val error = try {
                installer.install(step.component) { pct -> onProgress(step, pct) }
            } catch (t: Throwable) {
                t.message ?: t.toString()
            }
            if (error == null) {
                step.state = StepState.DONE
            } else {
                step.state = StepState.FAILED
                step.error = error
                ok = false
            }
            onStep(step)
        }
        return ok
    }
}

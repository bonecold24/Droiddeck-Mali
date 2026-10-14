package com.droiddeck.launcher.gpu

/**
 * Driver-selection migration for the Linux Vulkan driver.
 *
 * Rule (Gate A2):
 *  - no selection, or a selection that fails validation (missing files,
 *    bionic/Android object, corrupt ICD manifest, …) → migrate to the
 *    bundled glibc driver;
 *  - a valid selection — including one the user chose by hand — is kept.
 *
 * Pure logic (no Android dependencies) so host unit tests cover every case;
 * the Android glue in [GlibcDriverBundle] supplies the lambdas.
 */
object LinuxDriverMigration {

    sealed interface Decision {
        /** Keep the current selection (valid, or nothing better available). */
        data object Keep : Decision

        /** Switch the selection to [newId]. */
        data class Migrate(val newId: String) : Decision

        /** No valid selection and no bundled driver to migrate to. */
        data object Unavailable : Decision
    }

    /**
     * @param currentSelection the stored Linux driver id ("" when none).
     * @param isValid true when [String] names an installed, usable glibc driver.
     * @param bundledId the bundled glibc driver id, importing it if needed;
     *   null when no bundled driver is available.
     */
    @JvmStatic
    fun decide(
        currentSelection: String,
        isValid: (String) -> Boolean,
        bundledId: () -> String?,
    ): Decision {
        if (currentSelection.isNotEmpty() && isValid(currentSelection)) return Decision.Keep
        val id = bundledId() ?: return Decision.Unavailable
        // Already pointing at the bundled driver (e.g. re-imported after the
        // files were wiped): no preference write needed.
        if (id == currentSelection) return Decision.Keep
        return Decision.Migrate(id)
    }
}

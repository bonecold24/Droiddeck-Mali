package com.droiddeck.launcher.core

/**
 * The safe subset of FEX-Emu tuning knobs exposed in the UI.
 *
 * Deliberately excluded:
 * - EXTREME-only knobs (FEX_DISABLEL2CACHE, FEX_DYNAMICL1CACHE*): can corrupt
 *   execution or tank performance; they stay inside the named FEX presets.
 * - FEX_SMCCHECKS=none: disables self-modifying-code detection entirely.
 * - All BOX64_* variables: GameEnvironment.supported() treats Box64 env as
 *   unsupported, so exposing them here would write values the launcher drops.
 */
object FexSafeTuning {
    data class Knob(
        val key: String,
        val label: String,
        val detail: String,
        val safeValues: List<String>,
        val default: String,
    ) {
        init {
            require(default in safeValues) { "default $default not in safeValues for $key" }
        }
    }

    val knobs: List<Knob> = listOf(
        Knob(
            "FEX_TSOENABLED", "TSO memory model",
            "Emulate x86 total-store-ordering. On for compatibility, off for speed.",
            listOf("0", "1"), "0",
        ),
        Knob(
            "FEX_VECTORTSOENABLED", "Vector TSO",
            "TSO emulation for vector memory operations.",
            listOf("0", "1"), "0",
        ),
        Knob(
            "FEX_MEMCPYSETTSOENABLED", "memcpy TSO",
            "TSO emulation for memcpy/memset fast paths.",
            listOf("0", "1"), "0",
        ),
        Knob(
            "FEX_HALFBARRIERTSOENABLED", "Half-barrier TSO",
            "Cheaper partial TSO barrier emulation.",
            listOf("0", "1"), "0",
        ),
        Knob(
            "FEX_MULTIBLOCK", "Multi-block compilation",
            "Compile larger code blocks. Usually faster; disable for cranky titles.",
            listOf("0", "1"), "1",
        ),
        Knob(
            "FEX_X87REDUCEDPRECISION", "x87 reduced precision",
            "Run x87 math at reduced precision. Faster; can break old physics code.",
            listOf("0", "1"), "1",
        ),
        Knob(
            "FEX_SMCCHECKS", "Self-modifying-code checks",
            "How aggressively FEX watches for code that rewrites itself. 'none' is never offered.",
            listOf("default", "full"), "default",
        ),
        Knob(
            "FEX_HIDEHYPERVISORBIT", "Hide hypervisor bit",
            "Hide the hypervisor CPUID bit from the guest (helps some anti-cheat/DRM).",
            listOf("0", "1"), "0",
        ),
        Knob(
            "FEX_SMALLTSCSCALE", "Small TSC scale",
            "Scale the timestamp counter for games sensitive to timer frequency.",
            listOf("0", "1"), "0",
        ),
        Knob(
            "FEX_VOLATILEMETADATA", "Volatile metadata",
            "Treat FEX metadata pages as volatile; helps a few Denuvo-era titles.",
            listOf("0", "1"), "0",
        ),
    )

    private val byKey = knobs.associateBy { it.key }

    fun knob(key: String): Knob? = byKey[key]

    /**
     * Filter a user-supplied knob map down to safe keys and safe values.
     * Anything unknown or outside the safe value list is dropped silently so a
     * hand-edited profile can never smuggle an unsafe knob into the launch env.
     */
    fun toEnv(requested: Map<String, String>): Map<String, String> =
        requested.mapNotNull { (key, value) ->
            val knob = byKey[key] ?: return@mapNotNull null
            if (value in knob.safeValues) key to value else null
        }.toMap()

    fun defaults(): Map<String, String> = knobs.associate { it.key to it.default }
}

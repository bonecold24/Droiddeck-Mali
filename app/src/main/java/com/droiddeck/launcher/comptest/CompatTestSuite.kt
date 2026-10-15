package com.droiddeck.launcher.comptest

/**
 * Default compatibility-test suite definition.
 *
 * Ordering contract: Stage 0 environment probe first, then system checks
 * (vkcube → vulkaninfo → D3D9 cube → D3D11 cube), then Tier 1/2 game entries.
 * Games that are requested but not installed stay in the suite as specs — the
 * executor records them as SKIPPED so a missing game can never fail the suite.
 * Pure; host-unit-testable.
 */
object CompatTestSuite {
    data class InstalledGame(val gameId: Long, val name: String)

    fun systemChecks(): List<TestSpec> = listOf(
        TestSpec(
            id = "stage0-env", label = "Stage 0 environment probe", kind = TestKind.SYSTEM_CHECK,
            description = "Records kernel, device props, SELinux, /dev/mali0, ICD selection, " +
                "driver files, storage/RAM before any GPU test runs.",
            timeoutSec = 60,
        ),
        TestSpec(
            id = "vk-compute-test", label = "vk-compute-test (headless GPU)", kind = TestKind.SYSTEM_CHECK,
            description = "Headless Vulkan compute smoke test: instance, device enum, " +
                "vector-add shader, readback verify. No display needed; runs before vkcube " +
                "to separate GPU bring-up from display bring-up.",
            timeoutSec = 60,
        ),
        TestSpec(
            id = "vkcube", label = "vkcube (Vulkan cube)", kind = TestKind.SYSTEM_CHECK,
            description = "Renders the Vulkan cube through the active ICD for ~10s. " +
                "Requires a working display; reports PREREQ_MISSING if none.",
            timeoutSec = 60,
        ),
        TestSpec(
            id = "vulkaninfo", label = "vulkaninfo capture", kind = TestKind.SYSTEM_CHECK,
            description = "Captures the full Vulkan device/extension report for the active driver.",
            timeoutSec = 60,
        ),
        TestSpec(
            id = "d3d9-cube", label = "D3D9 sample cube", kind = TestKind.SYSTEM_CHECK,
            description = "Direct3D 9 sample cube via DXVK-Sarek (D3D9 verdict: go).",
            timeoutSec = 90, dxvkVariant = "DXVK-Sarek",
        ),
        TestSpec(
            id = "d3d11-cube", label = "D3D11 sample cube", kind = TestKind.SYSTEM_CHECK,
            description = "Direct3D 11 sample cube via stock DXVK (D3D11 target: FL 11_1).",
            timeoutSec = 90, dxvkVariant = "stock DXVK",
        ),
    )

    fun gameSpec(gameId: Long, name: String): TestSpec = TestSpec(
        id = "game-$gameId", label = name, kind = TestKind.GAME_TEST,
        description = "Launches the installed game: menu check, then gameplay sampling.",
        timeoutSec = 300, gameId = gameId,
    )

    /**
     * Default requested game list (Phase 6 tier plan — see docs/COMPAT.md).
     * Steam app ids are stable public identifiers. Pure; host-unit-testable.
     * Games not installed stay in the suite as specs so the executor records
     * them as SKIPPED; a missing game can never fail the suite.
     */
    fun defaultTierGames(): List<TestSpec> = listOf(
        // Tier 1 — light / free-to-play
        gameSpec(440L, "Team Fortress 2"),
        gameSpec(304930L, "Unturned"),
        gameSpec(291550L, "Brawlhalla"),
        // Tier 2 — D3D11 free-to-play
        gameSpec(230410L, "Warframe"),
        gameSpec(570L, "Dota 2"),
        gameSpec(238960L, "Path of Exile"),
        gameSpec(444090L, "Paladins"),
    )

    /**
     * Full ordered suite: system checks, then one spec per installed game.
     * Missing requested games are NOT filtered here; use [buildWithRequested] when the
     * tier list matters.
     */
    fun build(installedGames: List<InstalledGame>): List<TestSpec> =
        systemChecks() + installedGames.map { gameSpec(it.gameId, it.name) }

    /**
     * Suite from an explicit requested list (e.g. Tier 1/2 entries); requested games
     * that are not in [installedIds] remain as specs so the executor marks them SKIPPED.
     */
    fun buildWithRequested(requested: List<TestSpec>, installedIds: Set<Long>): List<TestSpec> =
        systemChecks() + requested

    fun isInstalled(spec: TestSpec, installedIds: Set<Long>): Boolean =
        spec.kind != TestKind.GAME_TEST || (spec.gameId != null && spec.gameId in installedIds)
}

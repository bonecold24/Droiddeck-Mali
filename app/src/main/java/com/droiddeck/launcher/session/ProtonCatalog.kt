package com.droiddeck.launcher.session

/**
 * Download-at-runtime Proton catalog. No Valve/third-party binaries are bundled
 * with the APK: every entry is metadata (official repo + pinned release tag +
 * asset pattern) resolved to a GitHub release download URL at install time.
 *
 * Pinned tags were verified against the GitHub releases API on 2026-10-14 and
 * each pinned asset exists for ARM64 (aarch64/arm64/arm64ec).
 */
object ProtonCatalog {
    enum class AssetKind { TARBALL, WCP }

    data class Entry(
        val id: String,
        val label: String,
        val repo: String,
        val pinnedTag: String,
        val assetPattern: Regex,
        val assetKind: AssetKind,
        val pinnedAsset: String,
        val notes: String = "",
    ) {
        fun downloadUrl(tag: String = pinnedTag, asset: String = pinnedAsset): String =
            "https://github.com/$repo/releases/download/$tag/$asset"
    }

    val entries: List<Entry> = listOf(
        Entry(
            id = "valve-arm64",
            label = "Valve Proton (ARM64)",
            repo = "The412Banner/DroidDeck-Proton",
            pinnedTag = "DroidDeck-Proton-11-7.5",
            assetPattern = Regex("aarch64\\.tar\\.(gz|xz)$"),
            assetKind = AssetKind.TARBALL,
            pinnedAsset = "DroidDeck-Proton-11-7.5-aarch64.tar.gz",
            notes = "Valve-based ARM64 Proton build for DroidDeck.",
        ),
        Entry(
            id = "proton-ge",
            label = "Proton-GE (ARM64)",
            repo = "GloriousEggroll/proton-ge-custom",
            pinnedTag = "GE-Proton11-9",
            assetPattern = Regex("aarch64\\.tar\\.(gz|xz)$"),
            assetKind = AssetKind.TARBALL,
            pinnedAsset = "GE-Proton11-9-aarch64.tar.gz",
        ),
        Entry(
            id = "proton-cachyos",
            label = "proton-cachyos (ARM64)",
            repo = "CachyOS/proton-cachyos",
            pinnedTag = "cachyos-11.0-20261005-slr",
            assetPattern = Regex("arm64\\.tar\\.(gz|xz)$"),
            assetKind = AssetKind.TARBALL,
            pinnedAsset = "proton-cachyos-11.0-20261005-slr-arm64.tar.xz",
        ),
        Entry(
            id = "arm64ec",
            label = "Proton ARM64EC",
            repo = "GameNative/proton-wine",
            pinnedTag = "proton-11.0-2-20261010",
            assetPattern = Regex("arm64ec\\.wcp(\\.xz)?$"),
            assetKind = AssetKind.WCP,
            pinnedAsset = "proton-11.0-2-arm64ec.wcp",
            notes = "ARM64EC Wine/Proton build (.wcp container format; needs the .wcp-aware installer path).",
        ),
    )

    fun byId(id: String?): Entry? = entries.firstOrNull { it.id == id }
}

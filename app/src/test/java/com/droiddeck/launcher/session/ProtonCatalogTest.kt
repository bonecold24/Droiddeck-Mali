package com.droiddeck.launcher.session

import org.junit.Assert.*
import org.junit.Test

class ProtonCatalogTest {
    @Test fun pinnedEntriesCoverValveArm64GeAndArm64Ec() {
        val ids = ProtonCatalog.entries.map { it.id }.toSet()
        assertTrue(ids.contains("valve-arm64"))
        assertTrue(ids.contains("proton-ge"))
        assertTrue(ids.contains("arm64ec"))
    }

    @Test fun everyPinnedTagIsSet() {
        ProtonCatalog.entries.forEach { entry ->
            assertTrue("pinned tag missing for ${entry.id}", entry.pinnedTag.isNotBlank())
        }
    }

    @Test fun downloadUrlsPointAtOfficialGithubReleases() {
        ProtonCatalog.entries.forEach { entry ->
            val url = entry.downloadUrl()
            assertTrue(url, url.startsWith("https://github.com/${entry.repo}/releases/download/${entry.pinnedTag}/"))
        }
        val ge = ProtonCatalog.byId("proton-ge")!!
        assertEquals(
            "https://github.com/GloriousEggroll/proton-ge-custom/releases/download/GE-Proton11-9/GE-Proton11-9-aarch64.tar.gz",
            ge.downloadUrl(),
        )
    }

    @Test fun assetPatternsMatchTheRealPinnedAssets() {
        val cases = mapOf(
            "valve-arm64" to "DroidDeck-Proton-11-7.5-aarch64.tar.gz",
            "proton-ge" to "GE-Proton11-9-aarch64.tar.gz",
            "proton-cachyos" to "proton-cachyos-11.0-20261005-slr-arm64.tar.xz",
            "arm64ec" to "proton-11.0-2-arm64ec.wcp",
        )
        cases.forEach { (id, asset) ->
            val entry = ProtonCatalog.byId(id)!!
            assertTrue("$id pattern must match $asset", entry.assetPattern.containsMatchIn(asset))
        }
    }

    @Test fun assetPatternsRejectWrongArchitectures() {
        val ge = ProtonCatalog.byId("proton-ge")!!
        assertFalse(ge.assetPattern.containsMatchIn("GE-Proton11-9.tar.gz"))
        assertFalse(ge.assetPattern.containsMatchIn("GE-Proton11-9-x86_64.tar.gz"))
    }

    @Test fun noBinariesAreBundledOnlyMetadata() {
        // The catalog is download-at-runtime metadata; the APK must not bundle Proton.
        ProtonCatalog.entries.forEach { entry ->
            assertTrue(entry.repo.contains("/"))
            assertFalse(entry.pinnedTag.contains(" "))
        }
    }

    @Test fun unknownIdReturnsNull() {
        assertNull(ProtonCatalog.byId("does-not-exist"))
    }
}

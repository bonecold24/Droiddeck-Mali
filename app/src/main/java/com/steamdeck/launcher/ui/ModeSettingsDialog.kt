package com.steamdeck.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steamdeck.launcher.core.FexPreset
import com.steamdeck.launcher.session.SessionPrefs
import com.steamdeck.launcher.session.SessionService

class DriverRow(val id: String, val name: String, val detail: String, val removable: Boolean)

class ModeSettings(
    val mode: String,
    val resolutionCap: Int,
    val shapeMode: String,
    val hdr: Boolean,
    val hdrReason: String?,
    val linuxRows: List<DriverRow>,
    val linuxSelected: String,
    val androidRows: List<DriverRow>,
    val androidSelected: String,
    val touchMode: String,
    val oscMode: String?,
    val directAudio: Boolean?,
    val mic: Boolean?,
    val renderer: String?,
    val gameStorage: String? = null,
    val storageOptions: List<Pair<String, String>> = emptyList(),
    val fexPreset: String? = null,
)

class ModeSettingsActions(
    val onResolution: (Int) -> Unit,
    val onShape: (String) -> Unit,
    val onHdr: (Boolean) -> Unit,
    val onSelectLinux: (String) -> Unit,
    val onImportLinux: () -> Unit,
    val onRemoveLinux: (String) -> Unit,
    val onSelectAndroid: (String) -> Unit,
    val onImportAndroid: () -> Unit,
    val onRemoveAndroid: (String) -> Unit,
    val onTouch: (String) -> Unit,
    val onOsc: (String) -> Unit,
    val onDirectAudio: (Boolean) -> Unit,
    val onMic: (Boolean) -> Unit,
    val onRenderer: (String) -> Unit,
    val onGameStorage: (path: String, label: String) -> Unit = { _, _ -> },
    val onPickGameStorageFolder: () -> Unit = {},
    val onFexPreset: (String) -> Unit = {},
    val onDismiss: () -> Unit,
)

@Composable
fun ModeSettingsPage(s: ModeSettings, a: ModeSettingsActions) {
    val steam = s.mode == SessionService.MODE_STEAM
    val host = rememberMenuHost()
    SettingsPage(
        host,
        title = if (steam) "Steam session" else "Desktop session",
        onBack = a.onDismiss,
    ) {
        SettingsGroup("Display") {
            val default = SessionPrefs.defaultResolutionCap(s.mode)
            ChoiceRow(
                host, "res", "Resolution", "Applies next session.",
                listOf(720 to "Up to 720p", 900 to "Up to 900p", 1080 to "Up to 1080p", 0 to "The panel's own")
                    .map { (cap, label) -> cap to (if (cap == default) "$label - the default" else label) },
                s.resolutionCap, note = "720p can improve menu responsiveness.",
                onPick = a.onResolution,
            )
            ChoiceRow(
                host, "shape", "Shape", "Adds bars to preserve 16:9.",
                listOf("auto" to "The panel's shape", "16:9" to "16:9 with bars"), s.shapeMode, onPick = a.onShape,
            )
        }
        SettingsGroup("HDR") {
            ToggleRow(
                host, "hdr", "HDR10 output",
                s.hdrReason?.let { "Not available: $it." }
                    ?: "Restart the app to apply.",
                checked = s.hdr && s.hdrReason == null, enabled = s.hdrReason == null, onChange = a.onHdr,
            )
        }
        SettingsGroup("Drivers") {
            DriverRowMenu(
                host, "rt", "Runtime driver",
                (if (steam) "Used by Steam and games." else "Used by desktop apps.") + " Applies next session.",
                s.linuxRows, s.linuxSelected, importLabel = "Import Turnip zip…",
                onSelect = a.onSelectLinux, onRemove = a.onRemoveLinux, onImport = a.onImportLinux,
            )
            DriverRowMenu(
                host, "panel", "Display driver",
                "Used by the compositor in both modes. Restart the app to apply.",
                s.androidRows, s.androidSelected, importLabel = "Import an AdrenoTools zip…",
                onSelect = a.onSelectAndroid, onRemove = a.onRemoveAndroid, onImport = a.onImportAndroid,
            )
        }
        SettingsGroup(if (steam) "Touch & controls" else "Touch") {
            ChoiceRow(
                host, "touch", "Touch", null,
                listOf("auto" to "Auto", "touchpad" to "Touchpad", "direct" to "Direct"), s.touchMode,
                note = "Auto uses touchpad on desktop and direct input in Steam. Touchpad: drag to move, tap to click.",
                onPick = a.onTouch,
            )
            if (steam && s.oscMode != null) ChoiceRow(
                host, "osc", "On-screen controls", null,
                listOf(
                    SessionPrefs.OSC_AUTO to "Auto",
                    SessionPrefs.OSC_ALWAYS to "Always",
                    SessionPrefs.OSC_STEAM_QAM to "Steam + QAM",
                    SessionPrefs.OSC_NEVER to "Never",
                ), s.oscMode,
                note = "Auto shows all controls without a controller. Steam + QAM shows only those buttons.", onPick = a.onOsc,
            )
        }
        if (steam && s.fexPreset != null) SettingsGroup("Games") {
            ChoiceRow(
                host, "fex", "FEX preset", "Applies on next game launch.",
                FexPreset.all.map { it.id to it.label }, s.fexPreset,
                note = FexPreset.byId(s.fexPreset).detail, onPick = a.onFexPreset,
            )
        }
        if (steam && s.directAudio != null && s.mic != null) SettingsGroup("Audio") {
            ToggleRow(host, "da", "DirectAudio for games", "Bypasses PulseAudio for lower latency.", s.directAudio, onChange = a.onDirectAudio)
            ToggleRow(host, "mic", "Microphone", "Uses the device microphone for voice chat.", s.mic, onChange = a.onMic)
        }
        if (steam && s.gameStorage != null) SettingsGroup("Game storage") {
            val custom = s.gameStorage.isNotEmpty() && s.gameStorage != "off" && s.storageOptions.none { it.second == s.gameStorage }
            val options = buildList {
                add("" to ("Automatic - the SD card when one is in" + (if (s.storageOptions.isEmpty()) " (none right now)" else "")))
                add("off" to "Internal only")
                for ((label, path) in s.storageOptions) add(path to label)
                if (custom) add(s.gameStorage to "Folder: ${s.gameStorage}")
            }
            val open = host.open == "storage"
            SettingsRow(
                "Second library",
                "Adds a library location in Steam. Applies next session.",
                highlighted = open,
            ) {
                androidx.compose.foundation.layout.Box {
                    ValueChip(options.firstOrNull { it.first == s.gameStorage }?.second?.substringBefore(" -") ?: "-", open) { host.open = if (open) null else "storage" }
                    AnchoredMenu(
                        open, onDismiss = { if (host.open == "storage") host.open = null }, title = "Second library",
                        note = "Games that stream assets from SD or shared storage may stutter. Keep them internal.",
                    ) {
                        for ((path, label) in options) MenuItem(label, checked = path == s.gameStorage) {
                            a.onGameStorage(path, if (path.isEmpty() || path == "off") "" else label.substringBefore(" ·"))
                            host.open = null
                        }
                        MenuItem("Choose a folder…", checked = false) { host.open = null; a.onPickGameStorageFolder() }
                    }
                }
            }
        }
        if (!steam && s.renderer != null) SettingsGroup("Renderer") {
            ChoiceRow(
                host, "renderer", "Desktop renderer", "Composites the desktop.",
                listOf("pixman" to "pixman - software", "gles2" to "gles2", "vulkan" to "vulkan"), s.renderer,
                note = "GLES2 and Vulkan require a DRM render node, unavailable on most devices.", onPick = a.onRenderer,
            )
        }
    }
}

@Composable
private fun DriverRowMenu(
    host: MenuHost, key: String, label: String, hint: String, rows: List<DriverRow>, selected: String, importLabel: String,
    onSelect: (String) -> Unit, onRemove: (String) -> Unit, onImport: () -> Unit,
) {
    val open = host.open == key
    val colors = MaterialTheme.colorScheme
    SettingsRow(label, hint, highlighted = open) {
        androidx.compose.foundation.layout.Box {
            ValueChip(rows.firstOrNull { it.id == selected }?.name ?: rows.firstOrNull()?.name ?: "-", open) { host.open = if (open) null else key }
            AnchoredMenu(open, onDismiss = { if (host.open == key) host.open = null }, title = label) {
                for (row in rows) MenuItem(
                    row.name, checked = row.id == selected, detail = row.detail.ifEmpty { null },
                    trailing = if (row.removable) ({
                        Text(
                            "✕", fontSize = 12.sp, color = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp).padding(4.dp).clickable { onRemove(row.id); host.open = null },
                        )
                    }) else null,
                ) { onSelect(row.id); host.open = null }
                MenuItem(importLabel, checked = false) { host.open = null; onImport() }
            }
        }
    }
}

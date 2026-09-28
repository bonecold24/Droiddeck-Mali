package com.droiddeck.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CoreRow(val core: Int, val label: String)

@Composable
fun PerformancePage(
    cores: List<CoreRow>,
    clientOverride: Boolean,
    clientCores: Set<Int>,
    gameCores: Set<Int>,
    tuSysmem: Boolean,
    zinkLazy: Boolean,
    glThread: Boolean,
    noGlError: Boolean,
    noXalia: Boolean,
    prootNoSeccomp: Boolean,
    phantomWarning: String?,
    onClientOverride: (Boolean) -> Unit,
    onTuSysmem: (Boolean) -> Unit,
    onZinkLazy: (Boolean) -> Unit,
    onGlThread: (Boolean) -> Unit,
    onNoGlError: (Boolean) -> Unit,
    onNoXalia: (Boolean) -> Unit,
    onProotNoSeccomp: (Boolean) -> Unit,
    onClientCore: (Int, Boolean) -> Unit,
    onGameCore: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val host = rememberMenuHost()
    val colors = MaterialTheme.colorScheme
    val coreItems = cores.map { it.core to it.label }
    SettingsPage(
        host, title = "Performance",
        lede = "CPU cores and session fixes. Applies next session.",
        onBack = onDismiss,
    ) {
        SettingsGroup("Steam client cores") {
            ToggleRow(
                host, "override", "Override Steam's own core choice",
                "Pins Steam, its UI helper, and gamescope to the cores below. Reapplied every few seconds.",
                clientOverride, onChange = onClientOverride,
            )
            MultiRow(
                host, "clientCores", "Client cores", if (clientOverride) "Choose cores for Steam." else "Enable the override to choose.",
                coreItems, clientCores, enabled = clientOverride, onToggle = onClientCore,
            )
        }
        SettingsGroup("Game cores") {
            MultiRow(
                host, "gameCores", "Game cores",
                "Choose cores for games. All selected lets Android schedule across every core.",
                coreItems, gameCores, onToggle = onGameCore,
            )
        }
        SettingsGroup("Client interface") {
            ToggleRow(
                host, "glthread", "Threaded GL",
                "May improve Steam menu responsiveness. Device impact is unverified.",
                glThread, onChange = onGlThread,
            )
            ToggleRow(
                host, "zink", "Zink: lazy descriptors",
                "Recommended for drivers without descriptor buffers.",
                zinkLazy, onChange = onZinkLazy,
            )
            ToggleRow(
                host, "noglerror", "Skip GL error checks",
                "Disables per-call GL validation.",
                noGlError, onChange = onNoGlError,
            )
        }
        SettingsGroup("Session fixes") {
            ToggleRow(
                host, "sysmem", "Turnip: sysmem rendering",
                "Required on Adreno 710/720/722. May fix corruption on other Adreno GPUs, but can reduce performance.",
                tuSysmem, onChange = onTuSysmem,
            )
            ToggleRow(
                host, "xalia", "Skip Steam's xalia helper",
                "Disables Proton's gamepad navigation helper. Try if sessions crash at startup.",
                noXalia, onChange = onNoXalia,
            )
            ToggleRow(
                host, "seccomp", "Run proot without seccomp",
                "May fix missing syscall errors on some kernels, but can reduce performance.",
                prootNoSeccomp, onChange = onProotNoSeccomp,
            )
        }
        if (phantomWarning != null) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.error.copy(alpha = 0.08f))
                    .border(1.dp, colors.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp),
            ) {
                Text("Android is set to kill this session", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.error)
                Text(phantomWarning, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

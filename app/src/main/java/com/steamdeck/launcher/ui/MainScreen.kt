package com.steamdeck.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steamdeck.launcher.R

class MainUiState(
    val installed: String?,
    val ready: Boolean,
    val available: String?,
    val availableSize: String?,
    val busy: Boolean,
    val stage: String,
    val percent: Int,
    val failed: Boolean,
    val frameGenLabel: String,
    val desktopInstalled: Boolean,
    val offlineAccount: String?,
    val offline: Boolean,
    val romsDir: String?,
    val logsEnabled: Boolean,
    val emulators: List<Pair<String, String>> = emptyList(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onPlay: () -> Unit,
    onDesktop: () -> Unit,
    onApps: () -> Unit,
    onRuntime: () -> Unit,
    onFrameGen: () -> Unit,
    onProtons: () -> Unit,
    onSteamSettings: () -> Unit,
    onDesktopSettings: () -> Unit,
    onPerformance: () -> Unit,
    onOffline: () -> Unit,
    onRoms: () -> Unit,
    onFiles: () -> Unit,
    onLaunchEmulator: (path: String) -> Unit,
    onEmulatorHelp: () -> Unit,
    onLogs: () -> Unit,
    onCredits: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val designHeight = 480.dp
        val k = (maxHeight / designHeight).coerceIn(0.55f, 1f)
        val colors = MaterialTheme.colorScheme

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 420.dp * k)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp * k, vertical = 12.dp * k),
        ) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp * k),
            )
            Spacer(Modifier.height(6.dp * k))
            Text("SteamDeck", fontSize = 22.sp * k, color = colors.onBackground)
            Spacer(Modifier.height(14.dp * k))

            val status = when {
                state.busy -> "Working…"
                !state.ready -> "Linux runtime not installed"
                else -> "Ready"
            }
            Text(status, fontSize = 15.sp * k, color = colors.onBackground)
            val detail = when {
                state.busy -> if (state.percent >= 0) "${state.stage} ${state.percent}%" else state.stage
                state.failed -> "Install failed. Nothing changed."
                !state.ready -> state.available?.let { "$it · ${state.availableSize} download, one time" }
                    ?: "Could not reach the runtime catalog"
                state.available != null && state.available != state.installed ->
                    "Runtime ${state.installed ?: "?"} installed · ${state.available} available"
                else -> null
            }
            if (detail != null) Text(
                detail, fontSize = 12.sp * k, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp * k),
            )
            if (state.busy) {
                Spacer(Modifier.height(6.dp * k))
                if (state.percent >= 0) {
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(14.dp * k))

            val buttonHeight = 40.dp * k
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp * k), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onPlay, enabled = state.ready && !state.busy,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) { Text("Play", fontSize = 14.sp * k) }
                CogButton(k, onSteamSettings)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp * k), modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k)) {
                OutlinedButton(
                    onClick = onDesktop, enabled = state.ready && !state.busy && state.desktopInstalled,
                    modifier = Modifier.weight(1f).height(buttonHeight),
                ) { Text("Desktop", fontSize = 13.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                CogButton(k, onDesktopSettings)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp * k),
                verticalArrangement = Arrangement.spacedBy(4.dp * k),
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k),
            ) {
                    OutlinedButton(
                        onClick = onRoms,
                        contentPadding = PaddingValues(horizontal = 10.dp * k, vertical = 0.dp),
                        modifier = Modifier.height(30.dp * k),
                    ) {
                        Text(
                            "ROMs: " + (state.romsDir?.substringAfterLast('/')?.ifEmpty { state.romsDir } ?: "choose folder"),
                            fontSize = 11.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    for ((label, path) in state.emulators) {
                        OutlinedButton(
                            onClick = { onLaunchEmulator(path) }, enabled = state.ready && !state.busy,
                            contentPadding = PaddingValues(horizontal = 10.dp * k, vertical = 0.dp),
                            modifier = Modifier.height(30.dp * k),
                        ) { Text(label, fontSize = 11.sp * k, maxLines = 1) }
                    }
                    OutlinedButton(
                        onClick = onEmulatorHelp,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.width(30.dp * k).height(30.dp * k),
                    ) { Text("?", fontSize = 12.sp * k) }
            }
            OutlinedButton(
                onClick = onOffline, enabled = state.offlineAccount != null,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp * k).height(buttonHeight),
            ) {
                Text(
                    when {
                        state.offlineAccount == null -> "Start offline"
                        state.offline -> "Start offline: on · ${state.offlineAccount}"
                        else -> "Start offline: off"
                    },
                    fontSize = 13.sp * k,
                )
            }
            Spacer(Modifier.height(8.dp * k))

            val runtimeLabel = when {
                state.busy -> "Working…"
                !state.ready -> "Install Linux runtime"
                state.available != null && state.available != state.installed -> "Update Linux runtime"
                else -> "Remove Linux runtime"
            }
            val tileGap = Arrangement.spacedBy(6.dp * k)
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile(runtimeLabel, null, k, Modifier.weight(1f), enabled = !state.busy, onClick = onRuntime)
                MenuTile("Desktop & apps", null, k, Modifier.weight(1f), enabled = state.ready && !state.busy, onClick = onApps)
            }
            Spacer(Modifier.height(6.dp * k))
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile("Frame generation", state.frameGenLabel, k, Modifier.weight(1f), onClick = onFrameGen)
                MenuTile("Compatibility tools", null, k, Modifier.weight(1f), onClick = onProtons)
            }
            Spacer(Modifier.height(6.dp * k))
            Row(horizontalArrangement = tileGap, modifier = Modifier.fillMaxWidth()) {
                MenuTile("Performance", null, k, Modifier.weight(1f), onClick = onPerformance)
                MenuTile("Files", null, k, Modifier.weight(1f), onClick = onFiles)
            }
            Spacer(Modifier.height(6.dp * k))
            Row(modifier = Modifier.fillMaxWidth()) {
                MenuTile(
                    "Session logs", if (state.logsEnabled) "on · Download/SteamDeck" else "off",
                    k, Modifier.weight(1f), onClick = onLogs,
                )
            }
            Spacer(Modifier.height(10.dp * k))
            Text(
                "by The412Banner and maxjivi05 · credits",
                fontSize = 11.sp * k, color = colors.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onCredits).padding(4.dp),
            )
        }
    }
}

@Composable
private fun CogButton(k: Float, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.width(44.dp * k).height(40.dp * k),
        contentPadding = PaddingValues(0.dp),
    ) { Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp * k)) }
}

@Composable
private fun MenuTile(
    title: String,
    value: String?,
    k: Float,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.height(if (value != null) 52.dp * k else 44.dp * k),
        contentPadding = PaddingValues(horizontal = 8.dp * k, vertical = 4.dp * k),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (value != null) {
                Text(
                    value, fontSize = 10.sp * k, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun EmulatorHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emulators") },
        text = { Text("Place games in the ROMs folder. In RPCS3, install firmware from File > Install Firmware.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Credits") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("The412Banner - the app, the Wayland compositor, the runtime and the Steam session.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("maxjivi05 (Max) - the gamescope runtime this is built on: the proot session, the session shim, the fake-evdev interposer and the controller work, from WinNative.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "GPL-3.0. Valve, Steam, Steam Deck and Proton are trademarks of Valve Corporation; this project is not affiliated with Valve. gamescope, Mesa, Turnip, Xwayland, PulseAudio, proot and Arch Linux ARM are their authors' own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

package com.steamdeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PackageRow(
    val id: String, val name: String, val tier: Int, val version: String, val size: String,
    val notes: String, val installed: String?,
    val launchers: List<Pair<String, String>> = emptyList(),
)

@Composable
fun DesktopAppsDialog(
    rows: List<PackageRow>?,
    busyStage: String?,
    busyPercent: Int,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLaunch: (path: String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (busyStage == null) onDismiss() },
        title = { Text("Desktop & apps") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (busyStage != null) {
                    Text(if (busyPercent >= 0) "$busyStage $busyPercent%" else busyStage, style = MaterialTheme.typography.bodySmall)
                    if (busyPercent >= 0) LinearProgressIndicator(progress = { busyPercent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Spacer(Modifier.height(8.dp))
                }
                when {
                    rows == null -> Text("Could not reach the package catalog", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    rows.isEmpty() -> Text("Loading the catalog…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Unit
                }
                var lastTier = 0
                for (row in rows ?: emptyList()) {
                    if (row.tier != lastTier) {
                        lastTier = row.tier
                        Text(
                            when (row.tier) {
                                1 -> "Native ARM64"
                                2 -> "ARM64 · experimental"
                                else -> "Source build · experimental"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                (if (row.installed != null) "Installed ${row.installed}" else "${row.version} · ${row.size}") +
                                    (if (row.notes.isNotEmpty()) " · ${row.notes}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (row.installed != null) TextButton(enabled = busyStage == null, onClick = { onRemove(row.id) }) { Text("Remove") }
                        else TextButton(enabled = busyStage == null, onClick = { onInstall(row.id) }) { Text("Install") }
                    }
                    if (row.installed != null && row.launchers.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 2.dp)) {
                            for ((label, path) in row.launchers) {
                                TextButton(enabled = busyStage == null, onClick = { onLaunch(path) }) {
                                    Text(" $label", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = busyStage == null, onClick = onDismiss) { Text("Close") } },
    )
}

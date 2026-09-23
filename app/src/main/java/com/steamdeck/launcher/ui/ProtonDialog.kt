package com.steamdeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class ProtonRow(val id: String, val name: String, val installed: String?, val queued: Boolean)

@Composable
fun ProtonDialog(
    rows: List<ProtonRow>,
    onInstall: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compatibility tools") },
        text = {
            Column {
                Text(
                    "Native ARM64 Proton builds beside the ARM64 Proton Valve ships. Installed when a session starts - a large download - and then chosen per game in Steam under Properties → Compatibility.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                for (row in rows) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    row.installed != null -> "installed: ${row.installed}"
                                    row.queued -> "installs at the next session start"
                                    else -> "not installed"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        when {
                            row.installed != null -> TextButton(onClick = { onRemove(row.id) }) { Text("Remove") }
                            row.queued -> TextButton(onClick = { onCancel(row.id) }) { Text("Cancel") }
                            else -> TextButton(onClick = { onInstall(row.id) }) { Text("Install") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

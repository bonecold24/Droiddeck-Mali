package com.steamdeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun RomsDialog(path: String?, onChoose: () -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ROMs folder") },
        text = {
            Column {
                Text(
                    "A folder on this device that every session shows as ROMs in the home folder " +
                        "(/root/ROMs) - open it from any emulator's file dialog. All of internal " +
                        "storage is there too, as Storage.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    path ?: "No folder chosen yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (path != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Takes effect at the next session. A folder on an SD card works too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onChoose) { Text(if (path == null) "Choose folder" else "Change folder") } },
        dismissButton = {
            if (path != null) TextButton(onClick = onClear) { Text("Forget") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

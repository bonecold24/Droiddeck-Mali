package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun WirelessAdbFixDialog(
    onDismiss: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onPair: (String, Int, String, (String?) -> Unit) -> Unit,
    onFindConnectPort: (String, (Int?) -> Unit) -> Unit,
    onApply: (String, Int, (String?) -> Unit) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var host by rememberSaveable { mutableStateOf("") }
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Fix over Wi-Fi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> {
                        Text("Enable Developer options and Wireless debugging on this device, and connect to Wi-Fi. Keep DroidDeck visible beside the pairing-code screen, such as in split screen. Android may close the code if you leave that screen.")
                        Text("Enter the IP address and pairing port shown with the code.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(host, { host = it.trim() }, label = { Text("Device IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pairingPort, { pairingPort = it.filter(Char::isDigit).take(5) }, label = { Text("Pairing port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, label = { Text("Pairing code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                    1 -> {
                        Text("Paired. Return to the Wireless debugging screen and enter the current IP address and port shown there. This connect port is different from the pairing port.")
                        OutlinedTextField(host, { host = it.trim() }, label = { Text("Device IP address") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(connectPort, { connectPort = it.filter(Char::isDigit).take(5) }, label = { Text("Wireless debugging port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                    else -> Text(message ?: "Android confirmed the child-process limit is disabled. You can start Steam now.")
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text(if (step == 0) "Pairing…" else "Applying and checking…")
                }
            }
        },
        confirmButton = {
            when (step) {
                0 -> TextButton(enabled = !busy && host.isNotBlank() && validPort(pairingPort) && pairingCode.length >= 6, onClick = {
                    busy = true
                    message = null
                    onPair(host, pairingPort.toInt(), pairingCode) { error ->
                        busy = false
                        pairingCode = ""
                        if (error == null) { step = 1; message = null } else message = error
                        if (error == null) {
                            busy = true
                            message = "Paired. Finding this device’s Wireless debugging port…"
                            onFindConnectPort(host) { discoveredPort ->
                                if (discoveredPort == null) {
                                    busy = false
                                    message = "Automatic discovery did not find the device. Enter the current Wireless debugging port below."
                                } else {
                                    connectPort = discoveredPort.toString()
                                    message = "Device found. Applying the setting…"
                                    onApply(host, discoveredPort) { applyError ->
                                        busy = false
                                        if (applyError == null) { step = 2; message = null }
                                        else message = "Could not apply automatically. Check the port below and retry. $applyError"
                                    }
                                }
                            }
                        }
                    }
                }) { Text("Pair") }
                1 -> TextButton(enabled = !busy && validPort(connectPort), onClick = {
                    busy = true
                    message = null
                    onApply(host, connectPort.toInt()) { error ->
                        busy = false
                        if (error == null) { step = 2; message = null } else message = error
                    }
                }) { Text("Disable child-process limit") }
                else -> TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = {
            Row {
                if (step < 2) TextButton(enabled = !busy, onClick = onOpenDeveloperOptions) { Text("Developer options") }
                TextButton(enabled = !busy, onClick = onDismiss) { Text(if (step == 2) "Close" else "Cancel") }
            }
        },
    )
}

private fun validPort(value: String): Boolean = value.toIntOrNull()?.let { it in 1..65535 } == true

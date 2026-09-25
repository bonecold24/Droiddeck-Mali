package com.droiddeck.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun WirelessAdbFixPage(
    onBack: () -> Unit,
    desiredEnabled: Boolean,
    onOpenDeveloperOptions: () -> Unit,
    onCopyCommand: () -> Unit,
    onPair: (String, Int, String, (String?) -> Unit) -> Unit,
    onFindConnectPort: (String, (Int?) -> Unit) -> Unit,
    onApply: (String, Int, Boolean, (String?) -> Unit) -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(0) }
    var host by rememberSaveable { mutableStateOf("") }
    var pairingPort by rememberSaveable { mutableStateOf("") }
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    BackHandler { if (!busy) onBack() }

    SettingsPage(
        host = rememberMenuHost(),
        title = if (step == 2) "Wireless ADB ready" else "Connect over Wi-Fi",
        eyebrow = "Setup",
        lede = "Pair Android Wireless debugging so DroidDeck can ${if (desiredEnabled) "restore" else "disable"} the child-process limit for Steam.",
        onBack = { if (!busy) onBack() },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> {
                        SettingsGroup("Pairing details") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("In Settings, open Developer options → Wireless debugging → Pair device with pairing code. Keep the code screen open while you enter these details.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it.trim() },
                                    label = { Text("Device IP address") },
                                    placeholder = { Text("For example, 192.168.1.25") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = pairingPort,
                                    onValueChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Pairing port") },
                                    placeholder = { Text("Shown with the pairing code") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = pairingCode,
                                    onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                                    label = { Text("6-digit pairing code") },
                                    placeholder = { Text("Shown in the pairing dialog") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        SettingsGroup("What will change") {
                            Text(
                                if (desiredEnabled) "DroidDeck will restore Android’s child-process limit for Steam."
                                else "DroidDeck will disable Android’s child-process limit for Steam.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                    1 -> {
                        SettingsGroup("Wireless debugging connection") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("Pairing is complete. Automatic discovery did not find the connection port, so enter the current Wireless debugging port from Settings. It is different from the pairing port.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it.trim() },
                                    label = { Text("Device IP address") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = connectPort,
                                    onValueChange = { connectPort = it.filter(Char::isDigit).take(5) },
                                    label = { Text("Wireless debugging port") },
                                    placeholder = { Text("Current port shown in Settings") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    else -> {
                        SettingsGroup("Child-process limit") {
                            Text(
                                "Android confirmed the limit is ${if (desiredEnabled) "enabled" else "disabled"}.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(message ?: if (step == 0) "Pairing…" else "Applying and checking…")
                    }
                } else {
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                if (step < 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Developer options", enabled = !busy, onClick = onOpenDeveloperOptions)
                        if (!desiredEnabled) SecondaryButton("Copy ADB", enabled = !busy, onClick = onCopyCommand)
                    }
                }
                when (step) {
                    0 -> PrimaryButton(
                        "Pair",
                        enabled = !busy && host.isNotBlank() && validPort(pairingPort) && pairingCode.length == 6,
                        onClick = {
                            busy = true
                            message = null
                            onPair(host, pairingPort.toInt(), pairingCode) { error ->
                                busy = false
                                pairingCode = ""
                                if (error == null) {
                                    step = 1
                                    busy = true
                                    message = "Paired. Finding the Wireless debugging port…"
                                    onFindConnectPort(host) { discoveredPort ->
                                        if (discoveredPort == null) {
                                            busy = false
                                            message = "Automatic discovery did not find the port. Enter the current port from Settings."
                                        } else {
                                            connectPort = discoveredPort.toString()
                                            message = "Device found. Applying the setting…"
                                            onApply(host, discoveredPort, desiredEnabled) { applyError ->
                                                busy = false
                                                if (applyError == null) {
                                                    step = 2
                                                    message = null
                                                } else {
                                                    message = "Could not apply automatically. Check the port below and retry. $applyError"
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    message = error
                                }
                            }
                        },
                    )
                    1 -> PrimaryButton(
                        if (desiredEnabled) "Enable child-process limit" else "Disable child-process limit",
                        enabled = !busy && validPort(connectPort),
                        onClick = {
                            busy = true
                            message = null
                            onApply(host, connectPort.toInt(), desiredEnabled) { error ->
                                busy = false
                                if (error == null) {
                                    step = 2
                                    message = null
                                } else {
                                    message = error
                                }
                            }
                        },
                    )
                    else -> PrimaryButton("Done", enabled = !busy, onClick = onBack)
                }
            }
        }
    }
}

private fun validPort(value: String): Boolean = value.toIntOrNull()?.let { it in 1..65535 } == true

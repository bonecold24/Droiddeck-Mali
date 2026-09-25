package com.droiddeck.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.core.PhantomProcessLimit

@Composable
fun WirelessAdbFixPage(
    onBack: () -> Unit,
    desiredEnabled: Boolean,
    onOpenDeveloperOptions: () -> Unit,
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

    val pairNow: () -> Unit = {
        val pairingHost = host
        busy = true
        message = null
        onPair(pairingHost, pairingPort.toInt(), pairingCode) { error ->
            pairingCode = ""
            if (error != null) {
                busy = false
                message = error
            } else {
                step = 1
                busy = true
                message = "Paired. Finding the Wireless debugging port…"
                onFindConnectPort(pairingHost) { discoveredPort ->
                    if (discoveredPort == null) {
                        busy = false
                        message = "Enter the current Wireless debugging port from Settings."
                    } else {
                        connectPort = discoveredPort.toString()
                        message = "Device found. Applying the setting…"
                        onApply(pairingHost, discoveredPort, desiredEnabled) { applyError ->
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
            }
        }
    }
    val applyPort: () -> Unit = {
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
    }

    BackHandler { if (!busy) onBack() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactSplit = maxWidth < 620.dp && maxHeight < 500.dp
        val form: @Composable () -> Unit = {
            WirelessStepForm(
                step = step,
                host = host,
                onHostChange = { host = it.trim() },
                pairingPort = pairingPort,
                onPairingPortChange = { pairingPort = it.filter(Char::isDigit).take(5) },
                pairingCode = pairingCode,
                onPairingCodeChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                connectPort = connectPort,
                onConnectPortChange = { connectPort = it.filter(Char::isDigit).take(5) },
                busy = busy,
                message = message,
                focusManager = focusManager,
                desiredEnabled = desiredEnabled,
                onPair = pairNow,
                onApply = applyPort,
            )
        }
        SettingsPage(
            host = rememberMenuHost(),
            title = when {
                compactSplit && step < 2 -> "Wireless ADB"
                step == 2 -> "Wireless ADB ready"
                else -> "Set up on-device ADB"
            },
            eyebrow = "Setup",
            lede = when {
                compactSplit && step == 0 -> "If the option is missing, pair over Wireless debugging."
                compactSplit && step == 1 -> "Enter the Wireless debugging connection port."
                compactSplit -> "The child-process limit was updated."
                step == 0 -> "Use step 2 if Developer options has no child-process setting. In Android Settings, open Developer options → Wireless debugging → Pair device with pairing code."
                step == 1 -> "The pairing port and the Wireless debugging connection port are different."
                else -> "The child-process limit was updated over Wireless debugging."
            },
            onBack = { if (!busy) onBack() },
            action = if (compactSplit && step < 2) {
                { SecondaryButton("Developer options", enabled = !busy, onClick = onOpenDeveloperOptions) }
            } else null,
            scrollContent = compactSplit,
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wide = maxWidth >= 620.dp
                if (step == 2) {
                    Column(
                        modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SettingsGroup("Child-process limit") {
                            Text(
                                "Android confirmed the limit is ${if (desiredEnabled) "enabled" else "disabled"}.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        PrimaryButton("Done", enabled = !busy, onClick = onBack)
                    }
                } else if (compactSplit) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        form()
                        CompactComputerFallback(desiredEnabled)
                    }
                } else {
                    val routes: @Composable () -> Unit = {
                        FallbackOrder(desiredEnabled, busy, onOpenDeveloperOptions)
                    }
                    if (wide) {
                        Row(
                            modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) { routes() }
                            Column(Modifier.weight(1f)) { form() }
                        }
                    } else {
                        Column(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            routes()
                            form()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WirelessStepForm(
    step: Int,
    host: String,
    onHostChange: (String) -> Unit,
    pairingPort: String,
    onPairingPortChange: (String) -> Unit,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    connectPort: String,
    onConnectPortChange: (String) -> Unit,
    busy: Boolean,
    message: String?,
    focusManager: FocusManager,
    desiredEnabled: Boolean,
    onPair: () -> Unit,
    onApply: () -> Unit,
) {
    if (step == 0) {
        SettingsGroup("2 · Pair Wireless debugging") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdbTextField(
                        value = host,
                        onValueChange = onHostChange,
                        label = "Device IP",
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        modifier = Modifier.weight(1f),
                    )
                    AdbTextField(
                        value = pairingPort,
                        onValueChange = onPairingPortChange,
                        label = "Pairing port",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdbTextField(
                        value = pairingCode,
                        onValueChange = onPairingCodeChange,
                        label = "Pairing code",
                        placeholder = "6 digits",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        onDone = focusManager::clearFocus,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        "Pair",
                        enabled = !busy && host.isNotBlank() && validPort(pairingPort) && pairingCode.length == 6,
                        onClick = onPair,
                    )
                }
                StatusMessage(busy, message)
            }
        }
    } else {
        SettingsGroup("2 · Apply over Wireless debugging") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AdbTextField(
                        value = host,
                        onValueChange = onHostChange,
                        label = "Device IP",
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        modifier = Modifier.weight(1f),
                    )
                    AdbTextField(
                        value = connectPort,
                        onValueChange = onConnectPortChange,
                        label = "Connection port",
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                        onDone = focusManager::clearFocus,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusMessage(busy, message, Modifier.weight(1f))
                    PrimaryButton(
                        if (desiredEnabled) "Enable child-process limit" else "Disable child-process limit",
                        enabled = !busy && validPort(connectPort),
                        onClick = onApply,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onNext?.invoke() },
            onDone = { onDone?.invoke() },
        ),
        modifier = modifier.fillMaxWidth().height(56.dp),
    )
}

@Composable
private fun FallbackOrder(
    desiredEnabled: Boolean,
    busy: Boolean,
    onOpenDeveloperOptions: () -> Unit,
) {
    SettingsGroup("Other ways to change it") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("1 · Developer options (try first)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text("Set “Restrict child processes” ${if (desiredEnabled) "on" else "off"}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton("Open Developer options", enabled = !busy, onClick = onOpenDeveloperOptions)
            Text("3 · Computer ADB (last resort)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text("Only if Wireless debugging is unavailable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                PhantomProcessLimit.adbCommand(desiredEnabled),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompactComputerFallback(desiredEnabled: Boolean) {
    SettingsGroup("3 · Computer ADB last resort") {
        Text(
            PhantomProcessLimit.adbCommand(desiredEnabled),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StatusMessage(busy: Boolean, message: String?, modifier: Modifier = Modifier) {
    if (busy) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(message ?: "Working…", style = MaterialTheme.typography.bodySmall)
        }
    } else {
        message?.let { Text(it, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

private fun validPort(value: String): Boolean = value.toIntOrNull()?.let { it in 1..65535 } == true

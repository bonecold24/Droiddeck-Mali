package com.droiddeck.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.core.PhantomProcessLimit
import com.droiddeck.launcher.core.PhantomProcessStatus

@Composable
fun PhantomProcessGatePage(
    status: PhantomProcessStatus,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    onFixOverWifi: () -> Unit,
    onCopyCommand: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    SettingsPage(
        host = rememberMenuHost(),
        title = "Steam cannot start yet",
        eyebrow = "Steam",
        lede = "${PhantomProcessLimit.title(status)} · checking again every two seconds",
        onBack = onDismiss,
        scrollContent = false,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 620.dp
            Column(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { GateInstructions(status) }
                        Column(Modifier.weight(1f)) { AdbFallback(onCopyCommand) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Fix over Wi-Fi", onClick = onFixOverWifi)
                        SecondaryButton("Developer options", onClick = onOpenDeveloperOptions)
                        SecondaryButton("Check again", onClick = onRefresh)
                        SecondaryButton("Not now", onClick = onDismiss)
                    }
                } else {
                    GateInstructions(status)
                    AdbFallback(onCopyCommand)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Fix over Wi-Fi", onClick = onFixOverWifi)
                        SecondaryButton("Developer options", onClick = onOpenDeveloperOptions)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Check again", onClick = onRefresh)
                        SecondaryButton("Not now", onClick = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun GateInstructions(status: PhantomProcessStatus) {
    SettingsGroup("What to change") {
        Text(
            PhantomProcessLimit.gateInstructions(status),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun AdbFallback(onCopyCommand: () -> Unit) {
    SettingsGroup("Computer ADB fallback") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "If you cannot find the setting in Developer options, connect this device to a computer with ADB and run:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                PhantomProcessLimit.ADB_COMMAND,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton("Copy ADB command", onClick = onCopyCommand)
        }
    }
}

@Composable
fun DeveloperDisplayChoicePage(
    displays: List<Pair<Int, String>>,
    onMainScreen: () -> Unit,
    onSecondaryScreen: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    SettingsPage(
        host = rememberMenuHost(),
        title = "Open Developer options",
        eyebrow = "Setup",
        lede = "Choose which display to open Android Settings on.",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsGroup("Display") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryButton("Main screen", onClick = onMainScreen)
                    displays.forEach { (id, label) ->
                        SecondaryButton(label, onClick = { onSecondaryScreen(id) })
                    }
                }
            }
        }
    }
}

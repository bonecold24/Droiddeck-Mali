package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R

private data class CpuCluster(val labelRes: Int, val cores: IntRange)

// Dimensity 9300+: 4x A520 @ 2.0 GHz, 3x A720 @ 2.85 GHz, 1x X4 @ 3.4 GHz.
private val clusters9300Plus = listOf(
    CpuCluster(R.string.cpu_cluster_little, 0..3),
    CpuCluster(R.string.cpu_cluster_mid, 4..6),
    CpuCluster(R.string.cpu_cluster_prime, 7..7),
)

/**
 * CPU core pinning UI for the 9300+. An empty selection means "default"
 * (no restriction); otherwise the game is pinned to the chosen cores.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CpuPinningPicker(
    selected: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SettingsGroup(stringResource(R.string.cpu_pinning_title)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.cpu_pinning_detail),
                fontSize = 12.sp, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp),
            ) {
                FilterChip(
                    selected = selected.isEmpty(),
                    onClick = { onSelectionChange(emptySet()) },
                    label = { Text(stringResource(R.string.cpu_pinning_default), fontSize = 12.sp) },
                )
            }
            for (cluster in clusters9300Plus) {
                Text(
                    stringResource(cluster.labelRes),
                    fontSize = 12.sp, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, start = 4.dp, bottom = 4.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                ) {
                    for (core in cluster.cores) {
                        val on = core in selected
                        FilterChip(
                            selected = on,
                            onClick = {
                                onSelectionChange(if (on) selected - core else selected + core)
                            },
                            label = { Text("$core", fontSize = 13.sp) },
                        )
                    }
                }
            }
        }
    }
}

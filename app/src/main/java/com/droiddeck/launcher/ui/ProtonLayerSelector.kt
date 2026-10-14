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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.ProtonSmartDefaults
import com.droiddeck.launcher.session.ProtonCatalog

/**
 * Per-game (and global-default) compatibility-layer selector with smart
 * defaults from [ProtonSmartDefaults]. When the effective layer is
 * VKD3D-Proton an explicit on-screen warning states that D3D12 is
 * stretch-only on this GPU (feature level capped at 11_1).
 */
@Composable
fun ProtonLayerSelector(
    tier: ProtonSmartDefaults.D3dTier,
    selectedLayer: ProtonSmartDefaults.TranslationLayer?,
    onSelectLayer: (ProtonSmartDefaults.TranslationLayer?) -> Unit,
    protonVersion: String?,
    onSelectProton: (String?) -> Unit,
) {
    var layerMenu by remember { mutableStateOf(false) }
    var protonMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val smart = remember(tier) { ProtonSmartDefaults.select(tier) }
    val effective = selectedLayer ?: smart.layer
    val warning = if (effective == ProtonSmartDefaults.TranslationLayer.VKD3D_PROTON) {
        ProtonSmartDefaults.select(ProtonSmartDefaults.D3dTier.D3D12).d3d12Warning
    } else {
        null
    }

    SettingsGroup(stringResource(R.string.proton_layer_title)) {
        SettingsRow(
            label = stringResource(R.string.proton_layer_title),
            hint = stringResource(R.string.proton_layer_smart_detail),
        ) {
            ValueChip(selectedLayer?.label ?: "${stringResource(R.string.proton_layer_smart)} (${smart.layer.label})", layerMenu) {
                layerMenu = !layerMenu
            }
            AnchoredMenu(layerMenu, onDismiss = { layerMenu = false }, title = stringResource(R.string.proton_layer_title)) { first ->
                MenuItem(
                    "${stringResource(R.string.proton_layer_smart)} (${smart.layer.label})",
                    checked = selectedLayer == null,
                    focusRequester = first,
                ) { onSelectLayer(null); layerMenu = false }
                ProtonSmartDefaults.TranslationLayer.values().forEach { layer ->
                    MenuItem(layer.label, checked = selectedLayer == layer) {
                        onSelectLayer(layer); layerMenu = false
                    }
                }
            }
        }
        if (warning != null) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(colors.error.copy(alpha = 0.08f))
                    .border(1.dp, colors.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(R.string.proton_layer_d3d12_warning_title),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.error,
                )
                Text(warning.message, fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        SettingsRow(label = stringResource(R.string.proton_version_title), hint = null) {
            val label = protonVersion?.let { ProtonCatalog.byId(it)?.label }
                ?: stringResource(R.string.proton_version_global)
            ValueChip(label, protonMenu) { protonMenu = !protonMenu }
            AnchoredMenu(protonMenu, onDismiss = { protonMenu = false }, title = stringResource(R.string.proton_version_title)) { first ->
                MenuItem(stringResource(R.string.proton_version_global), checked = protonVersion == null, focusRequester = first) {
                    onSelectProton(null); protonMenu = false
                }
                ProtonCatalog.entries.forEach { entry ->
                    MenuItem(entry.label, checked = protonVersion == entry.id) {
                        onSelectProton(entry.id); protonMenu = false
                    }
                }
            }
        }
    }
}

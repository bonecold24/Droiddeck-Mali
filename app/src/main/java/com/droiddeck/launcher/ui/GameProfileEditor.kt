package com.droiddeck.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.core.CpuCores
import com.droiddeck.launcher.core.FexSafeTuning
import com.droiddeck.launcher.core.PerfPresets
import com.droiddeck.launcher.core.ProtonSmartDefaults
import com.droiddeck.launcher.session.GameProfile

private val limiterChoices = listOf(null, 30, 60, 90, 120, 144, 240)

/**
 * Per-game profile editor. All logic lives in the tested core/session
 * classes; this is a thin form over [GameProfile].
 */
@Composable
fun GameProfileEditor(
    tier: ProtonSmartDefaults.D3dTier,
    profile: GameProfile,
    onChange: (GameProfile) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var presetMenu by remember { mutableStateOf(false) }
    var limiterMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroup(stringResource(R.string.game_profile_title)) {
            SettingsRow(label = stringResource(R.string.profile_preset_title), hint = null) {
                ValueChip(profile.preset.label, presetMenu) { presetMenu = !presetMenu }
                AnchoredMenu(presetMenu, onDismiss = { presetMenu = false }, title = stringResource(R.string.profile_preset_title)) { first ->
                    PerfPresets.Preset.values().forEachIndexed { i, preset ->
                        val mapping = PerfPresets.for9300Plus(preset)
                        MenuItem(
                            preset.label,
                            checked = profile.preset == preset,
                            detail = "${mapping.cpuPinning} · ${mapping.frameLimiterFps ?: 0} fps",
                            focusRequester = if (i == 0) first else null,
                        ) { onChange(profile.copy(preset = preset)); presetMenu = false }
                    }
                }
            }
            SettingsRow(label = stringResource(R.string.profile_frame_limiter), hint = null) {
                val label = profile.frameLimiterFps?.let { stringResource(R.string.profile_frame_limiter_fps, it) }
                    ?: stringResource(R.string.profile_frame_limiter_off)
                ValueChip(label, limiterMenu) { limiterMenu = !limiterMenu }
                AnchoredMenu(limiterMenu, onDismiss = { limiterMenu = false }, title = stringResource(R.string.profile_frame_limiter)) { first ->
                    limiterChoices.forEachIndexed { i, fps ->
                        val text = fps?.let { stringResource(R.string.profile_frame_limiter_fps, it) }
                            ?: stringResource(R.string.profile_frame_limiter_off)
                        MenuItem(text, checked = profile.frameLimiterFps == fps, focusRequester = if (i == 0) first else null) {
                            onChange(profile.copy(frameLimiterFps = fps)); limiterMenu = false
                        }
                    }
                }
            }
        }

        ProtonLayerSelector(
            tier = tier,
            selectedLayer = profile.dxvkVariant,
            onSelectLayer = { onChange(profile.copy(dxvkVariant = it)) },
            protonVersion = profile.protonVersion,
            onSelectProton = { onChange(profile.copy(protonVersion = it)) },
        )

        CpuPinningPicker(
            selected = CpuCores.parseAny(profile.cpuPinning),
            onSelectionChange = { onChange(profile.copy(cpuPinning = CpuCores.format(it))) },
        )

        SettingsGroup(stringResource(R.string.fex_tuning_title)) {
            for (knob in FexSafeTuning.knobs) {
                var menu by remember { mutableStateOf(false) }
                val current = profile.fexFlags[knob.key]
                SettingsRow(label = knob.label, hint = knob.detail) {
                    ValueChip(current ?: knob.default, menu) { menu = !menu }
                    AnchoredMenu(menu, onDismiss = { menu = false }, title = knob.label) { first ->
                        MenuItem("Default (${knob.default})", checked = current == null, focusRequester = first) {
                            onChange(profile.copy(fexFlags = profile.fexFlags - knob.key)); menu = false
                        }
                        knob.safeValues.forEach { value ->
                            MenuItem(value, checked = current == value) {
                                onChange(profile.copy(fexFlags = profile.fexFlags + (knob.key to value))); menu = false
                            }
                        }
                    }
                }
            }
        }

        SettingsGroup(stringResource(R.string.profile_overlay_title)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    stringResource(R.string.profile_overlay_title),
                    fontSize = 12.sp, color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                ToggleSwitch(
                    checked = profile.overlay.showFps,
                    label = stringResource(R.string.profile_overlay_fps),
                    onChange = { onChange(profile.copy(overlay = profile.overlay.copy(showFps = it, enabled = it || profile.overlay.showFrameTimes))) },
                )
                ToggleSwitch(
                    checked = profile.overlay.showFrameTimes,
                    label = stringResource(R.string.profile_overlay_frametimes),
                    onChange = { onChange(profile.copy(overlay = profile.overlay.copy(showFrameTimes = it, enabled = profile.overlay.showFps || it))) },
                )
            }
        }
    }
}

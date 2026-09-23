package com.steamdeck.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class PackageRow(
    val id: String, val name: String, val tier: Int, val version: String, val size: String,
    val notes: String, val installed: String?,
    val launchers: List<Pair<String, String>> = emptyList(),
)

private val RowShape = RoundedCornerShape(10.dp)

private fun tierName(tier: Int) = when (tier) {
    1 -> "Native ARM64"
    2 -> "ARM64 · experimental"
    else -> "Source build · experimental"
}

/**
 * Desktop & apps as a page in the front end's pane: one dropdown per tier, opened and closed like
 * the rail's own groups, and each package with its own Install/Remove and, while it installs, its
 * own bar. Leaving the page does not stop an install; coming back shows where it is.
 */
@Composable
fun DesktopAppsPage(
    rows: List<PackageRow>?,
    busyId: String?,
    busyStage: String?,
    busyPercent: Int,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
    onLaunch: (path: String) -> Unit,
    onBack: () -> Unit,
) {
    val host = rememberMenuHost()
    val colors = MaterialTheme.colorScheme
    // The first tier starts open; the rest wait for a tap.
    var open by remember { mutableStateOf(setOf(1)) }
    // An install always shows: its tier opens when it starts.
    val busyTier = rows?.firstOrNull { it.id == busyId }?.tier
    LaunchedEffect(busyTier) { if (busyTier != null) open = open + busyTier }
    SettingsPage(
        host, title = "Desktop & apps", eyebrow = "Desktop",
        lede = "Packages for the Linux desktop. Each installs into the runtime and appears in the desktop's menu.",
        onBack = onBack,
    ) {
        when {
            rows == null -> Text("Could not reach the package catalog.", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            rows.isEmpty() -> Text("Loading the catalog…", fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        for ((tier, group) in (rows ?: emptyList()).groupBy { it.tier }.toSortedMap()) {
            val expanded = tier in open
            TierHeader(tierName(tier), "${group.count { it.installed != null }}/${group.size}", expanded) {
                open = if (expanded) open - tier else open + tier
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(Motion.tw(280)) + fadeIn(Motion.tw(280)),
                exit = shrinkVertically(Motion.tw(200)) + fadeOut(Motion.tw(160)),
            ) {
                val line = LocalPalette.current.line
                Column(
                    modifier = Modifier.padding(start = 16.dp).fillMaxWidth()
                        .drawWithContent { drawContent(); drawRect(line, size = size.copy(width = 1.dp.toPx())) }
                        .padding(start = 8.dp, top = 2.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (row in group) PackageItem(
                        row, busy = busyId == row.id, anyBusy = busyId != null,
                        stage = busyStage, percent = busyPercent,
                        onInstall = onInstall, onRemove = onRemove, onLaunch = onLaunch,
                    )
                }
            }
        }
    }
}

/** A tier's dropdown row: the rail's caret, label and count pill. */
@Composable
private fun TierHeader(label: String, count: String, expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val ring by animateColorAsState(if (focused) pal.signal else Color.Transparent, Motion.tw(180), label = "tierRing")
    val rot by animateFloatAsState(if (expanded) 90f else 0f, Motion.sp(0.6f), label = "tierCaret")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            .clip(RowShape)
            .background(if (hovered) Color.White.copy(alpha = 0.04f) else Color.Transparent)
            .border(1.5.dp, ring, RowShape)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text("›", fontSize = 16.sp, color = colors.onSurfaceVariant, modifier = Modifier.width(14.dp).rotate(rot))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, modifier = Modifier.weight(1f))
        Text(
            count, fontSize = 11.sp, color = colors.onSurfaceVariant,
            modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(colors.background).padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** One package inside its tier: name, size or installed version, notes, the button, and its own bar. */
@Composable
private fun PackageItem(
    row: PackageRow, busy: Boolean, anyBusy: Boolean, stage: String?, percent: Int,
    onInstall: (String) -> Unit, onRemove: (String) -> Unit, onLaunch: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val bg by animateColorAsState(if (busy) pal.signal.copy(alpha = 0.10f) else Color.Transparent, Motion.tw(200), label = "pkgBg")
    Column(modifier = Modifier.fillMaxWidth().clip(RowShape).background(bg).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        if (row.installed != null) "  ● ${row.installed}" else "  ${row.size}",
                        fontSize = 11.sp, color = if (row.installed != null) pal.good else colors.onSurfaceVariant, maxLines = 1,
                    )
                }
                if (row.notes.isNotEmpty()) Text(row.notes, fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (row.installed != null) SecondaryButton("Remove", enabled = !anyBusy) { onRemove(row.id) }
            else SecondaryButton(if (busy) "Installing…" else "Install", enabled = !anyBusy) { onInstall(row.id) }
        }
        if (busy) {
            Text(
                if (stage != null && percent >= 0) "$stage · $percent%" else stage ?: "Starting…",
                fontSize = 11.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
            )
            if (percent >= 0) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
        }
        if (row.installed != null && row.launchers.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                for ((label, path) in row.launchers) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).border(1.dp, pal.line2, RoundedCornerShape(99.dp))
                            .clickable(enabled = !anyBusy) { onLaunch(path) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text("▶ $label", fontSize = 12.sp, color = colors.onBackground) }
                }
            }
        }
    }
}

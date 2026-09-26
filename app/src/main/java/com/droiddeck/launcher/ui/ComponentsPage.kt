package com.droiddeck.launcher.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.session.ComponentsManager
import com.droiddeck.launcher.session.ComponentsManager.CatalogItem
import com.droiddeck.launcher.session.ComponentsManager.Snapshot
import java.text.DateFormat
import java.util.Date

/** One installed row: a Proton's original bundle (one per Proton build) or a stored package. */
private class InstalledItem(
    val name: String, val detail: String, val tag: String,
    val selected: Boolean, val removable: Boolean,
    /** Stored package file, or null for an original. */
    val file: String?,
    /** The Proton build an original belongs to. */
    val protonVersion: String?,
)

private val ROW_HEIGHT: Dp = 40.dp
private val DETAIL_WIDTH: Dp = 260.dp
private val ACTION_WIDTH: Dp = 112.dp
private val GOLD = Color(0xFFF2C66D)

/**
 * FEX, DXVK and VKD3D-Proton per Proton, as a full page opened from the rail's "Components" entry.
 * Compact on purpose (layout A of the mock): title, Proton drop-down, component tabs, what is in
 * use and refresh share one toolbar line, the explanation sits behind the (i), and installed rows
 * and the Nightlies' downloads run as one list of short rows - a landscape screen shows a dozen or
 * more instead of two. Tapping an installed row swaps it in; the refresh button is the only thing
 * that goes online.
 */
@Composable
fun ComponentsPage(
    snapshot: Snapshot?,
    catalog: List<CatalogItem>,
    catalogAt: Long,
    protonId: String?,
    comp: String,
    checking: Boolean,
    busy: String?,
    downloads: Map<String, Int>,
    onProton: (String) -> Unit,
    onComp: (String) -> Unit,
    onSwap: (String) -> Unit,
    onRestore: (String) -> Unit,
    onCancelQueued: () -> Unit,
    onDeletePackage: (String) -> Unit,
    onDeleteOriginal: (String) -> Unit,
    onDownload: (CatalogItem) -> Unit,
    onRefresh: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    BackHandler(onBack = onBack)
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    var about by remember { mutableStateOf(false) }
    var protonMenu by remember { mutableStateOf(false) }
    fun ask(title: String, body: String, action: () -> Unit) { confirmTitle = title; confirm = body to action }

    val views = snapshot?.protons ?: emptyList()
    val view = views.firstOrNull { it.proton.id == protonId } ?: views.firstOrNull()
    val label = ComponentsManager.LABEL[comp] ?: comp
    val checked = if (catalogAt > 0) DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(catalogAt * 1000)) else "never"

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // ---- the toolbar: one line ------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            FocusText("‹ Back", colors.onSurfaceVariant, onClick = onBack)
            Text("Components", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1)
            if (view != null) {
                Box {
                    ValueChip(view.proton.name, protonMenu, modifier = Modifier.widthIn(max = 260.dp)) { protonMenu = !protonMenu }
                    AnchoredMenu(protonMenu, onDismiss = { protonMenu = false }, title = "Proton",
                        note = "Valve's own Proton is replaced when Steam updates it; its originals are kept per build.") { first ->
                        views.forEachIndexed { i, v ->
                            val swaps = v.components.values.count { it.activeFile != null }
                            MenuItem(
                                v.proton.name, checked = v.proton.id == view.proton.id,
                                detail = v.proton.version + (if (swaps > 0) " · $swaps swapped" else " · all original") + (if (v.inUseByGame) " · game running" else ""),
                                focusRequester = if (i == 0) first else null,
                            ) { onProton(v.proton.id); protonMenu = false }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BumperHint("LB")
                    Segmented(ComponentsManager.COMPONENTS.map { it to ComponentsManager.LABEL.getValue(it) }, comp, onComp)
                    BumperHint("RB")
                }
                val st = view.components.getValue(comp)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(8.dp).background(if (st.queued != null) Color(0xFFFFB86B) else pal.good, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    val fixedAt = if (view.reappliedAt > 0) " · re-applied at launch " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(view.reappliedAt * 1000)) else ""
                    Text(
                        (if (st.queued != null) "Next: ${st.queued} · after the game" else "In use: ${st.inUse}") + fixedAt,
                        fontSize = 12.5.sp, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (st.queued != null) { Spacer(Modifier.width(8.dp)); FocusText("Cancel", pal.signal, onClick = onCancelQueued) }
                }
            } else Spacer(Modifier.weight(1f))
            Text(if (busy != null) "$busy…" else "Nightlies · $checked", fontSize = 11.5.sp, color = colors.onSurfaceVariant, maxLines = 1)
            ToolIcon(Icons.Outlined.Info, "About Components") { about = true }
            ToolIcon(Icons.Outlined.Refresh, "Check the Nightlies for new packages", busy = checking, enabled = !checking, onClick = onRefresh)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))

        // ---- the list ---------------------------------------------------------------------------
        when {
            snapshot == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Reading the Protons…", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            view == null -> Text(
                "No Proton is installed in the Linux runtime yet. Start the Steam client once so it downloads its ARM64 Proton, or add GE-Proton / CachyOS from Setup.",
                fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp),
            )
            else -> {
                val p = view.proton
                val st = view.components.getValue(comp)
                val build = ComponentsManager.safeName(p.version)
                val originals = view.originals.filter { it.comp == comp }
                val stored = snapshot.packages.filter { it.comp == comp }
                val activeIsOriginal = st.activeFile == null
                val rows = originals.map { o ->
                    val isCurrent = o.protonVersion == build
                    InstalledItem(
                        "Original · ${o.protonVersion}",
                        o.label.substringAfterLast(" · ", "").ifEmpty { o.label } + if (isCurrent) " · this build" else " · earlier build",
                        "ORIGINAL", activeIsOriginal && isCurrent, !isCurrent, null, o.protonVersion,
                    )
                } + stored.map { s ->
                    InstalledItem(s.version, String.format("%.1f MB", s.size / 1048576.0), "STORED", s.file == st.activeFile, true, s.file, null)
                }
                val storedNames = snapshot.packages.map { it.file }.toSet()
                val available = catalog.filter { it.comp == comp && ComponentsManager.safeName(it.file) !in storedNames }
                val first = remember { FocusRequester() }
                LaunchedEffect(p.id, comp) { runCatching { first.requestFocus() } }

                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    item { SectionLabel("Installed · ${rows.size}") }
                    if (rows.isEmpty()) item {
                        Text("Nothing stored for $label yet. This Proton's own files are saved as its original when the page opens.",
                            fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    items(rows.size) { i ->
                        val item = rows[i]
                        InstalledLine(
                            item, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onSelect = {
                                if (item.selected) return@InstalledLine
                                val waits = if (view.inUseByGame) " A game is running on this Proton, so it waits until the game closes." else " It applies the next time a game starts."
                                if (item.file != null) ask("Swap $label?", "Put ${item.name} into ${p.name}? Its shipped files stay saved as an original bundle.$waits") { onSwap(item.file) }
                                else ask("Restore $label?", "Put the ${item.protonVersion} original back into ${p.name}?$waits") { onRestore(item.protonVersion!!) }
                            },
                            onDelete = {
                                if (item.file != null) ask("Delete ${item.name}?", "Its package file is removed from the app. You can download it again any time.") { onDeletePackage(item.file) }
                                else ask("Delete this original?", "The ${item.protonVersion} original of $label belongs to an earlier build of ${p.name}. The installed build's original is always kept.") { onDeleteOriginal(item.protonVersion!!) }
                            },
                        )
                    }
                    item { SectionLabel("Available on the Nightlies · ${available.size}") }
                    if (available.isEmpty()) item {
                        Text(if (catalogAt == 0L) "Tap refresh to list the packages on the Nightlies." else "Nothing new from the last check.",
                            fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    items(available.size) { i ->
                        val d = available[i]
                        AvailableLine(d, downloads[d.file], enabled = busy == null) { onDownload(d) }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                            SmallButton("Import .wcp", onClick = onImport)
                            if (!activeIsOriginal && originals.any { it.protonVersion == build }) FocusText("Restore this Proton's original", pal.signal) {
                                ask("Restore $label?", "Put ${p.name}'s original $label back?") { onRestore(build) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("Components") },
        text = {
            Text(
                "FEX, DXVK and VKD3D-Proton for each Proton the Steam client runs games with.\n\n" +
                    "Tap an installed row to swap it in; it applies the next time a game starts. If a game is running on that Proton, the swap waits until it closes.\n\n" +
                    "Your choices are also checked right before every game launch: if anything changed a Proton's files (Steam's start-up tests, for one), they are put back first, and the page says \"re-applied at launch\".\n\n" +
                    "Each Proton build's own files are kept as its ORIGINAL, so a Steam update never loses them. Packages come from the Nightlies \"-Linux\" releases (refresh) or Import .wcp.",
                fontSize = 13.sp,
            )
        },
        confirmButton = { FocusText("OK", pal.signal) { about = false } },
    )
    confirm?.let { (body, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(confirmTitle) },
            text = { Text(body, fontSize = 13.sp) },
            // Opens on Cancel, so a stray A press on a controller never swaps or deletes anything.
            confirmButton = {
                val del = confirmTitle.startsWith("Delete")
                FocusText(if (del) "Delete" else if (confirmTitle.startsWith("Restore")) "Restore" else "Swap", if (del) colors.error else pal.signal) { confirm = null; action() }
            },
            dismissButton = {
                val cancelFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
                FocusText("Cancel", colors.onBackground, modifier = Modifier.focusRequester(cancelFocus)) { confirm = null }
            },
        )
    }
}

/** The pad's bumper that turns the component tabs, as a small badge beside them. */
@Composable
private fun BumperHint(text: String) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = colors.onSurfaceVariant, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, pal.line2, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 10.dp, bottom = 4.dp)) {
        Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = colors.onSurfaceVariant)
        Box(Modifier.weight(1f).height(1.dp).background(pal.line))
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Row(Modifier.clip(RoundedCornerShape(9.dp)).background(colors.surfaceVariant).border(1.dp, pal.line2, RoundedCornerShape(9.dp)).padding(2.dp)) {
        for ((key, text) in options) {
            val on = key == selected
            val src = remember { MutableInteractionSource() }
            val hot = src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value
            val pick = { onPick(key) }
            Text(
                text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (on) pal.onSignal else if (hot) colors.onBackground else colors.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (on) pal.signal else if (hot) pal.signal.copy(alpha = 0.18f) else Color.Transparent)
                    .border(if (hot && !on) 1.dp else 0.dp, if (hot && !on) pal.signal else Color.Transparent, RoundedCornerShape(7.dp))
                    .hoverable(src).clickable(interactionSource = src, indication = null, onClick = pick)
                    .controllerConfirm(onClick = pick)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, description: String, busy: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
            .background(if (hot) pal.signal.copy(alpha = 0.16f) else Color.Transparent)
            .border(if (hot) 2.dp else 1.dp, if (hot) pal.signal else pal.line2, RoundedCornerShape(9.dp))
            .hoverable(src).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .controllerConfirm(enabled = enabled, onClick = onClick),
    ) {
        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        else Icon(icon, contentDescription = description, tint = colors.onBackground, modifier = Modifier.size(18.dp))
    }
}

/**
 * A small button. Resting: grey outline (blue text when [accent]). Focused or hovered: a solid blue
 * fill, white text and a white outline - unmistakable in a column of identical buttons.
 */
@Composable
private fun SmallButton(text: String, enabled: Boolean = true, accent: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = (src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value) && enabled
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(if (hot) pal.signal else colors.surfaceVariant)
            .border(2.dp, if (hot) Color.White else pal.line2, RoundedCornerShape(8.dp))
            .hoverable(src).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .controllerConfirm(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (!enabled) colors.onSurfaceVariant else if (hot) pal.onSignal else if (accent) pal.signal else colors.onBackground,
        )
    }
}

@Composable
private fun Tag(text: String) {
    val colors = MaterialTheme.colorScheme
    val (fg, bg) = when (text) {
        "ORIGINAL" -> GOLD to Color(0x22F2C66D)
        "IN USE" -> LocalPalette.current.onSignal to LocalPalette.current.signal
        "NEW" -> LocalPalette.current.good to Color(0x224CD37F)
        else -> colors.onSurfaceVariant to Color.White.copy(alpha = 0.07f)
    }
    Text(
        text, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(bg).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
private fun InstalledLine(item: InstalledItem, modifier: Modifier = Modifier, onSelect: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).clip(shape)
            .background(if (hot) pal.signal.copy(alpha = 0.14f) else if (item.selected) pal.signal.copy(alpha = 0.08f) else Color.Transparent)
            .border(2.dp, if (hot) pal.signal else Color.Transparent, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f).heightIn(min = ROW_HEIGHT).then(modifier)
                .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onSelect)
                .controllerConfirm(onClick = onSelect)
                .padding(horizontal = 10.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp).border(2.dp, if (item.selected) pal.signal else colors.onSurfaceVariant, CircleShape)) {
                if (item.selected) Box(Modifier.size(7.dp).background(pal.signal, CircleShape))
            }
            Text(
                item.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (item.selected) pal.signal else colors.onBackground, modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(72.dp)) { Tag(item.tag) }
            Text(item.detail, fontSize = 11.5.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(DETAIL_WIDTH))
            Box(Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) { if (item.selected) Tag("IN USE") }
        }
        // The trash column is always there (empty when the row can't be deleted) so rows line up.
        if (!item.removable) Spacer(Modifier.padding(end = 4.dp).size(32.dp))
        else {
            val delSrc = remember { MutableInteractionSource() }
            val delHot = delSrc.collectIsFocusedAsState().value || delSrc.collectIsHoveredAsState().value
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(end = 4.dp).size(32.dp).clip(RoundedCornerShape(8.dp))
                    .border(2.dp, if (delHot) colors.error else Color.Transparent, RoundedCornerShape(8.dp))
                    .hoverable(delSrc).clickable(interactionSource = delSrc, indication = null, onClick = onDelete)
                    .controllerConfirm(onClick = onDelete),
            ) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = if (delHot) colors.error else colors.onSurfaceVariant, modifier = Modifier.size(17.dp)) }
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(pal.line.copy(alpha = 0.5f)))
}

@Composable
private fun AvailableLine(d: CatalogItem, progress: Int?, enabled: Boolean, onDownload: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = ROW_HEIGHT).padding(horizontal = 10.dp),
    ) {
        // Fixed columns so every row's details and button line up: name | details | button.
        Tag("NEW")
        Text(d.file.removeSuffix(".wcp"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${d.release} · " + String.format("%.1f MB", d.size / 1048576.0), fontSize = 11.5.sp, color = colors.onSurfaceVariant,
            fontFamily = FontFamily.Default, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(DETAIL_WIDTH))
        if (progress == null) SmallButton("Download", enabled = enabled, accent = true, modifier = Modifier.width(ACTION_WIDTH), onClick = onDownload)
        else Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(ACTION_WIDTH)) {
            Text(if (progress < 0) "…" else "$progress%", fontSize = 11.sp, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            if (progress < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(pal.line.copy(alpha = 0.5f)))
}

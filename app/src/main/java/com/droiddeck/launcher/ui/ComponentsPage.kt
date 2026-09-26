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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.session.ComponentsManager
import com.droiddeck.launcher.session.ComponentsManager.CatalogItem
import com.droiddeck.launcher.session.ComponentsManager.Snapshot
import java.text.DateFormat
import java.util.Date

/** One installed row: a Proton's original bundle (one per Proton build) or a stored package. */
private class InstalledItem(
    val key: String, val name: String, val detail: String, val tag: String,
    val selected: Boolean, val removable: Boolean,
    /** Stored package file, or null for an original. */
    val file: String?,
    /** The Proton build an original belongs to. */
    val protonVersion: String?,
)

/**
 * FEX, DXVK and VKD3D-Proton per Proton, as a full page opened from the rail's "Components" entry.
 * Built like the driver pages: pick a Proton (drop-down) and a component (tabs); the installed
 * rows are the Proton's original bundles and the stored packages, the ● is what that Proton uses,
 * tapping another row swaps it in; downloads come from the Nightlies "-Linux" releases, and the
 * refresh button beside the title is the only thing that goes online.
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
    val host = rememberMenuHost()
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    BackHandler(onBack = onBack)
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var confirmTitle by remember { mutableStateOf("") }
    fun ask(title: String, body: String, action: () -> Unit) { confirmTitle = title; confirm = body to action }

    val views = snapshot?.protons ?: emptyList()
    val view = views.firstOrNull { it.proton.id == protonId } ?: views.firstOrNull()
    val label = ComponentsManager.LABEL[comp] ?: comp
    val checked = if (catalogAt > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(catalogAt * 1000)) else "never"
    val status = buildString {
        append("Nightlies last checked $checked")
        if (busy != null) append(" · $busy…")
    }

    val refreshSrc = remember { MutableInteractionSource() }
    val refreshHot = refreshSrc.collectIsFocusedAsState().value || refreshSrc.collectIsHoveredAsState().value
    SettingsPage(
        host, title = "Components", onBack = onBack,
        lede = "FEX, DXVK and VKD3D-Proton for each Proton the Steam client runs games with. Tap a row to swap it in; it applies the next time a game starts.\n$status",
        action = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (refreshHot) pal.signal.copy(alpha = 0.16f) else Color.Transparent)
                    .border(if (refreshHot) 2.dp else 1.dp, if (refreshHot) pal.signal else pal.line, RoundedCornerShape(10.dp))
                    .hoverable(refreshSrc)
                    .clickable(interactionSource = refreshSrc, indication = null, enabled = !checking, onClick = onRefresh),
            ) {
                if (checking) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Icon(Icons.Outlined.Refresh, contentDescription = "Check the Nightlies for new packages", tint = colors.onBackground, modifier = Modifier.size(20.dp))
            }
        },
    ) {
        if (snapshot == null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Reading the Protons…", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
            return@SettingsPage
        }
        if (view == null) {
            SettingsGroup("Proton") {
                Text(
                    "No Proton is installed in the Linux runtime yet. Start the Steam client once so it downloads its ARM64 Proton, or add GE-Proton / CachyOS from Setup.",
                    fontSize = 12.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(14.dp),
                )
            }
            return@SettingsPage
        }
        val p = view.proton
        SettingsGroup("Proton") {
            ChoiceRow(
                host, "components-proton", p.name,
                p.version + (if (view.inUseByGame) " · a game is running on it" else "") + (if (p.valve) " · updated by Steam" else ""),
                views.map { v ->
                    val swaps = v.components.values.count { it.activeFile != null }
                    v.proton.id to (v.proton.name + if (swaps > 0) "  ·  $swaps swapped" else "")
                },
                p.id,
                note = "Valve's own Proton is replaced when Steam updates it; its originals are kept per build.",
                onPick = onProton,
            )
        }

        // Component tabs.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            for (c in ComponentsManager.COMPONENTS) CompTab(ComponentsManager.LABEL.getValue(c), c == comp) { onComp(c) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))

        val state = view.components.getValue(comp)
        SettingsGroup("In use") {
            SettingsRow(
                state.inUse,
                "Detected: ${state.detected}" + (if (state.detail.isNotEmpty()) " · ${state.detail}" else "") +
                    (state.queued?.let { "\nNext: $it, when the game running on this Proton closes" } ?: ""),
            ) {
                if (state.queued != null) FocusText("Cancel", pal.signal, onClick = onCancelQueued)
                else Box(Modifier.size(10.dp).background(pal.good, CircleShape))
            }
        }

        val originals = view.originals.filter { it.comp == comp }
        val currentBuild = ComponentsManager.safeName(p.version)
        val stored = snapshot.packages.filter { it.comp == comp }
        val activeIsOriginal = state.activeFile == null
        val items = originals.map { o ->
            val isCurrent = o.protonVersion == currentBuild
            InstalledItem(
                "orig:${o.protonVersion}", "Original · ${o.protonVersion}",
                o.label.substringAfterLast(" · ", "").ifEmpty { o.label } + if (isCurrent) " · this build" else " · an earlier build",
                "ORIGINAL", selected = activeIsOriginal && isCurrent, removable = !isCurrent, file = null, protonVersion = o.protonVersion,
            )
        } + stored.map { s ->
            InstalledItem(
                "pkg:${s.file}", s.version, s.file + " · " + String.format("%.1f MB", s.size / 1048576.0),
                "STORED", selected = s.file == state.activeFile, removable = true, file = s.file, protonVersion = null,
            )
        }
        SettingsGroup("Installed") {
            if (items.isEmpty()) Text(
                "Nothing stored for $label yet. The Proton's own files are saved as its original the first time this page opens.",
                fontSize = 12.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            )
            val first = remember { FocusRequester() }
            LaunchedEffect(p.id, comp) { runCatching { first.requestFocus() } }
            items.forEachIndexed { i, item ->
                ComponentRow(
                    item, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onSelect = {
                        if (item.selected) return@ComponentRow
                        if (item.file != null) ask("Swap $label?", "Put ${item.name} into ${p.name}? Its shipped files stay saved as an original bundle." +
                            if (view.inUseByGame) " A game is running on this Proton, so the swap waits until it closes." else " It applies the next time a game starts.") { onSwap(item.file) }
                        else ask("Restore $label?", "Put the ${item.protonVersion} original back into ${p.name}?" +
                            if (view.inUseByGame) " A game is running on this Proton, so this waits until it closes." else "") { onRestore(item.protonVersion!!) }
                    },
                    onDelete = {
                        if (item.file != null) ask("Delete ${item.name}?", "Its package file is removed from the app. You can download it again any time.") { onDeletePackage(item.file) }
                        else ask("Delete this original?", "The ${item.protonVersion} original of $label belongs to an earlier build of ${p.name}. The installed build's original is always kept.") { onDeleteOriginal(item.protonVersion!!) }
                    },
                )
            }
        }

        val storedNames = snapshot.packages.map { it.file }.toSet()
        val available = catalog.filter { it.comp == comp && ComponentsManager.safeName(it.file) !in storedNames }
        SettingsGroup("Available to download") {
            if (available.isEmpty()) Text(
                if (catalogAt == 0L) "Tap the refresh button to list the packages on the Nightlies." else "Nothing new from the last check. Tap the refresh button to look for newer releases.",
                fontSize = 12.5.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            ) else for (d in available) {
                SettingsRow(d.file.removeSuffix(".wcp"), "${d.release} · " + String.format("%.1f MB", d.size / 1048576.0)) {
                    val pr = downloads[d.file]
                    if (pr == null) SecondaryButton("Download", enabled = busy == null) { onDownload(d) }
                    else Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                        Text(if (pr < 0) "…" else "$pr%", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        if (pr < 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { pr / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            SecondaryButton("Import .wcp", onClick = onImport)
            val currentOriginal = originals.any { it.protonVersion == currentBuild }
            if (!activeIsOriginal && currentOriginal) FocusText("Restore this Proton's original", pal.signal) {
                ask("Restore $label?", "Put ${p.name}'s original $label back?") { onRestore(currentBuild) }
            }
        }
    }

    confirm?.let { (body, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(confirmTitle) },
            text = { Text(body, fontSize = 13.sp) },
            // Opens on Cancel, so a stray A press on a controller never swaps or deletes anything.
            confirmButton = { FocusText(if (confirmTitle.startsWith("Delete")) "Delete" else if (confirmTitle.startsWith("Restore")) "Restore" else "Swap",
                if (confirmTitle.startsWith("Delete")) colors.error else pal.signal) { confirm = null; action() } },
            dismissButton = {
                val cancelFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
                FocusText("Cancel", colors.onBackground, modifier = Modifier.focusRequester(cancelFocus)) { confirm = null }
            },
        )
    }
}

@Composable
private fun CompTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(if (hot) pal.signal.copy(alpha = 0.14f) else Color.Transparent)
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick)
            .controllerConfirm(onClick = onClick),
    ) {
        Text(
            text, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            color = if (selected) colors.onBackground else colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        Box(Modifier.width(48.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) pal.signal else Color.Transparent))
    }
}

@Composable
private fun ComponentRow(item: InstalledItem, modifier: Modifier = Modifier, onSelect: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(3.dp).clip(shape)
            .background(if (hot) pal.signal.copy(alpha = 0.14f) else if (item.selected) pal.signal.copy(alpha = 0.08f) else Color.Transparent)
            .border(2.dp, if (hot) pal.signal else Color.Transparent, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).then(modifier)
                .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onSelect)
                .controllerConfirm(onClick = onSelect)
                .padding(horizontal = 11.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp).border(2.dp, if (item.selected) pal.signal else colors.onSurfaceVariant, CircleShape),
            ) { if (item.selected) Box(Modifier.size(10.dp).background(pal.signal, CircleShape)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (item.selected) pal.signal else colors.onBackground, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        item.tag, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        color = if (item.tag == "ORIGINAL") Color(0xFFF2C66D) else colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (item.tag == "ORIGINAL") Color(0x22F2C66D) else Color.White.copy(alpha = 0.07f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Text(item.detail, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (item.removable) {
            val delSrc = remember { MutableInteractionSource() }
            val delHot = delSrc.collectIsFocusedAsState().value || delSrc.collectIsHoveredAsState().value
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(end = 6.dp).size(38.dp).clip(RoundedCornerShape(9.dp))
                    .border(2.dp, if (delHot) colors.error else Color.Transparent, RoundedCornerShape(9.dp))
                    .hoverable(delSrc).clickable(interactionSource = delSrc, indication = null, onClick = onDelete)
                    .controllerConfirm(onClick = onDelete),
            ) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = if (delHot) colors.error else colors.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        }
    }
}

package com.droiddeck.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import com.droiddeck.launcher.session.GameSaves

/**
 * Game saves, opened from the floppy disk beside the Steam page's cog. A page like Components:
 * the Proton at the top, the save zip type beside it (LB / RB turn it), and the games set to that
 * Proton, each with Import (pick a zip in the app's file picker) and Export (pick a folder there).
 * A zip of either type imports; the type only decides how an export is written.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun GameSavesPage(
    tools: List<GameSaves.Tool>?,
    games: List<GameSaves.Game>,
    toolName: String?,
    layout: GameSaves.Layout,
    busy: String?,
    sessionRunning: Boolean,
    onTool: (String) -> Unit,
    onLayout: (GameSaves.Layout) -> Unit,
    onImport: (GameSaves.Game) -> Unit,
    onExport: (GameSaves.Game) -> Unit,
    onBack: () -> Unit,
    requestInitialFocus: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val narrow = LocalNarrowPane.current
    BackHandler(onBack = onBack)
    var toolMenu by remember { mutableStateOf(false) }

    val list = tools ?: emptyList()
    val tool = list.firstOrNull { it.name == toolName } ?: list.firstOrNull()
    val shown = games.filter { it.tool == tool?.name }
        .sortedWith(compareByDescending<GameSaves.Game> { it.launched }.thenBy { it.game.name.lowercase() })
    val layouts = GameSaves.Layout.entries

    Column(Modifier.fillMaxSize().padding(horizontal = if (narrow) 16.dp else 22.dp, vertical = if (narrow) 12.dp else 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Game saves", fontSize = if (narrow) 22.sp else 26.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                maxLines = 1, modifier = Modifier.weight(1f),
            )
            if (tools != null) Text(
                if (shown.size == 1) "1 game on this Proton" else "${shown.size} games on this Proton",
                fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 1,
            )
        }

        // ---- which Proton, which zip type -----------------------------------------------------------
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (tool != null) Box {
                ValueChip(tool.label, toolMenu, modifier = Modifier.widthIn(max = 300.dp).heightIn(min = 44.dp)) { toolMenu = !toolMenu }
                AnchoredMenu(toolMenu, onDismiss = { toolMenu = false }, title = "Proton",
                    note = "Saves belong to the game, not the Proton: switch a game to another Proton and its saves go with it.") { first ->
                    list.forEachIndexed { i, t ->
                        val n = games.count { it.tool == t.name }
                        MenuItem(
                            t.label, checked = t.name == tool.name,
                            detail = t.name + " · " + if (n == 1) "1 game" else if (n == 0) "no games yet" else "$n games",
                            focusRequester = if (i == 0) first else null,
                        ) { onTool(t.name); toolMenu = false }
                    }
                }
            }
            TabStrip(layouts.map { it.label }, layouts.indexOf(layout), { onLayout(layouts[it]) })
        }

        // ---- what the type means, or what is happening -----------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp)).background(colors.surface)
                .border(1.dp, pal.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp).heightIn(min = 28.dp),
        ) {
            if (busy != null) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            else Box(Modifier.size(8.dp).background(if (sessionRunning) Color(0xFFFFB86B) else pal.good, CircleShape))
            Text(
                when {
                    busy != null -> "$busy…"
                    sessionRunning -> "Steam is running: close the session before importing, or the game may save over it."
                    layout == GameSaves.Layout.GAMEHUB -> "Exports keep saves under steamuser, as GameHub does. Imports take either type and back up the saves already there first."
                    else -> "Exports keep saves under xuser, as Winlator does. Imports take either type and back up the saves already there first."
                },
                fontSize = 14.sp, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }

        // ---- the games ------------------------------------------------------------------------------
        when {
            tools == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Looking for games and their saves…", fontSize = 14.sp, color = colors.onSurfaceVariant)
            }
            tool == null -> Text(
                "No Proton is installed in the Linux runtime yet. Start the Steam client once so it sets one up.",
                fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 16.dp)) {
                val first = remember { FocusRequester() }
                LaunchedEffect(tool.name, requestInitialFocus) { if (requestInitialFocus) runCatching { first.requestFocus() } }
                SettingsGroup("Games on this Proton · ${shown.size}") {
                    if (shown.isEmpty()) Text(
                        "No game is set to ${tool.label}. Pick it for a game in Steam (Properties › Compatibility) and launch the game once.",
                        fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    var firstSet = false
                    shown.forEach { g ->
                        val canImport = g.launched && busy == null && !sessionRunning
                        val canExport = g.launched && busy == null
                        val mod = if (!firstSet && canExport) { firstSet = true; Modifier.focusRequester(first) } else Modifier
                        GameSaveLine(g, canImport, canExport, mod, { onImport(g) }, { onExport(g) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GameSaveLine(
    g: GameSaves.Game, canImport: Boolean, canExport: Boolean, firstButton: Modifier,
    onImport: () -> Unit, onExport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val narrow = LocalNarrowPane.current
    val detail = when {
        !g.launched -> "Launch it once first: its save folder is made on the first launch"
        g.saves.isEmpty() -> (if (g.game.library == "added") "Added game" else "Steam game") + " · no save folder found, Export takes the whole user folder"
        else -> {
            val files = g.saves.sumOf { it.files }
            val mb = g.saves.sumOf { it.bytes } / 1048576.0
            (if (g.game.library == "added") "Added game" else "Steam game") + " · " +
                (if (g.saves.size == 1) g.saves[0].relPath else "${g.saves.size} save folders") +
                " · $files files · " + (if (mb < 1) "${(mb * 1024).toInt()} KB" else String.format("%.1f MB", mb))
        }
    }
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(g.game.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = 12.sp, color = if (g.launched) colors.onSurfaceVariant else Color(0xFFFFB86B), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    val buttons: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Import", enabled = canImport, modifier = if (canImport) firstButton else Modifier, onClick = onImport)
            SmallButton("Export", enabled = canExport, modifier = if (!canImport) firstButton else Modifier, onClick = onExport)
        }
    }
    if (narrow) Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        text(Modifier.fillMaxWidth()); buttons()
    } else Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
    ) { text(Modifier.weight(1f)); buttons() }
}

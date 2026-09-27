package com.droiddeck.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.view.Display
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.droiddeck.launcher.R
import com.droiddeck.launcher.HomeApp
import com.droiddeck.launcher.frontend.Library
import com.droiddeck.launcher.gpu.FrameGen
import com.droiddeck.launcher.input.SecondScreenDisplay
import com.droiddeck.launcher.core.DeviceSupport
import com.droiddeck.launcher.core.PhantomProcessLimit
import com.droiddeck.launcher.core.PhantomProcessStatus
import com.droiddeck.launcher.session.SessionPrefs
import java.io.File
import kotlin.math.roundToInt

class FrontEndState(
    val installed: String?,
    val ready: Boolean,
    val available: String?,
    val busy: Boolean,
    val stage: String,
    val percent: Int,
    val desktopInstalled: Boolean,
    val offlineAccount: String?,
    val offline: Boolean,
    val frameGenLabel: String,
    val romsDir: String?,
    val logsEnabled: Boolean,
    val steamGames: List<Library.SteamGame>,
    val emulators: List<Library.Emulator>,
    val running: String?,
    val frameGenEngine: String = FrameGen.ENGINE_OFF,
    val frameGenMultiplier: Int = 2,
    val lsfgReady: Boolean = false,
    val pageKey: String? = null,
    val theme: String = Themes.GRAPHITE,
    val isHomeApp: Boolean = false,
    val homeScreenEnabled: Boolean = false,
    val defaultHomeLabel: String? = null,
    val androidApps: List<HomeApp.LaunchableApp> = emptyList(),
    val secondScreenDisplays: List<SecondScreenDisplay> = emptyList(),
    val packages: List<PackageRow>? = null,
    val packageCatalogLoading: Boolean = false,
    val packageBusyId: String? = null,
    val packageStage: String? = null,
    val packagePercent: Int = -1,
    val sessionRunning: Boolean = false,
    val backActionsInverted: Boolean = false,
    val buildLabel: String = "local",
    val oscMode: String = SessionPrefs.OSC_AUTO,
    val controller: com.droiddeck.launcher.input.ControllerPrefs.Settings? = null,
    val phantomProcessStatus: PhantomProcessStatus = PhantomProcessStatus.NOT_APPLICABLE,
    val showPhantomGate: Boolean = false,
    val launcherFullscreen: Boolean = true,
)

class FrontEndActions(
    val onPlay: () -> Unit,
    val onPlayDesktopUi: () -> Unit,
    val onSteamGame: (Library.SteamGame) -> Unit,
    val onDesktop: () -> Unit,
    val onEmulator: (Library.Emulator) -> Unit,
    val onRom: (Library.Rom) -> Unit,
    val onResume: () -> Unit,
    val onSteamSettings: () -> Unit,
    val onDesktopSettings: () -> Unit,
    val onInstallPackage: (String) -> Unit,
    val onRemovePackage: (String) -> Unit,
    val onRuntime: () -> Unit,
    val onFrameGenPick: (engine: String, multiplier: Int) -> Unit,
    val onProtons: () -> Unit,
    /** The Components page: FEX / DXVK / VKD3D-Proton per Proton. */
    val onComponents: () -> Unit,
    val onPerformance: () -> Unit,
    val onRoms: () -> Unit,
    val onFiles: () -> Unit,
    val onLogs: () -> Unit,
    val onShareLogs: () -> Unit = {},
    val onOffline: () -> Unit,
    val onCredits: () -> Unit,
    val onPageBack: () -> Unit = {},
    val onTheme: (String) -> Unit = {},
    val onLauncherFullscreen: (Boolean) -> Unit = {},
    val onHomeApp: () -> Unit = {},
    val onHomeScreen: (Boolean) -> Unit = {},
    val onAndroidApp: (HomeApp.LaunchableApp, Int?) -> Unit = { _, _ -> },
    val onBackActionsInverted: (Boolean) -> Unit = {},
    val onCheckLatestBuild: () -> Unit = {},
    val onRefreshPhantomStatus: () -> Unit = {},
    val onOpenDeveloperOptions: (Int?) -> Unit = {},
    val onWirelessAdbPair: (String, Int, String, (String?) -> Unit) -> Unit = { _, _, _, done -> done("Wireless ADB is unavailable") },
    val onFindWirelessAdbPort: (String, (Int?) -> Unit) -> Unit = { _, done -> done(null) },
    val onWirelessAdbApply: (String, Int, Boolean, (String?) -> Unit) -> Unit = { _, _, _, done -> done("Wireless ADB is unavailable") },
    val onSetPhantomProcessLimit: (Boolean, (String?) -> Unit) -> Unit = { _, done -> done("Wireless ADB is unavailable") },
    val onCopyPhantomCommand: (Boolean) -> Unit = {},
    val onDismissPhantomGate: () -> Unit = {},
    val controller: ControllerActions? = null,
)


internal object Motion {
    var scale = 1f
    val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    fun ms(base: Int) = (base * scale).roundToInt()
    fun <T> tw(base: Int, delay: Int = 0): FiniteAnimationSpec<T> = if (scale == 0f) snap() else tween(ms(base), ms(delay), Ease)
    fun <T> sp(damping: Float = 0.7f, stiffness: Float = Spring.StiffnessMediumLow): FiniteAnimationSpec<T> =
        if (scale == 0f) snap() else spring(damping, stiffness)
}

private val Shape10 = RoundedCornerShape(10.dp)
private val Shape12 = RoundedCornerShape(12.dp)

internal fun hueOf(name: String) = (name.hashCode().toUInt() % 360u).toFloat()
private fun tint(h: Float, s: Float = 0.7f, v: Float = 0.58f) = Color.hsv(h, s, v)
internal fun artBrush(h: Float) = Brush.linearGradient(listOf(tint(h), tint((h + 32f) % 360f, 0.65f, 0.30f), tint((h + 64f) % 360f, 0.6f, 0.14f)))

@Composable
internal fun Rise(i: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = state, modifier = modifier,
        enter = fadeIn(Motion.tw(450, i * 60)) + slideInVertically(Motion.tw(450, i * 60)) { it / 3 },
        exit = fadeOut(Motion.tw(120)),
        label = "rise",
    ) { content() }
}

@Composable
private fun Modifier.staggerIn(i: Int): Modifier {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, Motion.tw(360, i * 30)) }
    return graphicsLayer { alpha = t.value; translationY = (1f - t.value) * 10.dp.toPx() }
}

@Composable
private fun Modifier.shine(trigger: Boolean, strength: Float = 0.22f): Modifier {
    val x = remember { Animatable(-1f) }
    LaunchedEffect(trigger) { if (trigger) { x.snapTo(-1f); x.animateTo(1f, Motion.tw(800)) } }
    return drawWithContent {
        drawContent()
        val p = x.value
        if (p > -1f && p < 1f) {
            val w = size.width
            val c = w * 0.5f + p * w * 0.9f
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
                    start = Offset(c - w * 0.35f, 0f), end = Offset(c + w * 0.35f, size.height),
                ),
            )
        }
    }
}


/**
 * Controller focus on the front end: each rail item's requester and the page's main button (Play,
 * Open desktop, Open <emulator>, Launch...). The pane leaves, to the left, for the selected rail
 * item - whatever tile or button it leaves from - and remembers that control, so coming back in
 * lands on it again; a page not yet visited enters on its main button.
 */
private class FrontFocus {
    val rail = HashMap<String, FocusRequester>()
    val primary = FocusRequester()
    var primaryAttached by mutableStateOf(0)
    var focusedRail by mutableStateOf<String?>(null)
    fun railFor(key: String): FocusRequester = rail.getOrPut(key) { FocusRequester() }
    // The pane's controls by id (a tile's key, a button's label), how many of each are on screen,
    // and the last one focused.
    val items = HashMap<String, FocusRequester>()
    val attached = HashMap<String, Int>()
    var last: String? = null
    // The first tile of the page's grid: Down from the page's buttons goes to it, not to whichever
    // tile happens to sit under the button.
    val firstTile = FocusRequester()
    var firstTileAttached = 0
    fun paneEntry(): FocusRequester {
        val id = last
        return when {
            id == PRIMARY && primaryAttached > 0 -> primary
            id != null && id != PRIMARY && (attached[id] ?: 0) > 0 -> items.getValue(id)
            primaryAttached > 0 -> primary
            else -> FocusRequester.Default
        }
    }
    companion object { const val PRIMARY = "\u0000primary" }
}

/** Down from this button goes to the first tile of the page's grid, when there is one. */
@Composable
private fun Modifier.downToFirstTile(): Modifier {
    val ff = LocalFrontFocus.current ?: return this
    return this.focusProperties { down = if (ff.firstTileAttached > 0) ff.firstTile else FocusRequester.Default }
}

/** Marks the first tile of the page's grid. */
@Composable
private fun Modifier.firstTile(): Modifier {
    val ff = LocalFrontFocus.current ?: return this
    DisposableEffect(Unit) {
        ff.firstTileAttached++
        onDispose { ff.firstTileAttached-- }
    }
    return this.focusRequester(ff.firstTile)
}

/** Lets the pane come back to this control: it is remembered when focused. */
@Composable
private fun Modifier.paneItem(id: String): Modifier {
    val ff = LocalFrontFocus.current ?: return this
    val req = remember(id) { ff.items.getOrPut(id) { FocusRequester() } }
    DisposableEffect(id) {
        ff.attached[id] = (ff.attached[id] ?: 0) + 1
        onDispose { ff.attached[id] = (ff.attached[id] ?: 1) - 1 }
    }
    return this.focusRequester(req).onFocusChanged { if (it.isFocused) ff.last = id }
}

private val LocalFrontFocus = staticCompositionLocalOf<FrontFocus?> { null }

@Composable
fun FrontEndScreen(s: FrontEndState, a: FrontEndActions, page: (@Composable () -> Unit)? = null) {
    val frontFocus = remember { FrontFocus() }
    CompositionLocalProvider(LocalFrontFocus provides frontFocus) { FrontEndScreenBody(s, a, page, frontFocus) }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FrontEndScreenBody(s: FrontEndState, a: FrontEndActions, page: (@Composable () -> Unit)?, frontFocus: FrontFocus) {
    var selected by rememberSaveable { mutableStateOf("steam") }
    var showWirelessAdbFix by rememberSaveable { mutableStateOf(false) }
    var showDeveloperDisplayChoice by rememberSaveable { mutableStateOf(false) }
    var wirelessAdbDesiredEnabled by rememberSaveable { mutableStateOf(false) }
    var appToChooseDisplay by remember { mutableStateOf<HomeApp.LaunchableApp?>(null) }
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val phantomGateVisible = s.showPhantomGate && PhantomProcessLimit.blocksSteam(s.phantomProcessStatus)
    val processSettingsPageVisible = phantomGateVisible || showWirelessAdbFix || showDeveloperDisplayChoice
    val requestDeveloperOptions = {
        if (s.secondScreenDisplays.isEmpty()) a.onOpenDeveloperOptions(null)
        else showDeveloperDisplayChoice = true
    }
    val requestWirelessAdbFix: (Boolean) -> Unit = { enabled ->
        wirelessAdbDesiredEnabled = enabled
        showWirelessAdbFix = true
    }
    LaunchedEffect(s.showPhantomGate, s.phantomProcessStatus) {
        if (phantomGateVisible) {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                a.onRefreshPhantomStatus()
            }
        }
    }
    BackHandler(enabled = !processSettingsPageVisible && s.pageKey != null && page != null) { a.onPageBack() }
    // Back (and B) from a ROM or an emulator steps out one level, as its "‹" link does, instead
    // of leaving the app: a ROM -> its emulator, an emulator -> Desktop.
    BackHandler(
        enabled = !processSettingsPageVisible && (s.pageKey == null || page == null) &&
            (selected.startsWith("emu:") || selected.startsWith("rom:")),
    ) {
        selected = if (selected.startsWith("emu:")) "desktop" else "emu:" + selected.removePrefix("rom:").substringBefore(':')
    }
    LaunchedEffect(s.isHomeApp) { if (!s.isHomeApp && selected == "android-apps") selected = "steam" }
    // The last game uninstalled takes the Games tab with it.
    LaunchedEffect(s.steamGames.isEmpty()) {
        if (s.steamGames.isEmpty() && (selected == "games" || selected.startsWith("app:"))) selected = "steam"
    }
    remember { Motion.scale = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f); true }

    val railSelection = when {
        s.pageKey == "performance" || s.pageKey == "protons" || s.pageKey == "controller-mapping" -> "setup"
        s.pageKey?.startsWith("settings:steam") == true -> "steam"
        s.pageKey?.startsWith("settings:") == true -> "desktop"
        selected.startsWith("app:") -> "games"
        selected.startsWith("emu:") || selected.startsWith("rom:") -> "desktop"
        else -> s.pageKey ?: selected
    }
    // Bumped each time a rail item is picked, so a controller moves on into the new page.
    var railPicks by remember { mutableStateOf(0) }
    val showRailPage: (String) -> Unit = { key ->
        // Components is a full page like Protons or Performance, opened over the current rail
        // selection rather than replacing it.
        if (key == "components") a.onComponents()
        else {
            if (s.pageKey != null) a.onPageBack()
            selected = key
        }
    }
    val onRailFocus: (String) -> Unit = { key ->
        if (key != railSelection) showRailPage(key)
    }
    val onRailSelect: (String) -> Unit = { key ->
        showRailPage(key)
        railPicks++
    }
    val inputModeManager = LocalInputModeManager.current
    val window = LocalWindowInfo.current
    var anyFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().background(colors.background)
            .then(if (s.launcherFullscreen) Modifier else Modifier.systemBarsPadding())
            .onFocusChanged { anyFocused = it.hasFocus },
    ) {
        // Start controllers on the current page's main action, else on the rail.
        LaunchedEffect(processSettingsPageVisible) {
            if (processSettingsPageVisible) return@LaunchedEffect
            snapshotFlow { window.isWindowFocused }.first { it }
            repeat(20) {
                if (anyFocused) return@LaunchedEffect
                if (inputModeManager.inputMode != InputMode.Keyboard) inputModeManager.requestInputMode(InputMode.Keyboard)
                val target = if (frontFocus.primaryAttached > 0) frontFocus.primary else frontFocus.railFor(railSelection)
                runCatching { target.requestFocus() }
                kotlinx.coroutines.delay(100)
            }
        }
        // A tile or button that opens a page goes away with the page it was on, and focus with it;
        // the pad then had nothing to move from (a press landed back on the rail's first item). So
        // once the new page is in, a controller lands on its main button.
        LaunchedEffect(selected, s.pageKey, processSettingsPageVisible) {
            if (processSettingsPageVisible) return@LaunchedEffect
            kotlinx.coroutines.delay(450)
            if (!anyFocused && inputModeManager.inputMode == InputMode.Keyboard) runCatching {
                if (frontFocus.primaryAttached > 0) frontFocus.primary.requestFocus()
                else frontFocus.railFor(railSelection).requestFocus()
            }
        }
        // A rail item picked with a controller moves on into its page, once the page is in.
        LaunchedEffect(railPicks) {
            if (railPicks == 0 || processSettingsPageVisible) return@LaunchedEffect
            kotlinx.coroutines.delay(450)
            if (inputModeManager.inputMode == InputMode.Keyboard && frontFocus.primaryAttached > 0) runCatching {
                frontFocus.paneEntry().requestFocus()
            }
        }
        val paneFocus = Modifier
            .focusProperties { enter = { frontFocus.paneEntry() } }
            .focusGroup()
        val railFocus = Modifier
            .focusProperties { enter = { frontFocus.railFor(railSelection) } }
            .focusGroup()
        Row(modifier = Modifier.fillMaxSize()) {
            SideRail(s, railSelection, onRailSelect, onRailFocus, a, railFocus.fillMaxHeight())
            Box(Modifier.width(1.dp).fillMaxHeight().background(pal.line))
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Pane(
                    s, selected, a, page, Modifier.weight(1f).fillMaxWidth().then(paneFocus), { selected = it },
                    onAndroidAppClick = { app ->
                        if (s.secondScreenDisplays.isEmpty()) a.onAndroidApp(app, null)
                        else appToChooseDisplay = app
                    },
                    onOpenDeveloperOptions = requestDeveloperOptions,
                    onRequestWirelessAdb = requestWirelessAdbFix,
                )
                // Only while a pad or keyboard drives the launcher; a touch hides it again.
                AnimatedVisibility(
                    inputModeManager.inputMode == InputMode.Keyboard,
                    enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(),
                ) { ControllerHints() }
            }
        }

        appToChooseDisplay?.let { app ->
            val secondaryDisplay = s.secondScreenDisplays.firstOrNull()
            ChooseAppDisplayDialog(
                app = app,
                secondaryDisplay = secondaryDisplay,
                onPrimary = {
                    appToChooseDisplay = null
                    a.onAndroidApp(app, Display.DEFAULT_DISPLAY)
                },
                onSecondary = {
                    appToChooseDisplay = null
                    secondaryDisplay?.let { a.onAndroidApp(app, it.id) }
                },
                onDismiss = { appToChooseDisplay = null },
            )
        }
        if (phantomGateVisible && !showWirelessAdbFix) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                PhantomProcessGatePage(
                    status = s.phantomProcessStatus,
                    onDismiss = a.onDismissPhantomGate,
                    onOpenDeveloperOptions = requestDeveloperOptions,
                    onSetUpWirelessAdb = { requestWirelessAdbFix(false) },
                )
            }
        }
        if (showWirelessAdbFix) {
            Box(Modifier.fillMaxSize().background(colors.background)) {
                WirelessAdbFixPage(
                    onBack = { showWirelessAdbFix = false },
                    desiredEnabled = wirelessAdbDesiredEnabled,
                    onOpenDeveloperOptions = requestDeveloperOptions,
                    onPair = a.onWirelessAdbPair,
                    onFindConnectPort = a.onFindWirelessAdbPort,
                    onApply = a.onWirelessAdbApply,
                )
            }
        }
        if (showDeveloperDisplayChoice) {
            DeveloperDisplayChoiceDialog(
                displays = s.secondScreenDisplays.map { display ->
                    display.id to if (s.secondScreenDisplays.size == 1) "Bottom screen" else display.label
                },
                onMainScreen = {
                    showDeveloperDisplayChoice = false
                    a.onOpenDeveloperOptions(null)
                },
                onSecondaryScreen = { displayId ->
                    showDeveloperDisplayChoice = false
                    a.onOpenDeveloperOptions(displayId)
                },
                onDismiss = { showDeveloperDisplayChoice = false },
            )
        }
    }
    val hasBackTarget = (s.pageKey != null && page != null) ||
        ((s.pageKey == null || page == null) &&
            (selected.startsWith("emu:") || selected.startsWith("rom:")))
    // At the top of a section, Back goes to the rail - the launcher itself is never backed out of.
    BackHandler(enabled = !processSettingsPageVisible && !hasBackTarget) {
        if (inputModeManager.inputMode != InputMode.Keyboard) inputModeManager.requestInputMode(InputMode.Keyboard)
        runCatching { frontFocus.railFor(railSelection).requestFocus() }
    }
}


private val Shape14 = RoundedCornerShape(14.dp)

/** What the pad's face buttons do here, along the bottom edge as on a console. */
@Composable
private fun ControllerHints() {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth().height(34.dp).background(colors.surface).padding(horizontal = 20.dp),
        ) {
            HintGlyph("A", "Select")
            HintGlyph("B", "Back")
        }
    }
}

@Composable
private fun HintGlyph(button: String, action: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp).clip(CircleShape).background(colors.onBackground)) {
            Text(button, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.background)
        }
        Text(action, fontSize = 13.sp, color = colors.onSurfaceVariant)
    }
}
private val AttentionAmber = Color(0xFFFFB547)

/**
 * The launcher's sections, always on screen down the left edge: the app is landscape-only, so the
 * width a hamburger menu would save is better spent keeping every section one press away.
 */
@Composable
private fun SideRail(
    s: FrontEndState, selected: String,
    onSelect: (String) -> Unit, onFocusSelect: (String) -> Unit, a: FrontEndActions, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val compact = LocalConfiguration.current.screenHeightDp < 420
    // Under 600dp wide (4:3 and square screens) the labels would cost a fifth of the width.
    val iconOnly = isNarrowScreen()
    val setupNeedsAttention = !s.ready || (s.available != null && s.available != s.installed) ||
        PhantomProcessLimit.blocksSteam(s.phantomProcessStatus)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(if (iconOnly) 64.dp else 92.dp).background(colors.surface).padding(vertical = if (compact) 8.dp else 12.dp),
    ) {
        Image(painterResource(R.drawable.logo), contentDescription = "DroidDeck", modifier = Modifier.size(if (compact) 28.dp else 34.dp))
        Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            if (s.isHomeApp) RailItem("Apps", Icons.Outlined.Apps, "android-apps", selected == "android-apps", compact, iconOnly, onFocus = { onFocusSelect("android-apps") }) { onSelect("android-apps") }
            RailItem("Steam", Icons.Outlined.SportsEsports, "steam", selected == "steam", compact, iconOnly, onFocus = { onFocusSelect("steam") }) { onSelect("steam") }
            // Games appears once there is one: an empty list is no place to land.
            if (s.steamGames.isNotEmpty()) RailItem("Games", Icons.Outlined.VideoLibrary, "games", selected == "games", compact, iconOnly, onFocus = { onFocusSelect("games") }) { onSelect("games") }
            RailItem("Desktop", Icons.Outlined.DesktopWindows, "desktop", selected == "desktop", compact, iconOnly, onFocus = { onFocusSelect("desktop") }) { onSelect("desktop") }
            RailItem("Components", Icons.Outlined.Layers, "components", selected == "components", compact, iconOnly, onFocus = { onFocusSelect("components") }) { onSelect("components") }
            RailItem("Setup", Icons.Outlined.Tune, "setup", selected == "setup", compact, iconOnly, badge = setupNeedsAttention, onFocus = { onFocusSelect("setup") }) { onSelect("setup") }
        }
        AnimatedVisibility(s.busy, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 8.dp)) {
                val barWidth = if (iconOnly) 44.dp else 60.dp
                if (s.percent >= 0) LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.width(barWidth))
                else LinearProgressIndicator(modifier = Modifier.width(barWidth))
                Text(if (s.percent >= 0) "${s.percent}%" else if (iconOnly) "…" else "Working", fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        var lastRunning by remember { mutableStateOf("") }
        if (s.running != null) lastRunning = s.running
        AnimatedVisibility(
            s.running != null,
            enter = expandVertically(Motion.sp(0.75f)) + fadeIn(Motion.tw(300)),
            exit = shrinkVertically(Motion.tw(220)) + fadeOut(Motion.tw(180)),
        ) { ResumeRailItem(lastRunning, compact, iconOnly, a.onResume) }
    }
}

@Composable
private fun RailItem(
    label: String, icon: ImageVector, key: String, current: Boolean, compact: Boolean, iconOnly: Boolean,
    badge: Boolean = false, onFocus: () -> Unit = {}, onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val frontFocus = LocalFrontFocus.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val pressed by src.collectIsPressedAsState()
    if (frontFocus != null) LaunchedEffect(focused) {
        if (focused) {
            frontFocus.focusedRail = key
            onFocus()
        }
        else if (frontFocus.focusedRail == key) frontFocus.focusedRail = null
    }
    val fg by animateColorAsState(
        if (current) pal.signal else if (focused || hovered) colors.onBackground else colors.onSurfaceVariant,
        Motion.tw(220), label = "railFg",
    )
    val fill by animateColorAsState(
        if (current) pal.signal.copy(alpha = 0.14f) else if (focused || hovered) Color.White.copy(alpha = 0.05f) else Color.Transparent,
        Motion.tw(220), label = "railFill",
    )
    val ring by animateColorAsState(if (focused) pal.signal else Color.Transparent, Motion.tw(180), label = "railRing")
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "railScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(if (frontFocus != null) Modifier.focusRequester(frontFocus.railFor(key)) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(width = if (iconOnly) 52.dp else 80.dp, height = if (iconOnly) 48.dp else if (compact) 52.dp else 60.dp)
            .clip(Shape14)
            .background(fill)
            .border(2.dp, ring, Shape14)
            .hoverable(src)
            .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Tab, onClick = onClick)
            .then(if (iconOnly) Modifier.semantics { contentDescription = label } else Modifier),
    ) {
        if (iconOnly) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (compact) 20.dp else 22.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, softWrap = false)
        }
        if (badge) Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = if (iconOnly) 7.dp else 8.dp, end = if (iconOnly) 9.dp else 18.dp)
                .size(8.dp).clip(CircleShape).background(AttentionAmber),
        )
    }
}

/** The running session, one press from anywhere in the launcher. */
@Composable
private fun ResumeRailItem(name: String, compact: Boolean, iconOnly: Boolean, onResume: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringScale by pulse.animateFloat(0.4f, 1.6f, infiniteRepeatable(tween(1600, easing = Motion.Ease), RepeatMode.Restart), label = "ring")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .padding(top = 6.dp)
            .width(if (iconOnly) 52.dp else 80.dp)
            .clip(Shape14)
            .background(pal.good.copy(alpha = if (focused || hovered) 0.20f else 0.12f))
            .border(2.dp, if (focused) pal.signal else Color.Transparent, Shape14)
            .hoverable(src)
            .clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onResume)
            .semantics { contentDescription = "Resume $name" }
            .padding(vertical = if (iconOnly) 15.dp else if (compact) 6.dp else 9.dp, horizontal = 4.dp),
    ) {
        Box(modifier = Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(14.dp).graphicsLayer { scaleX = ringScale; scaleY = ringScale; alpha = (1.6f - ringScale) / 1.2f }.border(1.5.dp, pal.good, CircleShape))
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(pal.good))
        }
        // Icons only: the live dot alone says something is running; its description says what.
        if (!iconOnly) {
            Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, softWrap = false)
            Text(name, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun Pane(
    s: FrontEndState, selected: String, a: FrontEndActions, page: (@Composable () -> Unit)?, modifier: Modifier,
    onSelect: (String) -> Unit, onAndroidAppClick: (HomeApp.LaunchableApp) -> Unit,
    onOpenDeveloperOptions: () -> Unit, onRequestWirelessAdb: (Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
      CompositionLocalProvider(LocalNarrowPane provides (maxWidth < NarrowPaneWidth)) {
        val backdropArt: File? = when {
            s.pageKey != null && page != null -> null
            selected.startsWith("app:") -> s.steamGames.firstOrNull { "app:${it.appId}" == selected }?.art
            selected == "games" -> s.steamGames.maxByOrNull { it.lastPlayed }?.art
            selected.startsWith("rom:") -> romFor(s, selected)?.second?.art
            else -> null
        }
        Backdrop(backdropArt)
        AnimatedContent(
            // Picking another game changes the detail beside the list, not the whole page.
            targetState = if (page != null && s.pageKey != null) s.pageKey else if (selected.startsWith("app:")) "games" else selected,
            transitionSpec = {
                (fadeIn(Motion.tw(300, 80)) + slideInVertically(Motion.tw(420, 80)) { it / 24 })
                    .togetherWith(fadeOut(Motion.tw(170)) + slideOutVertically(Motion.tw(170)) { -it / 40 })
                    .apply { targetContentZIndex = 1f }
            },
            label = "pane",
        ) { key ->
            if (page != null && key == s.pageKey) page()
            else Content(s, if (key == "games") selected else key, a, Modifier.fillMaxSize(), onSelect, onAndroidAppClick, onOpenDeveloperOptions, onRequestWirelessAdb)
        }
      }
    }
}

@Composable
private fun Backdrop(art: File?) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Crossfade(targetState = art, animationSpec = Motion.tw(700), label = "backdrop") { a ->
        // A game's own art, blurred, where it has some; otherwise the plain ground with one quiet
        // glow of the theme's signal colour - a colour per title hash read as noise.
        if (a != null && Build.VERSION.SDK_INT >= 31) {
            Box(modifier = Modifier.fillMaxSize().alpha(0.26f).blur(70.dp)) {
                AsyncImage(model = a, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(pal.signal.copy(alpha = 0.07f), Color.Transparent), center = Offset.Zero, radius = 1400f)))
        }
    }
    Spacer(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.35f)))))
}

private fun romFor(s: FrontEndState, selected: String): Pair<Library.Emulator, Library.Rom>? {
    val parts = selected.split(":")
    val e = s.emulators.firstOrNull { it.id == parts.getOrNull(1) } ?: return null
    val g = e.games.getOrNull(parts.getOrNull(2)?.toIntOrNull() ?: -1) ?: return null
    return e to g
}

@Composable
private fun Content(
    s: FrontEndState, selected: String, a: FrontEndActions, modifier: Modifier,
    onSelect: (String) -> Unit, onAndroidAppClick: (HomeApp.LaunchableApp) -> Unit,
    onOpenDeveloperOptions: () -> Unit, onRequestWirelessAdb: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val detailPosterWidth = if (LocalConfiguration.current.screenHeightDp < 600) 72.dp else 120.dp
    val narrow = LocalNarrowPane.current
    val padH = if (narrow) 16.dp else 22.dp
    val padV = if (narrow) 12.dp else 18.dp
    // Setup scrolls inside itself, under its tabs.
    if (selected == "setup") {
        Column(modifier = modifier.padding(horizontal = padH, vertical = padV)) {
            SetupPanel(s, a, onOpenDeveloperOptions, onRequestWirelessAdb)
        }
        return
    }
    // Steam is a full-bleed wall of the library with Play over it.
    if (selected == "steam") {
        SteamHome(s, a, modifier)
        return
    }
    // The Games tab lays out its own list and detail.
    if (selected == "games" || selected.startsWith("app:")) {
        GamesPage(s, a, selected, onSelect, modifier)
        return
    }
    // Every other page scrolls as one - header, hero and grid - so a short screen reaches the grid
    // instead of showing a sliver of it under a fixed hero. A focused tile scrolls itself into view.
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = padH, vertical = padV)) {
        when {
            selected == "android-apps" && s.isHomeApp -> {
                Rise(0) { PageHeader("Android apps") { Chip("${s.androidApps.size} apps", ok = false) } }
                if (s.androidApps.isEmpty()) Rise(3) { Note("No launchable Android apps found.") }
                else Rise(3, Modifier.fillMaxWidth()) {
                    ArtGrid(s.androidApps.map { app ->
                        Tile(
                            app.label,
                            null,
                            null,
                            "android:${app.packageName}",
                            onClick = { onAndroidAppClick(app) },
                            iconBitmap = app.icon,
                        )
                    })
                }
            }
            selected == "desktop" -> {
                val installed = s.emulators.filter { it.installed }
                val available = s.emulators.filter { !it.installed }
                Rise(0) {
                    PageHeader("Desktop") {
                        if (s.desktopInstalled) Chip("● Desktop installed", ok = true) else Chip("Installs on first open", ok = false)
                    }
                }
                Rise(2) { DesktopCard(s, a) }
                if (installed.isNotEmpty()) {
                    Rise(3) { SectionTitle("Emulators", "${installed.size} installed") }
                    Rise(4) { EmulatorGrid(installed, first = true, onSelect = onSelect) }
                }
                if (available.isNotEmpty()) {
                    Rise(5) { SectionTitle("Available to install", available.size.toString()) }
                    Rise(6) { EmulatorGrid(available, first = installed.isEmpty(), onSelect = onSelect) }
                }
            }
            selected.startsWith("emu:") -> {
                val e = s.emulators.firstOrNull { "emu:${it.id}" == selected }
                if (e == null) Note("Not installed.") else {
                    val pkgId = Library.packageId(e.id)
                    val pkg = pkgId?.let { id -> s.packages?.firstOrNull { it.id == id } }
                    Rise(0) {
                        BackLink("Desktop") { onSelect("desktop") }
                    }
                    Rise(1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                        ) {
                            Image(painterResource(e.iconRes), null, modifier = Modifier.size(52.dp))
                            Column {
                                Text(e.name, fontSize = if (narrow) 22.sp else 26.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
                                Text(
                                    e.system.replaceFirstChar { it.uppercase() } + if (e.installed) "" else " · not installed",
                                    fontSize = 14.sp, color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (e.installed) {
                        Rise(3) {
                            Actions {
                                PrimaryButton("Open ${e.name}", enabled = s.ready && !s.busy, main = true) { a.onEmulator(e) }
                                SecondaryButton("ROMs folder", onClick = a.onRoms)
                                if (pkg != null) SecondaryButton(
                                    if (pkg.kind == "appimage") "Remove" else "Forget",
                                    enabled = s.packageBusyId == null && !s.sessionRunning,
                                ) { a.onRemovePackage(pkg.id) }
                            }
                        }
                        Rise(4) { SectionTitle("Games", e.games.size.toString()) }
                        if (e.games.isEmpty()) Rise(5) {
                            Note(
                                if (s.romsDir == null) "Choose a ROMs folder."
                                else if (e.id == "retroarch") "Browse to /root/ROMs in RetroArch."
                                else "Add ${e.system} games to ROMs/${e.system.substringBefore(' ')}.",
                            )
                        }
                        else Rise(5, Modifier.fillMaxWidth()) {
                            ArtGrid(e.games.mapIndexed { index, g -> Tile(g.name, if (g.art != null) "installed" else g.hostPath.extension.uppercase().ifEmpty { "folder" }, g.art, "rom:${e.id}:$index", e.iconRes) { onSelect("rom:${e.id}:$index") } }, wide = e.games.none { it.art != null })
                        }
                    } else {
                        if (pkg != null) Rise(2) {
                            Actions {
                                PrimaryButton(
                                    if (s.packageBusyId == pkg.id) "Installing…" else "Install ${e.name}",
                                    enabled = s.packageBusyId == null && s.ready && !s.packageCatalogLoading && !s.sessionRunning,
                                ) { a.onInstallPackage(pkg.id) }
                                if (s.sessionRunning) ActionChip("Stop session to install", ok = false)
                                else if (!s.ready) ActionChip("Runtime required", ok = false)
                            }
                        }
                        Rise(3) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Note(when {
                                    s.packageCatalogLoading -> "Loading install details…"
                                    pkg == null -> "Install details are unavailable right now. Try again when the package catalog is reachable."
                                    !s.ready -> "Install the Linux runtime from Setup before installing desktop apps."
                                    s.sessionRunning -> "Stop the active session before installing desktop apps."
                                    pkg.notes.isNotBlank() -> pkg.notes
                                    else -> "Install ${e.name} into the Linux desktop runtime."
                                })
                            }
                        }
                        if (s.packageBusyId == pkg?.id) Rise(4) {
                            val stage = s.packageStage
                            Text(
                                if (stage != null && s.packagePercent >= 0) "$stage · ${s.packagePercent}%" else stage ?: "Starting…",
                                fontSize = 12.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp),
                            )
                            if (s.packagePercent >= 0) LinearProgressIndicator(progress = { s.packagePercent / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp))
                            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
                        }
                        if (pkg?.kind == "tar") Rise(5) { Note("Forgetting this package hides it from Desktop; its files remain in the Linux runtime.") }
                    }
                }
            }
            selected.startsWith("rom:") -> {
                val pair = romFor(s, selected)
                if (pair == null) Note("That game is gone from the ROMs folder.") else {
                    val (e, g) = pair
                    Rise(0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BackLink(e.name) { onSelect("emu:${e.id}") }
                            Eyebrow("Desktop · ${e.system}")
                        }
                    }
                    Rise(1) { Title(g.name) }
                    Rise(2) {
                        Row {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(g.guestPath, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.surface).padding(horizontal = 10.dp, vertical = 6.dp))
                                Spacer(Modifier.height(14.dp))
                                Actions {
                                    Image(painterResource(e.iconRes), null, modifier = Modifier.size(40.dp))
                                    PrimaryButton("Launch in ${e.name}", enabled = s.ready && !s.busy, main = true) { a.onRom(g) }
                                    ActionChip(g.hostPath.extension.uppercase().ifEmpty { "folder" }, ok = false)
                                }
                            }
                            if (g.art != null && !narrow) Poster(g.art, g.name, Modifier.width(detailPosterWidth))
                        }
                    }
                    val others = e.games.filter { it !== g }
                    if (others.isNotEmpty()) {
                        Rise(3) { SectionTitle("Also in ${e.name}", null) }
                        Rise(4, Modifier.fillMaxWidth()) {
                            ArtGrid(others.map { x ->
                                val index = e.games.indexOf(x)
                                Tile(x.name, if (x.art != null) "installed" else x.hostPath.extension.uppercase().ifEmpty { "folder" }, x.art, "rom:${e.id}:$index", e.iconRes) { onSelect("rom:${e.id}:$index") }
                            }, wide = others.none { it.art != null })
                        }
                    }
                }
            }
            else -> Note("Select an item.")
        }
    }
}

/** The Linux desktop itself, above the emulators that run in it. */
@Composable
private fun DesktopCard(s: FrontEndState, a: FrontEndActions) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val narrow = LocalNarrowPane.current
    val actions: @Composable () -> Unit = {
        Actions {
            // Enabled without a runtime or the desktop: the session's loading screen installs them first.
            PrimaryButton(if (s.desktopInstalled) "Open desktop" else "Install & open desktop", enabled = !s.busy, main = true, onClick = a.onDesktop)
            Cog(onClick = a.onDesktopSettings)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(Shape16).background(colors.surface).border(1.dp, pal.line, Shape16).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).clip(Shape14).background(colors.surfaceVariant).border(1.dp, pal.line2, Shape14),
            ) { Icon(Icons.Outlined.DesktopWindows, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(26.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Linux desktop", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text("LXQt, Firefox and your emulators", fontSize = 14.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!narrow) actions()
        }
        if (narrow) actions()
    }
}

/** Emulators as wide tiles - three across, or a list on a narrow page. */
@Composable
private fun EmulatorGrid(emulators: List<Library.Emulator>, first: Boolean, onSelect: (String) -> Unit) {
    val columns = if (LocalNarrowPane.current) 1 else 3
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
        emulators.chunked(columns).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { i, e ->
                    key(e.id) {
                        EmulatorTile(e, Modifier.weight(1f).fillMaxHeight(), isFirst = first && r == 0 && i == 0) { onSelect("emu:${e.id}") }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * One emulator: its icon, system and games. One not yet installed says so in words and keeps full
 * contrast - dimming it read as disabled rather than one press from installing.
 */
@Composable
private fun EmulatorTile(e: Library.Emulator, modifier: Modifier, isFirst: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "emuScale")
    val system = e.system.replaceFirstChar { it.uppercase() }
    val detail = if (e.installed && e.id != "retroarch") "$system · ${e.games.size} game${if (e.games.size == 1) "" else "s"}" else system
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.paneItem("tile:emu:${e.id}").then(if (isFirst) Modifier.firstTile() else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Shape14)
            .background(if (hot) pal.signal.copy(alpha = 0.10f) else if (e.installed) colors.surface else Color.Transparent)
            .border(if (hot) 2.dp else 1.dp, if (hot) pal.signal else pal.line, Shape14)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Image(painterResource(e.iconRes), contentDescription = null, modifier = Modifier.size(if (e.installed) 44.dp else 36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(e.name, fontSize = if (e.installed) 15.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, fontSize = if (e.installed) 13.sp else 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!e.installed) Text(
            "Install", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal.signal,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, pal.line2, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private enum class CheckState { OK, WARN, BUSY }

/** One requirement in Setup's system check: a status mark, what it is, and at most one action. */
@Composable
private fun CheckRow(state: CheckState, title: String, detail: String?, divider: Boolean = true, action: (@Composable () -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val tint = when (state) {
        CheckState.OK -> pal.good
        CheckState.WARN -> AttentionAmber
        CheckState.BUSY -> pal.signal
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
            .background(if (state == CheckState.WARN) AttentionAmber.copy(alpha = 0.07f) else Color.Transparent)
            .heightIn(min = 60.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f))) {
            Icon(
                when (state) {
                    CheckState.OK -> Icons.Filled.Check
                    CheckState.WARN -> Icons.Filled.PriorityHigh
                    CheckState.BUSY -> Icons.Filled.Refresh
                },
                contentDescription = when (state) {
                    CheckState.OK -> "Done"
                    CheckState.WARN -> "Needs attention"
                    CheckState.BUSY -> "Working"
                },
                tint = tint, modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            if (detail != null) Text(detail, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) action()
    }
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
}

@Composable
private fun SetupPanel(
    s: FrontEndState,
    a: FrontEndActions,
    onOpenDeveloperOptions: () -> Unit,
    onRequestWirelessAdb: (Boolean) -> Unit,
) {
    val host = rememberMenuHost()
    var processLimitBusy by remember { mutableStateOf(false) }
    var processLimitMessage by remember { mutableStateOf<String?>(null) }
    val setProcessLimit: (Boolean) -> Unit = { enabled ->
        processLimitBusy = true
        processLimitMessage = null
        a.onSetPhantomProcessLimit(enabled) { error ->
            processLimitBusy = false
            if (error == null) {
                a.onRefreshPhantomStatus()
            } else {
                processLimitMessage = "Check the Wireless debugging IP address & Port, or pair again if Android removed this device."
                onRequestWirelessAdb(enabled)
            }
        }
    }
    val runtime = when {
        s.busy -> "Working…"
        !s.ready -> "Install"
        s.available != null && s.available != s.installed -> "Update"
        else -> "Manage"
    }
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val gpuOk = remember { DeviceSupport.adreno() }
    val gpuName = remember { DeviceSupport.gpuName() }
    val limitBlocks = PhantomProcessLimit.blocksSteam(s.phantomProcessStatus)
    val signedIn = s.offlineAccount != null
    var showLimitDetails by rememberSaveable { mutableStateOf(false) }
    val checks = 4
    val readyCount = listOf(gpuOk, s.ready && !s.busy, !limitBlocks, signedIn).count { it }
    // Five tabs instead of one long scroll; LB and RB turn them from anywhere on the page.
    val tabs = listOf("Overview", "Controller", "Session", "Launcher", "About")
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabFocus = remember { List(tabs.size) { FocusRequester() } }
    var tabTurned by remember { mutableStateOf(false) }
    val pick: (Int) -> Unit = { i -> tab = i; tabTurned = true }
    val inputModeManager = LocalInputModeManager.current
    // The control a controller was on went with the old tab: it lands on the new tab itself.
    LaunchedEffect(tab) {
        if (tabTurned && inputModeManager.inputMode == InputMode.Keyboard) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { tabFocus[tab].requestFocus() }
        }
    }
    Rise(0, Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().bumpers(
                onPrevious = { pick((tab + tabs.size - 1) % tabs.size) },
                onNext = { pick((tab + 1) % tabs.size) },
            ),
        ) {
            PageHeader("Setup") {
                Chip(if (readyCount == checks) "● All set" else "$readyCount of $checks ready", ok = readyCount == checks)
            }
            TabStrip(tabs, tab, pick, Modifier.padding(bottom = 4.dp), tabFocus)
            Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                when (tab) {
                    0 -> {
                        SectionTitle("System check", null)
                        // What Steam needs, one row each: green when done, one button when not. The
                        // process-limit controls only open under their row.
                        Column(modifier = Modifier.fillMaxWidth().clip(Shape14).background(colors.surface).border(1.dp, pal.line, Shape14)) {
                            CheckRow(
                                if (gpuOk) CheckState.OK else CheckState.WARN,
                                if (gpuOk) "Device supported" else "GPU not supported",
                                if (gpuOk) gpuName else "Steam draws with an Adreno driver; $gpuName may show a black screen",
                            )
                            CheckRow(
                                when { s.busy -> CheckState.BUSY; !s.ready -> CheckState.WARN; else -> CheckState.OK },
                                "Linux runtime",
                                when {
                                    s.busy -> if (s.percent >= 0) "${s.stage} · ${s.percent}%" else s.stage
                                    !s.ready -> "Not installed · about 3 GB, installed on the first Play"
                                    s.available != null && s.available != s.installed -> "${s.installed ?: "Installed"} · update available"
                                    else -> "${s.installed ?: "Installed"} · up to date"
                                },
                            ) { SecondaryButton(runtime, enabled = !s.busy, compact = true, onClick = a.onRuntime) }
                            CheckRow(
                                if (limitBlocks) CheckState.WARN else CheckState.OK,
                                if (limitBlocks) "Android may close Steam" else "Android process limit",
                                if (limitBlocks) "“Restrict child processes” is on - it takes a minute to turn off" else PhantomProcessLimit.title(s.phantomProcessStatus),
                            ) {
                                if (limitBlocks) PrimaryButton(if (showLimitDetails) "Hide" else "Fix it", compact = true) { showLimitDetails = !showLimitDetails }
                                else if (s.phantomProcessStatus != PhantomProcessStatus.NOT_APPLICABLE) {
                                    SecondaryButton(if (showLimitDetails) "Hide" else "Details", compact = true) { showLimitDetails = !showLimitDetails }
                                }
                            }
                            AnimatedVisibility(showLimitDetails, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 14.dp, top = 4.dp, bottom = 10.dp)) {
                                    Text(
                                        PhantomProcessLimit.instructions(s.phantomProcessStatus),
                                        fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                                        SecondaryButton("Developer options", compact = true, onClick = onOpenDeveloperOptions)
                                        SecondaryButton("Check again", compact = true, onClick = a.onRefreshPhantomStatus)
                                        SecondaryButton("Copy ADB command", compact = true, onClick = { a.onCopyPhantomCommand(false) })
                                    }
                                    Text(
                                        PhantomProcessLimit.ADB_COMMAND, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                        color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                    if (s.phantomProcessStatus != PhantomProcessStatus.NOT_APPLICABLE) {
                                        Text(
                                            "With wireless debugging paired, DroidDeck can switch the limit itself. Turning it off is recommended for Steam sessions.",
                                            fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                                            SecondaryButton("Turn limit off", compact = true, enabled = !processLimitBusy && s.phantomProcessStatus != PhantomProcessStatus.DISABLED) { setProcessLimit(false) }
                                            SecondaryButton("Turn limit on", compact = true, enabled = !processLimitBusy && s.phantomProcessStatus != PhantomProcessStatus.ENABLED) { setProcessLimit(true) }
                                        }
                                        if (processLimitBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                                        processLimitMessage?.let { Text(it, fontSize = 14.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
                                    }
                                }
                            }
                            CheckRow(
                                if (signedIn) CheckState.OK else CheckState.WARN,
                                "Steam account",
                                s.offlineAccount?.let { if (s.offline) "Signed in as $it · offline mode" else "Signed in as $it" } ?: "Press Play and sign in to Steam",
                                divider = false,
                            )
                        }
                        SectionTitle("Tools", null)
                        ToolGrid(s, a)
                    }
                    1 -> {
                        val controller = s.controller
                        if (controller != null && a.controller != null) SettingsGroup("Controller") {
                            ControllerRows(host, s.oscMode, controller, a.controller)
                        }
                        if (s.controller == null || a.controller == null) Note("Controller settings are unavailable.")
                    }
                    2 -> {
                        SettingsGroup("Session") {
                            ChoiceRow(
                                host, "back-actions", "Back", SessionPrefs.backActionsOrder(s.backActionsInverted),
                                listOf(
                                    false to SessionPrefs.BACK_MENU_THEN_QAM,
                                    true to SessionPrefs.BACK_QAM_THEN_MENU,
                                ), s.backActionsInverted, onPick = a.onBackActionsInverted,
                            )
                            SettingsRow("Frame generation", "Select the frame generation mode") {
                                Box {
                                    ValueChip(s.frameGenLabel, host.open == "fg") { host.open = if (host.open == "fg") null else "fg" }
                                    FrameGenMenu(s, a, host)
                                }
                            }
                            SettingsRow("Session logs", "${if (s.logsEnabled) "Enabled" else "Disabled"} · logs are saved after each session") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    SecondaryButton(if (s.logsEnabled) "Turn off" else "Turn on") { a.onLogs() }
                                    SecondaryButton("Share latest") { a.onShareLogs() }
                                }
                            }
                            SettingsRow("Offline mode", s.offlineAccount?.let { if (s.offline) "Enabled for $it" else "Signed in as $it" } ?: "Sign in to Steam first") {
                                SecondaryButton(if (s.offline) "Turn off" else "Turn on", enabled = s.offlineAccount != null) { a.onOffline() }
                            }
                        }
                    }
                    3 -> {
                        SettingsGroup("Launcher") {
                            SettingsRow("Theme", "Choose the launcher appearance") {
                                Box {
                                    ValueChip(Themes.byId(s.theme).label, host.open == "theme") { host.open = if (host.open == "theme") null else "theme" }
                                    AnchoredMenu(host.open == "theme", onDismiss = { if (host.open == "theme") host.open = null }, title = "Theme") { firstItemFocus ->
                                        Themes.all.forEachIndexed { index, theme ->
                                            MenuItem(theme.label, checked = s.theme == theme.id, focusRequester = if (index == 0) firstItemFocus else null) {
                                                a.onTheme(theme.id)
                                                host.open = null
                                            }
                                        }
                                    }
                                }
                            }
                            ToggleRow(
                                host, "home-screen", "Use as a Home screen",
                                if (s.homeScreenEnabled) "DroidDeck can be the phone's Home app" else "Off: DroidDeck is never offered as a Home app",
                                s.homeScreenEnabled,
                            ) { a.onHomeScreen(it) }
                            ToggleRow(
                                host, "launcher-fullscreen", "Fullscreen",
                                if (s.launcherFullscreen) "Hide the Android status and navigation bars" else "Show the Android status and navigation bars",
                                s.launcherFullscreen,
                            ) { a.onLauncherFullscreen(it) }
                            if (s.homeScreenEnabled) {
                                ActionRow("Default Home app", s.defaultHomeLabel ?: "Choose a Home app", "Choose", a.onHomeApp)
                            }
                        }
                    }
                    else -> {
                        SettingsGroup("About") {
                            ActionRow("Build", s.buildLabel, "Check for newer", a.onCheckLatestBuild)
                            ActionRow("Credits", "The people and projects DroidDeck builds on", "View", a.onCredits)
                        }
                    }
                }
            }
        }
    }
}

/** The launcher's own tools as cards: four across, two by two on a narrow page. */
@Composable
private fun ToolGrid(s: FrontEndState, a: FrontEndActions) {
    val columns = if (LocalNarrowPane.current) 2 else 4
    val tools = listOf(
        ToolSpec(Icons.Outlined.Folder, "Files", "Browse and manage files", a.onFiles),
        ToolSpec(Icons.Outlined.Extension, "Compatibility tools", "Install ARM64 Proton builds", a.onProtons),
        ToolSpec(Icons.Outlined.Speed, "Performance", "CPU core assignment", a.onPerformance),
        ToolSpec(Icons.Outlined.VideogameAsset, "ROMs folder", s.romsDir ?: "Choose where emulator games are stored", a.onRoms),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        for (row in tools.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (t in row) ToolCard(t, Modifier.weight(1f).fillMaxHeight())
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class ToolSpec(val icon: ImageVector, val title: String, val detail: String, val onClick: () -> Unit)

@Composable
private fun ToolCard(t: ToolSpec, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "toolScale")
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.paneItem("tool:${t.title}")
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Shape14)
            .background(if (hot) pal.signal.copy(alpha = 0.10f) else colors.surface)
            .border(if (hot) 2.dp else 1.dp, if (hot) pal.signal else pal.line, Shape14)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = t.onClick)
            .controllerConfirm(onClick = t.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(t.icon, contentDescription = null, tint = if (hot) pal.signal else colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.detail, fontSize = 13.sp, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private class Tile(
    val title: String, val sub: String?, val art: File?, val key: String,
    val iconRes: Int? = null, val dim: Boolean = false, val iconBitmap: Bitmap? = null,
    val showFooter: Boolean = true,
    val onClick: () -> Unit,
)


@Composable
internal fun Eyebrow(t: String) {
    val pal = LocalPalette.current
    val rule = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rule.animateTo(1f, Motion.tw(600, 120)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.width(18.dp).height(1.5.dp).graphicsLayer { scaleX = rule.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }.background(pal.signal))
        Text(t.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = pal.signal)
    }
}
@Composable internal fun Title(t: String) = Text(t, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
@Composable internal fun Lede(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
@Composable
private fun SectionTitle(t: String, detail: String?) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(t.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = colors.onSurfaceVariant)
        if (detail != null) Text(detail, fontSize = 12.sp, color = colors.onBackground)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(LocalPalette.current.line))
    }
}
@Composable private fun Note(t: String) = Text(t, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().clip(Shape12).background(MaterialTheme.colorScheme.surface).border(1.dp, LocalPalette.current.line2, Shape12).padding(12.dp))
/** A page's buttons: they wrap onto a second line rather than run off a narrow page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(content: @Composable () -> Unit) = FlowRow(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
) { content() }

/** A status chip in a row of buttons, centred on their height. */
@Composable
private fun ActionChip(t: String, ok: Boolean) = Box(contentAlignment = Alignment.Center, modifier = Modifier.heightIn(min = 44.dp)) { Chip(t, ok) }

private val Shape16 = RoundedCornerShape(16.dp)

/** One level up, as Back and B do: a real button, big enough to hit ([compact]: for the tightest layouts). */
@Composable
internal fun BackLink(label: String, compact: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val shape = RoundedCornerShape(22.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.paneItem("back:$label").heightIn(min = if (compact) 36.dp else 44.dp)
            .clip(shape)
            .background(if (hot) pal.signal.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .border(2.dp, if (hot) pal.signal else Color.Transparent, shape)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .controllerConfirm(onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
    ) {
        Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = if (hot) pal.signal else colors.onBackground, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FrameGenMenu(s: FrontEndState, a: FrontEndActions, host: MenuHost) {
    AnchoredMenu(host.open == "fg", onDismiss = { if (host.open == "fg") host.open = null }, title = "Frame generation") { firstItemFocus ->
        val need = if (s.lsfgReady) null else "Install Lossless Scaling in Steam"
        MenuItem("Off", checked = s.frameGenEngine == FrameGen.ENGINE_OFF, focusRequester = firstItemFocus) { a.onFrameGenPick(FrameGen.ENGINE_OFF, 2); host.open = null }
        for (m in 2..4) MenuItem("Win-FG ${m}×", checked = s.frameGenEngine == FrameGen.ENGINE_WINFG && s.frameGenMultiplier == m) { a.onFrameGenPick(FrameGen.ENGINE_WINFG, m); host.open = null }
        for (m in 2..4) MenuItem("LSFG ${m}×", checked = s.frameGenEngine == FrameGen.ENGINE_LSFG && s.frameGenMultiplier == m, enabled = s.lsfgReady, detail = need) { a.onFrameGenPick(FrameGen.ENGINE_LSFG, m); host.open = null }
    }
}

/**
 * What shapes a launch, beside the game rather than three screens away in Setup. They are the
 * app-wide settings - each card opens the same page or menu Setup does.
 */
@Composable
private fun LaunchSettings(s: FrontEndState, a: FrontEndActions, host: MenuHost) {
    // Three across, two on a narrow page; each row's cards share one height.
    val columns = if (LocalNarrowPane.current) 2 else 3
    val controller = a.controller
    val cards = buildList<@Composable (Modifier) -> Unit> {
        add { m -> SettingCard("Compatibility", "Proton & components", "card:components", m, a.onComponents) }
        add { m ->
            Box(m) {
                SettingCard("Frame generation", s.frameGenLabel, "card:fg", Modifier.fillMaxSize()) {
                    host.open = if (host.open == "fg") null else "fg"
                }
                FrameGenMenu(s, a, host)
            }
        }
        if (controller != null) add { m -> SettingCard("Controls", "Button mapping", "card:controls", m, controller.onMapping) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        for (row in cards.chunked(columns)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                for (card in row) card(Modifier.weight(1f).fillMaxHeight())
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingCard(label: String, value: String, id: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "cardScale")
    val edge by animateColorAsState(if (hot) pal.signal else pal.line2, Motion.tw(220), label = "cardEdge")
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier.paneItem(id)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(Shape14)
            .background(if (hot) pal.signal.copy(alpha = 0.10f) else colors.surface)
            .border(if (hot) 2.dp else 1.dp, edge, Shape14)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
            .controllerConfirm(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 13.sp, color = if (hot) pal.signal else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The Games tab: installed Steam games down the left, most recently played first, and the one
 * picked beside them - its banner, Launch and the launch settings. One game skips the list.
 */
@Composable
private fun GamesPage(s: FrontEndState, a: FrontEndActions, selected: String, onSelect: (String) -> Unit, modifier: Modifier) {
    val games = remember(s.steamGames) { s.steamGames.sortedByDescending { it.lastPlayed } }
    val current = games.firstOrNull { "app:${it.appId}" == selected } ?: games.firstOrNull() ?: return
    val host = rememberMenuHost()
    val narrow = LocalNarrowPane.current
    if (games.size == 1) {
        Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (narrow) 16.dp else 22.dp, vertical = if (narrow) 12.dp else 18.dp)) {
            Rise(0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    GameHero(current, Modifier.weight(1f).heightIn(min = if (narrow) 190.dp else 250.dp)) {
                        GameHeroCopy(current, if (narrow) 28.sp else 38.sp)
                        PrimaryButton("Launch", enabled = s.ready && !s.busy, main = true, icon = Icons.Filled.PlayArrow) { a.onSteamGame(current) }
                    }
                    if (!narrow) Poster(current.art, current.name, Modifier.width(168.dp))
                }
            }
            Rise(1) { SectionTitle("Launch settings", null) }
            Rise(2) { LaunchSettings(s, a, host) }
        }
        return
    }
    val pal = LocalPalette.current
    Row(modifier = modifier) {
        GameList(games, current, onSelect = { onSelect("app:${it.appId}") }, onLaunch = { a.onSteamGame(it) },
            modifier = Modifier.width(if (narrow) 168.dp else 250.dp).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(pal.line))
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 14.dp else 20.dp, vertical = 16.dp),
        ) {
            GameHero(current, Modifier.fillMaxWidth().heightIn(min = if (narrow) 170.dp else 200.dp)) {
                GameHeroCopy(current, if (narrow) 24.sp else 32.sp)
                PrimaryButton("Launch", enabled = s.ready && !s.busy, main = true, icon = Icons.Filled.PlayArrow) { a.onSteamGame(current) }
            }
            SectionTitle("Launch settings", null)
            LaunchSettings(s, a, host)
        }
    }
}

/** When it was last played (or where it is, if never) over its name, then room for Launch. */
@Composable
private fun ColumnScope.GameHeroCopy(g: Library.SteamGame, titleSize: androidx.compose.ui.unit.TextUnit) {
    Text(
        (lastPlayedText(g.lastPlayed) ?: libraryLabel(g.library)).uppercase(),
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = LocalPalette.current.signal,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    Text(
        g.name, fontSize = titleSize, lineHeight = titleSize * 1.15f, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GameList(
    games: List<Library.SteamGame>, current: Library.SteamGame,
    onSelect: (Library.SteamGame) -> Unit, onLaunch: (Library.SteamGame) -> Unit, modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Laid out whole, as the art grid is: the pad's focus search only finds rows that exist.
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 10.dp, top = 16.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)) {
            Text("Games", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, maxLines = 1)
            Text(games.size.toString(), fontSize = 13.sp, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        for (g in games) key(g.appId) {
            GameRow(g, g.appId == current.appId, onSelect = { onSelect(g) }, onLaunch = { onLaunch(g) })
        }
    }
}

/** One game in the list. Moving onto it with the pad shows it; A launches it, a tap only shows it. */
@Composable
private fun GameRow(g: Library.SteamGame, selected: Boolean, onSelect: () -> Unit, onLaunch: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val hovered by src.collectIsHoveredAsState()
    LaunchedEffect(focused) { if (focused && !selected) onSelect() }
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().paneItem("game:${g.appId}")
            .clip(Shape12)
            .background(if (selected) pal.signal.copy(alpha = 0.14f) else if (hovered) Color.White.copy(alpha = 0.05f) else Color.Transparent)
            .border(2.dp, if (selected || focused) pal.signal else Color.Transparent, Shape12)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, onClick = onSelect)
            .controllerConfirm(onClick = onLaunch)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(Modifier.width(30.dp).height(45.dp).clip(RoundedCornerShape(5.dp)).background(artBrush(hueOf(g.name)))) {
            if (g.art != null) AsyncImage(model = g.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(g.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                lastPlayedText(g.lastPlayed)?.removePrefix("Last played ")?.replaceFirstChar { it.uppercase() } ?: "Never played",
                fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A page's title, with room at its right for a status chip. */
@Composable
private fun PageHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Text(
            title, fontSize = if (LocalNarrowPane.current) 22.sp else 26.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
        )
        trailing()
    }
}

/** Whether Steam can start, said where Play is rather than only in Setup. */
@Composable
internal fun RuntimeChip(s: FrontEndState) = when {
    s.busy -> Chip(if (s.percent >= 0) "${s.stage} · ${s.percent}%" else s.stage, ok = false)
    !s.ready -> Chip("Runtime installs on first Play", ok = false)
    s.available != null && s.available != s.installed -> Chip("Runtime update available", ok = false)
    else -> Chip("● Runtime ready", ok = true)
}

/** "Last played 3 days ago" from Steam's unix seconds; null for a game never played. */
private fun lastPlayedText(lastPlayed: Long): String? {
    if (lastPlayed <= 0L) return null
    val span = android.text.format.DateUtils.getRelativeTimeSpanString(
        lastPlayed * 1000L, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return "Last played " + span.replaceFirstChar { it.lowercase() }
}

private fun libraryLabel(library: String): String = when (library) {
    "internal" -> "Internal storage"
    "added" -> "Added game"
    else -> library
}

/**
 * A game's wide banner: Steam's hero art where the client cached one, else its capsule blurred to
 * fill the width. The copy sits bottom-left over a scrim of the ground colour so it always reads;
 * the art takes the banner's size, so a banner given a minimum height grows to fit its copy.
 */
@Composable
private fun GameHero(g: Library.SteamGame, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.clip(Shape16).background(artBrush(hueOf(g.name)))) {
        val image = g.hero ?: g.art
        if (image != null) AsyncImage(
            model = image, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
                .then(if (g.hero == null) Modifier.blur(24.dp).graphicsLayer { scaleX = 1.3f; scaleY = 1.3f } else Modifier),
        )
        Spacer(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    0f to colors.background.copy(alpha = 0.92f),
                    0.55f to colors.background.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                ),
            ),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp),
            content = content,
        )
    }
}

@Composable
private fun Chip(t: String, ok: Boolean) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    Text(
        t, fontSize = 12.sp, color = if (ok) pal.good else colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(colors.surfaceVariant).border(1.dp, if (ok) pal.good.copy(alpha = 0.3f) else pal.line, RoundedCornerShape(99.dp)).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun rememberHot(src: MutableInteractionSource): Boolean = src.collectIsFocusedAsState().value || src.collectIsHoveredAsState().value

@Composable
internal fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    main: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    /** The Steam tab's own Play: bigger than any other button on a page. */
    large: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pal = LocalPalette.current
    // The page's main button is where the pane is entered from the rail.
    val frontFocus = if (main) LocalFrontFocus.current else null
    if (frontFocus != null) DisposableEffect(Unit) {
        frontFocus.primaryAttached++
        onDispose { frontFocus.primaryAttached-- }
    }
    val track = (if (frontFocus == null) Modifier.paneItem("btn:$text") else Modifier).downToFirstTile()
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src) && enabled
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.955f else if (hot) 1.02f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "btnScale")
    val lift by animateFloatAsState(if (hot) 14f else 6f, Motion.tw(300), label = "btnLift")
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.then(track)
            .then(if (frontFocus != null) Modifier.focusRequester(frontFocus.primary).onFocusChanged { if (it.isFocused) frontFocus.last = FrontFocus.PRIMARY } else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (enabled) lift.dp.toPx() else 0f; shape = Shape12; clip = false; ambientShadowColor = pal.signal; spotShadowColor = pal.signal }
            .clip(Shape12)
            .background(if (enabled) Brush.linearGradient(listOf(colors.primary, pal.primary2)) else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant)))
            .shine(hot, 0.45f)
            // The grow and shine alone barely show on the light fill: outline it when a
            // controller is on it, as the other controls are.
            .border(2.dp, if (hot) pal.signal else Color.Transparent, Shape12)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .padding(
                start = if (large) 20.dp else if (compact) 10.dp else if (icon != null) 14.dp else 18.dp,
                end = if (large) 26.dp else if (compact) 10.dp else 18.dp,
                top = if (large) 16.dp else if (compact) 7.dp else 11.dp,
                bottom = if (large) 16.dp else if (compact) 7.dp else 11.dp,
            ),
    ) {
        val fg = if (enabled) colors.onPrimary else colors.onSurfaceVariant
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (large) 20.dp else 17.dp))
        Text(
            text, fontSize = if (large) 17.sp else if (compact) 12.sp else 15.sp,
            fontWeight = if (large) FontWeight.Bold else FontWeight.SemiBold, letterSpacing = 0.5.sp, color = fg, maxLines = 1,
        )
    }
}

@Composable
internal fun SecondaryButton(text: String, enabled: Boolean = true, compact: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src) && enabled
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.955f else if (hot) 1.02f else 1f, Motion.sp(0.5f, Spring.StiffnessMedium), label = "secScale")
    val pal = LocalPalette.current
    val edge by animateColorAsState(if (hot) pal.signal else pal.line2, Motion.tw(250), label = "secEdge")
    val fill by animateColorAsState(if (hot) pal.signal.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.03f), Motion.tw(250), label = "secFill")
    Box(
        modifier = modifier.paneItem("btn:$text").downToFirstTile().graphicsLayer { scaleX = scale; scaleY = scale }.clip(Shape12).background(fill).border(1.dp, edge, Shape12)
            .alpha(if (enabled) 1f else 0.5f)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .controllerConfirm(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 7.dp else 11.dp),
    ) { Text(text, fontSize = if (compact) 12.sp else 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp, color = colors.onBackground, maxLines = 1) }
}

@Composable
internal fun Cog(size: androidx.compose.ui.unit.Dp = 42.dp, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val hot = rememberHot(src)
    val rot by animateFloatAsState(if (hot) 90f else 0f, Motion.sp(0.55f), label = "cog")
    val pal = LocalPalette.current
    val edge by animateColorAsState(if (hot) pal.signal else pal.line2, Motion.tw(250), label = "cogEdge")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.paneItem("cog").downToFirstTile().size(size).clip(if (size > 48.dp) Shape14 else Shape12).background(Color.White.copy(alpha = 0.03f)).border(1.dp, edge, if (size > 48.dp) Shape14 else Shape12)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick),
    ) { Icon(Icons.Filled.Settings, "Settings", tint = if (hot) pal.signal else colors.onBackground, modifier = Modifier.size(if (size > 48.dp) 20.dp else 18.dp).rotate(rot)) }
}

@Composable
private fun Poster(art: File?, name: String, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, Motion.sp(0.6f, Spring.StiffnessLow)) }
    Box(
        modifier = modifier.padding(start = 16.dp).aspectRatio(2f / 3f)
            .graphicsLayer { alpha = t.value; translationY = (1f - t.value) * 16.dp.toPx(); rotationZ = (1f - t.value) * 2f; scaleX = 0.94f + 0.06f * t.value; scaleY = scaleX; shadowElevation = 22.dp.toPx(); shape = Shape12; clip = false }
            .clip(Shape12).background(artBrush(hueOf(name))),
    ) {
        if (art != null) CoverImage(art, Modifier.fillMaxSize())
        else Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtGrid(tiles: List<Tile>, wide: Boolean = false) {
    val square = tiles.isNotEmpty() && tiles.all { it.art == null && (it.iconRes != null || it.iconBitmap != null) }
    // Big enough to recognise a game by its art and read its name under it; icon tiles are squares.
    val minSize = if (square) 76.dp else if (wide) 120.dp else 96.dp
    val gap = 12.dp
    // Laid out whole, not lazily: the pad's focus search only finds tiles that exist, and a lazy
    // grid composes only the rows on screen, so a press towards the next row bounced back among
    // the visible tiles. A few hundred tiles lay out fine; the page's scroll follows the focused one.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val avail = maxWidth - 8.dp
        val cols = ((avail + gap) / (minSize + gap)).toInt().coerceAtLeast(1)
        val tileWidth = (avail - gap * (cols - 1)) / cols
        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp, start = 4.dp, end = 4.dp)) {
            for (row in tiles.chunked(cols)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().padding(bottom = gap)) {
                    for (t in row) key(t.key) {
                        val src = remember { MutableInteractionSource() }
                        val hot = rememberHot(src)
                        val track = Modifier.paneItem("tile:" + t.key).then(if (t === tiles.first()) Modifier.firstTile() else Modifier)
                        Box(modifier = Modifier.width(tileWidth).zIndex(if (hot) 1f else 0f)) { GameTile(t, wide, square, src, hot, track) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameTile(t: Tile, wide: Boolean, square: Boolean, src: MutableInteractionSource, hot: Boolean, track: Modifier) {
    val colors = MaterialTheme.colorScheme
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else if (hot) 1.04f else 1f, Motion.sp(0.55f, Spring.StiffnessMedium), label = "tileScale")
    val lift by animateFloatAsState(if (hot) -5f else 0f, Motion.sp(0.6f), label = "tileLift")
    val elev by animateFloatAsState(if (hot) 18f else 2f, Motion.tw(300), label = "tileElev")
    val pal = LocalPalette.current
    val ring by animateColorAsState(if (hot) pal.signal else Color.Transparent, Motion.tw(220), label = "tileRing")
    Column(
        modifier = track
            .graphicsLayer { scaleX = scale; scaleY = scale; translationY = lift.dp.toPx(); shadowElevation = elev.dp.toPx(); shape = Shape12; clip = false; ambientShadowColor = if (hot) pal.signal else Color.Black; spotShadowColor = if (hot) pal.signal else Color.Black; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f) }
            .clip(Shape12)
            .background(colors.surface)
            .border(2.5.dp, ring, Shape12)
            .alpha(if (t.dim && !hot) 0.55f else 1f)
            .hoverable(src).clickable(interactionSource = src, indication = LocalIndication.current, onClick = t.onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().shine(hot)) {
            Art(t.art, t.iconRes, t.title, Modifier.fillMaxWidth(), wide, t.iconBitmap)
        }
        if (t.showFooter) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(t.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.sub != null) Text(t.sub, fontSize = 12.sp, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Art for a portrait (2:3) tile. Box art fills it; wide art - a PS3 disc's ICON0, a game's header -
 * is shown whole over a blurred, darkened copy of itself instead of losing its sides to the crop.
 */
@Composable
private fun CoverImage(art: File, modifier: Modifier) {
    var wideArt by remember(art) { mutableStateOf(false) }
    Box(modifier) {
        if (wideArt) {
            AsyncImage(
                model = art, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }.blur(14.dp),
            )
            Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        AsyncImage(
            model = art, contentDescription = null,
            contentScale = if (wideArt) ContentScale.Fit else ContentScale.Crop,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > size.height * 1.1f) wideArt = true
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Art(art: File?, iconRes: Int?, label: String, modifier: Modifier, wide: Boolean = false, iconBitmap: Bitmap? = null) {
    val colors = MaterialTheme.colorScheme
    val ratio = if (art == null && (iconRes != null || iconBitmap != null)) 1f else if (wide) 16f / 9f else 2f / 3f
    Box(modifier = modifier.aspectRatio(ratio).background(if (art == null && iconRes == null && iconBitmap == null) artBrush(hueOf(label)) else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surface)))) {
        when {
            art != null -> if (wide) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else CoverImage(art, Modifier.fillMaxSize())
            iconRes != null -> Image(painterResource(iconRes), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(if (wide) 10.dp else 8.dp))
            iconBitmap != null -> Image(bitmap = iconBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            else -> {
                Spacer(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.align(Alignment.BottomStart).padding(6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

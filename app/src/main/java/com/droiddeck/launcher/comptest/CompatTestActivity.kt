package com.droiddeck.launcher.comptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Built-in Compatibility Test mode — the one-tap GPU/driver report the user runs
 * on the phone. No Steam login required: system checks + already-installed games only.
 *
 * Flow: IDLE (test list) → RUNNING (progress + cancel) → DONE (results + share).
 * Partial results are persisted after every test, so a killed run keeps its report.
 */
class CompatTestActivity : ComponentActivity() {
    private lateinit var vm: CompatTestViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = CompatTestViewModel(this)
        vm.load()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CompatTestScreen(vm)
                }
            }
        }
    }

    override fun onDestroy() {
        vm.cancel()
        super.onDestroy()
    }
}

internal enum class Phase { IDLE, RUNNING, DONE }

internal class RowState(
    val spec: TestSpec,
    var status: String = "pending", // pending|running|<outcome>|cancelled
    var result: TestResult? = null,
)

internal class CompatTestViewModel(private val activity: CompatTestActivity) {
    var phase by mutableStateOf(Phase.IDLE)
    var rows by mutableStateOf<List<RowState>>(emptyList())
    var progress by mutableStateOf(0f)
    var currentLabel by mutableStateOf("")
    var summary by mutableStateOf("")
    var environment by mutableStateOf<EnvironmentInfo?>(null)
    var runId by mutableStateOf("")

    private var runner: CompatTestRunner? = null
    private var runThread: Thread? = null

    fun load() {
        Thread({
            val games = CompatTestGames.listInstalled(activity)
            val specs = CompatTestSuite.build(games)
            val env = CompatTestExecutor.environmentOf(activity)
            activity.runOnUiThread {
                rows = specs.map { RowState(it) }
                environment = env
            }
        }, "comptest-load").start()
    }

    fun start() {
        if (phase == Phase.RUNNING) return
        val specs = rows.map { it.spec }
        if (specs.isEmpty()) return
        runId = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val env = environment ?: CompatTestExecutor.environmentOf(activity)
        val games = CompatTestGames.listInstalled(activity)
        val installedIds = games.map { it.gameId }.toSet()
        val executor = CompatTestExecutor(activity, installedIds) { env }
        val results = ArrayList<TestResult>()
        val r = CompatTestRunner(
            executor = executor,
            telemetry = { CompatTelemetry.snapshot(activity) },
            driver = { env.driverPackage },
            sink = object : CompatTestRunner.ResultsSink {
                override fun onTestFinished(result: TestResult) {
                    // Persist partial results as we go — a killed run keeps its report.
                    val snapshot = synchronized(results) {
                        results.add(result)
                        results.toList()
                    }
                    runCatching {
                        CompatTestExport.writeReport(
                            activity, runId,
                            CompatReportData(
                                generatedAtMs = System.currentTimeMillis(),
                                environment = env, results = snapshot,
                            ),
                        )
                    }
                    activity.runOnUiThread {
                        rows = rows.map {
                            if (it.spec.id == result.specId)
                                it.copy(status = result.outcome.name, result = result)
                            else it
                        }
                    }
                }
            },
        )
        r.listener = object : CompatTestRunner.Listener {
            override fun onTestStarted(spec: TestSpec, index: Int, total: Int) {
                activity.runOnUiThread {
                    currentLabel = spec.label
                    rows = rows.map {
                        if (it.spec.id == spec.id) it.copy(status = "running") else it
                    }
                }
            }
            override fun onTestFinished(result: TestResult, index: Int, total: Int) {}
            override fun onSuiteProgress(done: Int, total: Int) {
                activity.runOnUiThread { progress = done.toFloat() / total.coerceAtLeast(1) }
            }
        }
        runner = r
        phase = Phase.RUNNING
        progress = 0f
        results.clear()
        rows = rows.map { it.copy(status = "pending", result = null) }
        runThread = Thread({
            val finished = r.runAll(specs)
            runCatching {
                CompatTestExport.writeReport(
                    activity, runId,
                    CompatReportData(generatedAtMs = System.currentTimeMillis(), environment = env, results = finished),
                )
            }
            activity.runOnUiThread {
                phase = Phase.DONE
                progress = 1f
                currentLabel = ""
                summary = summarize(finished)
            }
        }, "comptest-run").also { it.start() }
    }

    fun cancel() {
        runner?.cancel()
        runThread?.interrupt()
    }

    fun share() {
        if (runId.isNotEmpty()) runCatching { CompatTestExport.shareLatest(activity, runId) }
    }

    private fun summarize(results: List<TestResult>): String {
        val counts = results.groupingBy { it.outcome }.eachCount()
        return counts.entries.sortedBy { it.key.ordinal }
            .joinToString(" · ") { "${it.key.name}: ${it.value}" }
    }
}

internal fun RowState.copy(status: String = this.status, result: TestResult? = this.result) =
    RowState(spec, status, result)

@Composable
private fun CompatTestScreen(vm: CompatTestViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Compatibility Test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        vm.environment?.let { env ->
            Text("${env.deviceModel} · ${env.soc}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Driver: ${env.driverPackage.name} ${env.driverPackage.version}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        when (vm.phase) {
            Phase.IDLE -> IdleBody(vm)
            Phase.RUNNING -> RunningBody(vm)
            Phase.DONE -> DoneBody(vm)
        }
    }
}

@Composable
private fun IdleBody(vm: CompatTestViewModel) {
    Text("Runs system GPU checks first, then installed games. Missing games are skipped, never fail the run. No Steam login needed.",
        style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    TestList(vm.rows, Modifier.weight(1f))
    Spacer(Modifier.height(12.dp))
    Button(onClick = { vm.start() }, modifier = Modifier.fillMaxWidth(), enabled = vm.rows.isNotEmpty()) {
        Text("Run compatibility test")
    }
}

@Composable
private fun RunningBody(vm: CompatTestViewModel) {
    LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text(if (vm.currentLabel.isNotEmpty()) "Running: ${vm.currentLabel}" else "Starting…",
        style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    TestList(vm.rows, Modifier.weight(1f))
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { vm.cancel() },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
    ) { Text("Cancel") }
}

@Composable
private fun DoneBody(vm: CompatTestViewModel) {
    if (vm.summary.isNotEmpty()) {
        Text(vm.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
    }
    TestList(vm.rows, Modifier.weight(1f))
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.share() }, modifier = Modifier.weight(1f)) { Text("Share report") }
        OutlinedButton(onClick = { vm.start() }, modifier = Modifier.weight(1f)) { Text("Run again") }
    }
    Text("Report saved as JSON + Markdown (share sends both).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TestList(rows: List<RowState>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows, key = { it.spec.id }) { row -> TestRow(row) }
    }
}

@Composable
private fun TestRow(row: RowState) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.spec.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    (if (row.spec.kind == TestKind.SYSTEM_CHECK) "System check" else "Game") +
                        (row.spec.dxvkVariant?.let { " · $it" } ?: "") +
                        " · ${row.spec.timeoutSec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.result?.let { r ->
                    val fps = r.avgFps?.let { " · %.0f fps avg".format(Locale.US, it) } ?: ""
                    val low = r.onePercentLowFps?.let { " (1%% low %.0f)".format(Locale.US, it) } ?: ""
                    Text(r.outcome.name + fps + low, style = MaterialTheme.typography.bodySmall)
                    if (!r.note.isNullOrBlank()) Text(r.note.take(160),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                statusGlyph(row.status),
                color = statusColor(row.status),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun statusGlyph(status: String): String = when (status) {
    "pending" -> "○"
    "running" -> "▶"
    "REACHED_MENU", "REACHED_GAMEPLAY" -> "●"
    "SKIPPED" -> "–"
    else -> "✕"
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "REACHED_MENU", "REACHED_GAMEPLAY" -> Color(0xFF69F0AE)
    "SKIPPED", "pending" -> Color.Gray
    "running" -> Color(0xFF82B1FF)
    else -> Color(0xFFFF8A80)
}

package com.goldlens.analyst

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goldlens.analyst.core.AnalysisEngine
import com.goldlens.analyst.core.AnalysisResult
import com.goldlens.analyst.core.Candle
import com.goldlens.analyst.core.CsvCandleParser
import com.goldlens.analyst.core.DataSourceKind
import com.goldlens.analyst.core.Direction
import com.goldlens.analyst.core.IndicatorValues
import com.goldlens.analyst.core.MarketDataset
import com.goldlens.analyst.core.PriceZone
import com.goldlens.analyst.core.RiskInput
import com.goldlens.analyst.core.RiskResult
import com.goldlens.analyst.core.ZoneSide
import com.goldlens.analyst.core.ZoneStatus
import com.goldlens.analyst.data.GoldLensDatabase
import com.goldlens.analyst.data.JournalEntity
import com.goldlens.analyst.data.SettingEntity
import com.goldlens.analyst.data.toCandle
import com.goldlens.analyst.data.toEntity
import com.goldlens.analyst.data.replaceCandleSet
import com.goldlens.analyst.data.saveAnalysis
import com.goldlens.analyst.overlay.FloatingAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Gold = Color(0xFFD9B45B)
private val Screen = Color(0xFF090C11)
private val Panel = Color(0xFF141922)
private val Muted = Color(0xFF9AA4B3)
private val Green = Color(0xFF40C99A)
private val Red = Color(0xFFFF7777)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldLensScreen(this) }
    }
}

@Composable
private fun GoldLensScreen(context: Context) {
    val prefs = remember(context) { context.getSharedPreferences("goldlens-settings", Context.MODE_PRIVATE) }
    val database = remember(context) { GoldLensDatabase.get(context) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var overlayRunning by remember { mutableStateOf(prefs.getBoolean("overlay_running", false)) }
    var settingsLoaded by remember { mutableStateOf(false) }
    var backendUrl by remember {
        mutableStateOf(run {
            val saved = prefs.getString("backend_url", null)
            if (saved?.contains("onrender.com", ignoreCase = true) == true || saved?.contains("enzonic.dev", ignoreCase = true) == true) {
                prefs.edit().remove("backend_url").apply()
                BuildConfig.DEFAULT_BACKEND_URL
            } else saved ?: BuildConfig.DEFAULT_BACKEND_URL
        })
    }
    var backendState by remember { mutableStateOf(if (backendUrl.isBlank()) "Optional API not configured" else "Checking API…") }
    var backendResponse by remember { mutableStateOf("") }
    var dataset by remember { mutableStateOf<MarketDataset?>(null) }
    var analysis by remember { mutableStateOf<AnalysisResult?>(null) }
    var risk by remember { mutableStateOf<RiskResult?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var timeframe by remember { mutableStateOf("1h") }
    var isBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var riskBalance by remember { mutableStateOf("") }
    var riskPercent by remember { mutableStateOf("1.0") }
    var riskEntry by remember { mutableStateOf("") }
    var riskStop by remember { mutableStateOf("") }
    var riskTarget by remember { mutableStateOf("") }
    var riskContract by remember { mutableStateOf("1.0") }
    val scope = rememberCoroutineScope()

    fun request(path: String, imageUri: Uri? = null) {
        scope.launch {
            isBusy = true
            error = ""
            try {
                val root = backendUrl.trim().trimEnd('/')
                require(root.startsWith("https://") || BuildConfig.DEBUG && root.startsWith("http://")) {
                    "Set a valid HTTPS API URL in Settings. HTTP is debug-only."
                }
                val body = withContext(Dispatchers.IO) {
                    if (imageUri == null) getJson(root + path) else uploadImage(context, root + path, imageUri)
                }
                backendResponse = body
                if (path == "/health") {
                    backendState = if (JSONObject(body).optString("status") == "ok") "Optional API connected" else "API replied; health status unknown"
                }
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
                if (path == "/health") backendState = "API unavailable; offline analysis is unaffected"
            } finally { isBusy = false }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            isBusy = true
            error = ""
            try {
                val content = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalArgumentException("Could not read the selected CSV file.")
                }
                val parsed = withContext(Dispatchers.Default) { CsvCandleParser.parse(content) }
                withContext(Dispatchers.IO) { database.replaceCandleSet(timeframe, parsed.candles) }
                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Imported OHLC CSV" } ?: "Imported OHLC CSV"
                dataset = MarketDataset(parsed.candles, DataSourceKind.IMPORTED_CSV, fileName, timeframe, System.currentTimeMillis())
                analysis = null
                risk = null
                error = if (parsed.skippedRows > 0) "Loaded ${parsed.candles.size} valid candles; skipped ${parsed.skippedRows} invalid/duplicate rows." else "Loaded ${parsed.candles.size} valid candles."
            } catch (e: Exception) {
                error = e.message ?: "CSV import failed."
            } finally { isBusy = false }
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) request("/api/screenshot/analyze", uri)
    }

    fun analyzeOffline() {
        val loaded = dataset ?: run { error = "Import an authorized OHLC CSV before analyzing."; return }
        scope.launch {
            isBusy = true
            error = ""
            try {
                val selected = loaded.copy(timeframe = timeframe)
                val result = withContext(Dispatchers.Default) { AnalysisEngine.analyze(selected) }
                withContext(Dispatchers.IO) { database.saveAnalysis(result.toEntity(), result.zones.map { it.toEntity() }) }
                dataset = selected
                analysis = result
            } catch (e: Exception) { error = e.message ?: "Analysis failed." }
            finally { isBusy = false }
        }
    }

    fun calculateRisk() {
        try {
            risk = RiskInput(
                accountBalance = riskBalance.toDouble(), riskPercent = riskPercent.toDouble(),
                entry = riskEntry.toDouble(), stopLoss = riskStop.toDouble(), takeProfit = riskTarget.toDouble(),
                contractSize = riskContract.toDouble(),
            ).calculate()
            error = ""
        } catch (e: Exception) {
            risk = null
            error = e.message ?: "Enter valid calculator values."
        }
    }

    fun startOverlayService() {
        ContextCompat.startForegroundService(context, Intent(context, FloatingAssistantService::class.java))
        overlayRunning = true
        prefs.edit().putBoolean("overlay_running", true).apply()
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        startOverlayService()
    }
    val overlaySettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        overlayAllowed = Settings.canDrawOverlays(context)
        if (overlayAllowed) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else startOverlayService()
        }
    }
    fun enableOverlay() {
        overlayAllowed = Settings.canDrawOverlays(context)
        if (!overlayAllowed) {
            overlaySettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
        } else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startOverlayService()
    }
    fun disableOverlay() {
        context.stopService(Intent(context, FloatingAssistantService::class.java))
        overlayRunning = false
        prefs.edit().putBoolean("overlay_running", false).apply()
    }

    LaunchedEffect(database) {
        try {
            val savedTimeframe = withContext(Dispatchers.IO) { database.settingDao().get("timeframe") ?: "1h" }
            timeframe = savedTimeframe
            val rows = withContext(Dispatchers.IO) { database.candleDao().latest("XAU/USD", savedTimeframe, 5_000).asReversed() }
            if (rows.isNotEmpty()) {
                val restored = MarketDataset(rows.map { it.toCandle() }, DataSourceKind.IMPORTED_CSV, "Saved local candles", savedTimeframe, System.currentTimeMillis())
                dataset = restored
                analysis = withContext(Dispatchers.Default) { AnalysisEngine.analyze(restored) }
            }
        } catch (e: Exception) {
            error = "Local database could not be read: ${e.message ?: "unknown error"}"
        } finally { settingsLoaded = true }
        if (backendUrl.isNotBlank()) request("/health")
    }
    LaunchedEffect(timeframe, settingsLoaded) {
        if (settingsLoaded) withContext(Dispatchers.IO) { database.settingDao().put(SettingEntity("timeframe", timeframe)) }
    }
    val tabs = listOf("Overview", "Analysis", "S/R Zones", "Risk", "Journal", "Settings")

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, onPrimary = Color(0xFF17130A), secondary = Green,
        background = Screen, surface = Panel, onSurface = Color(0xFFE7EAF0),
        onBackground = Color(0xFFE7EAF0), error = Red,
    )) {
        Scaffold(containerColor = Screen) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("GOLDLENS", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("XAU/USD · Offline-first analysis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(dataset?.let { "${it.sourceLabel} · ${it.timeframe} · ${it.candles.size} candles" } ?: "No market feed configured · no sample prices", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Screen, contentColor = Gold) {
                    tabs.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (error.isNotBlank()) InfoCard(error, if (error.startsWith("Loaded")) Green else Red)
                    when (tab) {
                        0 -> OverviewTab(dataset, analysis, timeframe, isBusy, onImport = { csvPicker.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) }, onTimeframe = { timeframe = it }, onAnalyze = ::analyzeOffline, onRisk = { tab = 3 })
                        1 -> AnalysisTab(analysis, dataset, isBusy, ::analyzeOffline)
                        2 -> ZonesTab(analysis?.zones.orEmpty(), dataset?.candles.orEmpty(), analysis?.price)
                        3 -> RiskTab(riskBalance, { riskBalance = it }, riskPercent, { riskPercent = it }, riskEntry, { riskEntry = it }, riskStop, { riskStop = it }, riskTarget, { riskTarget = it }, riskContract, { riskContract = it }, risk, ::calculateRisk)
                        4 -> JournalTab(database, timeframe)
                        else -> SettingsTab(backendUrl, { backendUrl = it; prefs.edit().putString("backend_url", it).apply(); scope.launch(Dispatchers.IO) { database.settingDao().put(SettingEntity("backend_url", it)) } }, backendState, backendResponse, isBusy, overlayAllowed, overlayRunning, ::enableOverlay, ::disableOverlay, { request("/health") }, { request("/api/market/candles?timeframe=$timeframe&period=5d") }, { request("/api/analysis?timeframe=$timeframe&period=3mo") }, { request("/api/signals") }, { screenshotPicker.launch("image/*") })
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    dataset: MarketDataset?, analysis: AnalysisResult?, timeframe: String, busy: Boolean,
    onImport: () -> Unit, onTimeframe: (String) -> Unit, onAnalyze: () -> Unit, onRisk: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Import OHLC CSV") }
        OutlinedButton(onClick = onAnalyze, enabled = dataset != null && !busy, modifier = Modifier.weight(1f)) { Text("Analyze locally") }
    }
    TimeframePicker(timeframe, onTimeframe)
    if (dataset == null) {
        InfoCard("Import a CSV containing timestamp, open, high, low and close. GoldLens will calculate locally. No invented market values are displayed.", Gold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline analysis ready", color = Gold, fontWeight = FontWeight.Bold)
                Text("Indicators, support/resistance and risk calculations run on this device once you import candle data.", color = Muted)
            }
        }
    } else {
        val latest = dataset.candles.last()
        MetricRow("Last imported close", price(latest.close), "CSV · ${dataset.timeframe}")
        Text("Candles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        CandlestickChart(dataset.candles, analysis?.zones.orEmpty(), analysis?.price)
        if (analysis == null) InfoCard("Candles are loaded locally. Run Analyze locally to calculate indicators and levels.", Gold)
        else SignalSummary(analysis)
        OutlinedButton(onClick = onRisk, modifier = Modifier.fillMaxWidth()) { Text("Open risk calculator") }
    }
}

@Composable
private fun AnalysisTab(analysis: AnalysisResult?, dataset: MarketDataset?, busy: Boolean, onAnalyze: () -> Unit) {
    if (dataset == null) {
        InfoCard("Import historical OHLC candles on Overview to run analysis without a backend.", Gold)
        return
    }
    Button(onClick = onAnalyze, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Calculating…" else "Recalculate from imported candles") }
    if (analysis == null) {
        InfoCard("No analysis result yet. No signal or price has been fabricated.", Muted)
        return
    }
    SignalSummary(analysis)
    val values = analysis.indicators
    if (values != null) {
        Text("Indicators · ${analysis.timeframe}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IndicatorRow("EMA 20 / 50", values.ema20, values.ema50)
        IndicatorRow("EMA 100 / 200", values.ema100, values.ema200)
        IndicatorRow("RSI (14)", values.rsi14)
        IndicatorRow("MACD histogram", values.macdHistogram)
        IndicatorRow("ATR (14)", values.atr14)
        IndicatorRow("Bollinger upper / lower", values.bollingerUpper, values.bollingerLower)
        IndicatorRow("Stochastic %K / %D", values.stochasticK, values.stochasticD)
    }
    analysis.structure?.let { InfoCard("Market structure: ${it.trend}\nSwing highs: ${it.swingHighs.joinToString { p -> price(p) }.ifEmpty { "—" }}\nSwing lows: ${it.swingLows.joinToString { p -> price(p) }.ifEmpty { "—" }}", Gold) }
    Text("Evidence score: ${analysis.score} · not a calibrated probability", color = Muted, style = MaterialTheme.typography.bodySmall)
    analysis.evidence.forEach { BulletLine(it, Green) }
    analysis.conflicts.forEach { BulletLine(it, Red) }
}

@Composable
private fun SignalSummary(result: AnalysisResult) {
    val directionColor = when (result.direction) { Direction.BUY -> Green; Direction.SELL -> Red; Direction.WAIT -> Gold }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(result.direction.name, color = directionColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${result.timeframe} · ${result.dataSource.name}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            result.price?.let { Text("Last candle close: ${price(it)}", style = MaterialTheme.typography.titleMedium) }
            Text(result.reason, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Entry", result.entry, Modifier.weight(1f))
                SmallMetric("Stop", result.stopLoss, Modifier.weight(1f))
                SmallMetric("Target", result.takeProfit, Modifier.weight(1f))
                SmallMetric("R:R", result.rewardRisk, Modifier.weight(1f))
            }
            if (result.timestampMillis != null) Text("Candle time: ${java.time.Instant.ofEpochMilli(result.timestampMillis)}", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ZonesTab(zones: List<PriceZone>, candles: List<Candle>, price: Double?) {
    if (candles.isNotEmpty()) CandlestickChart(candles, zones, price)
    if (zones.isEmpty()) {
        InfoCard("Support and resistance are calculated from confirmed historical swing pivots after a local analysis. Import enough candles; no manual or sample levels are shown.", Gold)
        return
    }
    zones.sortedWith(compareBy<PriceZone> { if (it.side == ZoneSide.SUPPORT) 0 else 1 }.thenBy { kotlin.math.abs((price ?: it.midpoint) - it.midpoint) })
        .forEach { zone ->
            val color = if (zone.side == ZoneSide.SUPPORT) Green else Red
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${zone.strength.uppercase()} ${zone.side.name} · ${zone.timeframe}", color = color, fontWeight = FontWeight.Bold)
                    Text("${price(zone.lower)} – ${price(zone.upper)} · ${zone.touches} separated pivot touches", style = MaterialTheme.typography.bodyMedium)
                    Text("Status: ${zone.status.name.lowercase()} · rejection score ${"%.2f".format(java.util.Locale.US, zone.rejectionScore)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Last confirmed reaction: ${java.time.Instant.ofEpochMilli(zone.lastTouchMillis)}", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
}

@Composable
private fun RiskTab(
    balance: String, onBalance: (String) -> Unit, percent: String, onPercent: (String) -> Unit,
    entry: String, onEntry: (String) -> Unit, stop: String, onStop: (String) -> Unit,
    target: String, onTarget: (String) -> Unit, contract: String, onContract: (String) -> Unit,
    result: RiskResult?, onCalculate: () -> Unit,
) {
    InfoCard("Manual calculator works offline. Contract size is broker-specific; the default 1.0 is only a unit multiplier, not a universal XAU/USD lot specification.", Gold)
    NumericField("Account balance", balance, onBalance)
    NumericField("Risk percent (max 5%)", percent, onPercent)
    NumericField("Entry", entry, onEntry)
    NumericField("Stop loss", stop, onStop)
    NumericField("Take profit", target, onTarget)
    NumericField("Contract size / units per lot", contract, onContract)
    Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth()) { Text("Calculate position size") }
    result?.let {
        MetricRow("Monetary risk", price(it.riskAmount), "${it.riskPercent}% of balance")
        MetricRow("Position size", "${"%.4f".format(java.util.Locale.US, it.positionLots)} lots", "${price(it.riskPerLot)} risk per lot")
        MetricRow("Potential loss / profit", "${price(it.potentialLoss)} / ${price(it.potentialProfit)}", "R:R ${"%.2f".format(java.util.Locale.US, it.rewardRisk)}")
        Text(it.warning, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun JournalTab(database: GoldLensDatabase, timeframe: String) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<JournalEntity>()) }
    var direction by remember { mutableStateOf("BUY") }
    var entry by remember { mutableStateOf("") }
    var stop by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    var journalError by remember { mutableStateOf("") }
    suspend fun reload() { entries = withContext(Dispatchers.IO) { database.journalDao().history(100) } }
    LaunchedEffect(database) { reload() }
    InfoCard("Manual trade notes are stored on this device. GoldLens does not connect to MT5 or execute orders.", Gold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = direction == "BUY", onClick = { direction = "BUY" }, label = { Text("BUY") })
        FilterChip(selected = direction == "SELL", onClick = { direction = "SELL" }, label = { Text("SELL") })
    }
    NumericField("Entry price", entry, { entry = it })
    NumericField("Stop loss (optional)", stop, { stop = it })
    NumericField("Take profit (optional)", target, { target = it })
    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        scope.launch {
            try {
                val entryValue = entry.toDouble().also { require(it.isFinite() && it > 0) { "Entry must be a positive price." } }
                val stopValue = stop.trim().takeIf(String::isNotEmpty)?.toDouble()
                val targetValue = target.trim().takeIf(String::isNotEmpty)?.toDouble()
                require(stopValue == null || stopValue > 0) { "Stop loss must be positive." }
                require(targetValue == null || targetValue > 0) { "Take profit must be positive." }
                withContext(Dispatchers.IO) {
                    database.journalDao().insert(JournalEntity(
                        openedAtMillis = System.currentTimeMillis(), direction = direction,
                        timeframe = timeframe, entry = entryValue, stopLoss = stopValue,
                        takeProfit = targetValue, notes = notes.trim(),
                    ))
                }
                entry = ""; stop = ""; target = ""; notes = ""; journalError = ""
                reload()
            } catch (e: Exception) { journalError = e.message ?: "Could not save journal entry." }
        }
    }, modifier = Modifier.fillMaxWidth()) { Text("Save manual journal entry") }
    if (journalError.isNotBlank()) Text(journalError, color = Red)
    NumericField("Exit price to close selected open entry", exitPrice, { exitPrice = it })
    Text("Recent entries (${entries.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    if (entries.isEmpty()) InfoCard("No journal entries yet.", Muted)
    entries.forEach { item ->
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${item.direction} · ${item.timeframe} · ${price(item.entry)}", color = if (item.direction == "BUY") Green else Red, fontWeight = FontWeight.Bold)
                Text("Stop ${item.stopLoss?.let(::price) ?: "—"} · Target ${item.takeProfit?.let(::price) ?: "—"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (item.notes.isNotBlank()) Text(item.notes)
                val closeDescription = if (item.closedAtMillis == null) "OPEN" else "Closed at ${item.exitPrice?.let(::price) ?: "—"}"
                Text("Opened ${java.time.Instant.ofEpochMilli(item.openedAtMillis)} · $closeDescription", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (item.closedAtMillis == null) TextButton(onClick = {
                    scope.launch {
                        try {
                            val close = exitPrice.toDouble().also { require(it > 0 && it.isFinite()) { "Enter a positive exit price first." } }
                            withContext(Dispatchers.IO) { database.journalDao().close(item.id, System.currentTimeMillis(), close) }
                            exitPrice = ""; journalError = ""; reload()
                        } catch (e: Exception) { journalError = e.message ?: "Could not close entry." }
                    }
                }) { Text("Close using entered exit price") }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    url: String, onUrl: (String) -> Unit, connection: String, response: String, busy: Boolean,
    overlayAllowed: Boolean, overlayRunning: Boolean, onEnableOverlay: () -> Unit, onDisableOverlay: () -> Unit,
    onHealth: () -> Unit, onMarket: () -> Unit, onAnalysis: () -> Unit, onHistory: () -> Unit, onScreenshot: () -> Unit,
) {
    InfoCard("Offline CSV analysis and risk tools do not require this optional API. Enter a real verified HTTPS endpoint only if you have one.", Gold)
    Text("Floating assistant over MT5", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    InfoCard("The overlay permission lets GoldLens draw its draggable bubble and panel over other apps. It cannot read or control MT5, place orders, or capture the screen by itself. You can revoke the permission in Android Settings and stop the service here.", Muted)
    Text("Overlay permission: ${if (overlayAllowed) "Allowed" else "Not granted"} · Service: ${if (overlayRunning) "Running" else "Stopped"}", color = if (overlayAllowed && overlayRunning) Green else Muted)
    if (overlayRunning) OutlinedButton(onClick = onDisableOverlay, modifier = Modifier.fillMaxWidth()) { Text("Stop floating assistant") }
    else Button(onClick = onEnableOverlay, modifier = Modifier.fillMaxWidth()) { Text(if (overlayAllowed) "Start floating assistant" else "Grant permission and start") }
    OutlinedTextField(value = url, onValueChange = onUrl, label = { Text("Optional backend HTTPS URL") }, placeholder = { Text("https://your-api.example") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Text(connection, color = Muted)
    Button(onClick = onHealth, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Test API connection") }
    Text("Optional API actions", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onMarket, enabled = !busy && url.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Market") }
        OutlinedButton(onClick = onAnalysis, enabled = !busy && url.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Server analysis") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onHistory, enabled = !busy && url.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Server history") }
        OutlinedButton(onClick = onScreenshot, enabled = !busy && url.isNotBlank(), modifier = Modifier.weight(1f)) { Text("Upload image") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (response.isNotBlank()) Text(response, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TimeframePicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Timeframe: $selected ▾") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d").forEach { value ->
                DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun CandlestickChart(candles: List<Candle>, zones: List<PriceZone>, lastPrice: Double?) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        if (candles.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("No candle data", color = Muted) }
        } else {
            Canvas(Modifier.fillMaxWidth().height(250.dp).padding(12.dp)) {
                val visible = candles.takeLast(80)
                val lo = visible.minOf(Candle::low)
                val hi = visible.maxOf(Candle::high)
                val span = (hi - lo).coerceAtLeast(1e-9)
                fun y(v: Double) = size.height - ((v - lo) / span * size.height).toFloat()
                zones.forEach { zone ->
                    if (zone.upper >= lo && zone.lower <= hi) {
                        val c = if (zone.side == ZoneSide.SUPPORT) Green else Red
                        val top = y(zone.upper)
                        val bottom = y(zone.lower)
                        drawRect(c.copy(alpha = 0.10f), topLeft = Offset(0f, top), size = Size(size.width, (bottom - top).coerceAtLeast(1f)))
                    }
                }
                val step = size.width / visible.size
                visible.forEachIndexed { index, candle ->
                    val centerX = step * (index + 0.5f)
                    val color = if (candle.close >= candle.open) Green else Red
                    drawLine(color, Offset(centerX, y(candle.high)), Offset(centerX, y(candle.low)), strokeWidth = 1.4f)
                    val top = y(maxOf(candle.open, candle.close))
                    val bottom = y(minOf(candle.open, candle.close))
                    drawRect(color, Offset(centerX - step * 0.28f, top), Size((step * 0.56f).coerceAtLeast(1.2f), (bottom - top).coerceAtLeast(1.5f)))
                }
                if (lastPrice != null && lastPrice in lo..hi) {
                    drawLine(Gold, Offset(0f, y(lastPrice)), Offset(size.width, y(lastPrice)), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)))
                }
                drawRect(Color(0xFF3A414D), size = size, style = Stroke(width = 1f))
            }
        }
    }
}

@Composable
private fun NumericField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
}

@Composable
private fun IndicatorRow(label: String, first: Double?, second: Double? = null) {
    val value = listOfNotNull(first?.let(::price), second?.let(::price)).joinToString(" / ").ifBlank { "warming up" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Muted); Text(value) }
}

@Composable
private fun SmallMetric(label: String, value: Double?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value?.let(::price) ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MetricRow(title: String, value: String, note: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text(title, color = Muted, style = MaterialTheme.typography.bodySmall); Text(note, color = Muted, style = MaterialTheme.typography.labelSmall) }
            Text(value, color = Gold, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(message: String, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(8.dp)) { drawCircle(accent) }
            Text(message, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BulletLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("•", color = color); Text(text, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun price(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

private fun getJson(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 20_000
    connection.readTimeout = 90_000
    connection.requestMethod = "GET"
    connection.useCaches = false
    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
        JSONObject(body).toString(2)
    } finally { connection.disconnect() }
}

private fun uploadImage(context: Context, address: String, uri: Uri): String {
    val boundary = "----GoldLens${UUID.randomUUID()}"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 30_000
    connection.readTimeout = 120_000
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    try {
        connection.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"chart.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            context.contentResolver.openInputStream(uri)?.use { it.copyTo(out) } ?: throw IllegalArgumentException("Cannot read selected image")
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
        return JSONObject(body).toString(2)
    } finally { connection.disconnect() }
}

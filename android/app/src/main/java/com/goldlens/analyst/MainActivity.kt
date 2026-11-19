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
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.goldlens.analyst.core.AnalysisEngine
import com.goldlens.analyst.core.BacktestCosts
import com.goldlens.analyst.core.BacktestEngine
import com.goldlens.analyst.core.BacktestReport
import com.goldlens.analyst.core.AnalysisResult
import com.goldlens.analyst.core.Candle
import com.goldlens.analyst.core.CsvCandleParser
import com.goldlens.analyst.core.DataSourceKind
import com.goldlens.analyst.core.Direction
import com.goldlens.analyst.core.IndicatorValues
import com.goldlens.analyst.core.MarketDataset
import com.goldlens.analyst.core.InstrumentRegistry
import com.goldlens.analyst.core.InstrumentPrice
import com.goldlens.analyst.core.PriceZone
import com.goldlens.analyst.core.RiskInput
import com.goldlens.analyst.core.RiskResult
import com.goldlens.analyst.core.calculate
import com.goldlens.analyst.core.ZoneSide
import com.goldlens.analyst.core.TradeSide
import com.goldlens.analyst.core.ZoneStatus
import com.goldlens.analyst.data.BacktestEntity
import com.goldlens.analyst.data.CsvReportExporter
import com.goldlens.analyst.data.GoldLensDatabase
import com.goldlens.analyst.data.market.MarketDataException
import com.goldlens.analyst.data.market.ProviderMarketSnapshot
import com.goldlens.analyst.data.market.YahooFinanceChartProvider
import com.goldlens.analyst.core.MultiTimeframeAnalyzer
import com.goldlens.analyst.core.MultiTimeframeResult
import com.goldlens.analyst.data.JournalEntity
import com.goldlens.analyst.data.SettingEntity
import com.goldlens.analyst.data.toCandle
import com.goldlens.analyst.data.toDataSourceKind
import com.goldlens.analyst.data.toEntity
import com.goldlens.analyst.data.replaceCandleSet
import com.goldlens.analyst.data.saveAnalysis
import com.goldlens.analyst.overlay.FloatingAssistantService
import com.goldlens.analyst.notifications.MarketSignalScheduler
import com.goldlens.analyst.notifications.MarketSignalWorker
import com.goldlens.analyst.notifications.TelegramNotifier
import com.goldlens.analyst.notifications.TelegramTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private val Gold = Color(0xFFE3C470)
private val Screen = Color(0xFF0B0E13)
private val Panel = Color(0xFF161B22)
private val PanelElevated = Color(0xFF1C2128)
private val Muted = Color(0xFF8B949E)
private val MutedDim = Color(0xFF6E7681)
private val Green = Color(0xFF3FB95F)
private val GreenDim = Color(0xFF2EA043)
private val Red = Color(0xFFD73A49)
private val RedDim = Color(0xFFCB243E)
private val Blue = Color(0xFF79C0FF)
private val SurfaceBorder = Color(0xFF30363D)

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
    val marketDataProvider = remember { YahooFinanceChartProvider() }
    var providerSnapshot by remember { mutableStateOf<ProviderMarketSnapshot?>(null) }
    var multiTimeframe by remember { mutableStateOf<MultiTimeframeResult?>(null) }
    var backtestReport by remember { mutableStateOf<BacktestReport?>(null) }
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
    var instrument by remember { mutableStateOf("XAU/USD") }
    var metalPointSize by remember(instrument) { mutableStateOf(prefs.getString("metal_point_size_$instrument", "") ?: "") }
    val configuredPointSize = metalPointSize.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    var isBusy by remember { mutableStateOf(false) }
    var autoMonitoring by remember { mutableStateOf(prefs.getBoolean(MarketSignalWorker.KEY_ENABLED, false)) }
    var autoStatus by remember { mutableStateOf(prefs.getString(MarketSignalWorker.KEY_LAST_STATUS, "No background scan yet") ?: "No background scan yet") }
    var telegramEnabled by remember { mutableStateOf(prefs.getBoolean(MarketSignalWorker.KEY_TELEGRAM_ENABLED, false)) }
    var telegramChatId by remember { mutableStateOf(prefs.getString(MarketSignalWorker.KEY_TELEGRAM_CHAT, "") ?: "") }
    var telegramStatus by remember { mutableStateOf(prefs.getString(MarketSignalWorker.KEY_TELEGRAM_STATUS, "Telegram is not configured") ?: "Telegram is not configured") }
    var telegramConfigured by remember { mutableStateOf(TelegramTokenStore.hasToken(context) && telegramChatId.isNotBlank()) }
    var telegramBusy by remember { mutableStateOf(false) }
    var exportTable by remember { mutableStateOf("journal") }
    var error by remember { mutableStateOf("") }
    var riskBalance by remember { mutableStateOf("") }
    var riskPercent by remember { mutableStateOf("1.0") }
    var riskSide by remember { mutableStateOf(TradeSide.LONG) }
    var riskEntry by remember { mutableStateOf("") }
    var riskStop by remember { mutableStateOf("") }
    var riskTarget by remember { mutableStateOf("") }
    var riskContract by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun analyzeAndSave(loaded: MarketDataset): Pair<AnalysisResult, MultiTimeframeResult> {
        val (baseResult, mtf) = withContext(Dispatchers.Default) {
            val base = AnalysisEngine.analyze(loaded)
            base to MultiTimeframeAnalyzer.analyze(loaded)
        }
        val result = MultiTimeframeAnalyzer.applyGate(baseResult, mtf)
        withContext(Dispatchers.IO) { database.saveAnalysis(result.toEntity(), result.zones.map { it.toEntity() }) }
        return result to mtf
    }

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
                val fileName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Imported OHLC CSV" } ?: "Imported OHLC CSV"
                val loaded = MarketDataset(parsed.candles, DataSourceKind.IMPORTED_CSV, fileName, timeframe, System.currentTimeMillis(), instrument, parsed.sourcePrecision)
                withContext(Dispatchers.IO) { database.replaceCandleSet(timeframe, parsed.candles, instrument, DataSourceKind.IMPORTED_CSV, fileName, parsed.sourcePrecision) }
                val (result, mtf) = analyzeAndSave(loaded)
                dataset = loaded
                providerSnapshot = null
                analysis = result
                multiTimeframe = mtf
                risk = null
                val importStatus = if (parsed.skippedRows > 0) "Loaded ${parsed.candles.size} valid candles; skipped ${parsed.skippedRows} invalid/duplicate rows." else "Loaded ${parsed.candles.size} valid candles."
                error = "$importStatus Signal: ${result.direction}. ${result.reason}"
            } catch (e: Exception) {
                error = e.message ?: "CSV import failed."
            } finally { isBusy = false }
        }
    }

    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) request("/api/screenshot/analyze", uri)
    }
    val reportExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            isBusy = true
            try {
                val exported = withContext(Dispatchers.IO) {
                    when (exportTable) {
                        "analyses" -> {
                            val rows = database.analysisDao().history(10_000)
                            CsvReportExporter.analyses(rows) to rows.size
                        }
                        "zones" -> {
                            val rows = database.analysisDao().history(10_000)
                            val joined = rows.map { it to database.analysisDao().zones(it.id) }
                            CsvReportExporter.zones(joined) to joined.sumOf { it.second.size }
                        }
                        "backtests" -> {
                            val rows = database.backtestDao().history(10_000)
                            CsvReportExporter.backtests(rows) to rows.size
                        }
                        else -> {
                            val rows = database.journalDao().history(10_000)
                            CsvReportExporter.journal(rows) to rows.size
                        }
                    }
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exported.first) }
                        ?: throw IllegalArgumentException("Could not write the selected CSV destination.")
                }
                error = "Exported ${exported.second} $exportTable records."
            } catch (e: Exception) { error = e.message ?: "CSV export failed." }
            finally { isBusy = false }
        }
    }
    val csvExportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val candlesToExport = dataset?.candles.orEmpty()
        if (uri != null && candlesToExport.isNotEmpty()) scope.launch {
            try {
                val csv = withContext(Dispatchers.Default) { CsvCandleParser.export(candlesToExport) }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
                        ?: throw IllegalArgumentException("Could not write the selected CSV destination.")
                }
                error = "Exported ${candlesToExport.size} candles for $instrument."
            } catch (e: Exception) { error = e.message ?: "CSV export failed." }
        }
    }

    fun analyzeOffline() {
        val loaded = dataset ?: run { error = "Fetch provider candles or import an authorized OHLC CSV before analyzing."; return }
        scope.launch {
            isBusy = true
            error = ""
            try {
                val selected = loaded.copy(timeframe = timeframe, instrument = instrument)
                val (result, mtf) = analyzeAndSave(selected)
                dataset = selected
                analysis = result
                multiTimeframe = mtf
            } catch (e: Exception) { error = e.message ?: "Analysis failed." }
            finally { isBusy = false }
        }
    }

    fun calculateRisk() {
        try {
            risk = RiskInput(
                accountBalance = riskBalance.toDouble(), riskPercent = riskPercent.toDouble(),
                entry = riskEntry.toDouble(), stopLoss = riskStop.toDouble(), takeProfit = riskTarget.toDouble(),
                contractSize = riskContract.toDouble(), side = riskSide,
            ).calculate()
            error = ""
        } catch (e: Exception) {
            risk = null
            error = e.message ?: "Enter valid calculator values."
        }
    }

    fun fetchMarketData() {
        scope.launch {
            isBusy = true
            error = ""
            try {
                val snapshot = withContext(Dispatchers.IO) { marketDataProvider.fetch(instrument, timeframe) }
                val loaded = MarketDataset(snapshot.candles, snapshot.source, snapshot.sourceLabel, timeframe, snapshot.fetchedAtMillis, instrument, snapshot.sourcePrecision)
                withContext(Dispatchers.IO) {
                    database.replaceCandleSet(timeframe, snapshot.candles, instrument, snapshot.source, snapshot.sourceLabel, snapshot.sourcePrecision)
                }
                val (result, mtf) = analyzeAndSave(loaded)
                providerSnapshot = snapshot
                dataset = loaded
                analysis = result
                multiTimeframe = mtf
                val candleAt = snapshot.candles.lastOrNull()?.timestampMillis?.let { java.time.Instant.ofEpochMilli(it).toString() } ?: "unknown time"
                error = "Fetched ${snapshot.candles.size} historical provider candles; signal ${result.direction} for $instrument · $timeframe · candle $candleAt. ${result.reason} ${snapshot.note}"
            } catch (e: Exception) {
                providerSnapshot = null
                error = e.message ?: "Market data unavailable; retry later or import a CSV."
            } finally { isBusy = false }
        }
    }

    fun runBacktest(costs: BacktestCosts) {
        val loaded = dataset ?: run { error = "Load imported or provider candles before backtesting."; return }
        scope.launch {
            isBusy = true
            error = ""
            try {
                val report = withContext(Dispatchers.Default) { BacktestEngine.run(loaded, costs) }
                withContext(Dispatchers.IO) {
                    val pf = report.full.profitFactor?.let { if (it.isFinite()) it else "infinite" }
                    val json = JSONObject()
                        .put("split_index", report.splitIndex)
                        .put("full_trades", report.full.totalTrades)
                        .put("full_win_rate_percent", report.full.winRatePercent)
                        .put("full_profit_factor", pf)
                        .put("full_net_pnl_per_unit", report.full.netPnlPerUnit)
                        .put("out_of_sample_trades", report.outOfSample.totalTrades)
                        .put("out_of_sample_net_pnl_per_unit", report.outOfSample.netPnlPerUnit)
                        .put("assumptions", org.json.JSONArray(report.assumptions))
                    database.backtestDao().insert(BacktestEntity(
                        createdAtMillis = System.currentTimeMillis(),
                        strategy = "EMA 20/50 cross · ATR exits",
                        timeframe = report.timeframe,
                        source = report.sourceLabel,
                        trades = report.full.totalTrades,
                        winRate = report.full.winRatePercent,
                        profitFactor = report.full.profitFactor?.takeIf { it.isFinite() },
                        maxDrawdown = report.full.maxDrawdownPerUnit,
                        netResult = report.full.netPnlPerUnit,
                        reportJson = json.toString(),
                    ))
                }
                backtestReport = report
                error = "Backtest finished for ${report.instrument} · ${report.barCount} bars · historical simulation only."
            } catch (e: Exception) { error = e.message ?: "Backtest failed." }
            finally { isBusy = false }
        }
    }

    fun startOverlayService() {
        prefs.edit().putString("analysis_symbol", instrument).putString("timeframe", timeframe).apply()
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

    val scheduleMarketMonitoring = {
        prefs.edit().putBoolean(MarketSignalWorker.KEY_ENABLED, true).apply()
        MarketSignalScheduler.start(context)
        autoMonitoring = true
        autoStatus = "Scheduled every 15 minutes for all supported instruments · timeframe $timeframe. Android may defer background work."
    }
    val autoNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scheduleMarketMonitoring()
        if (!granted) autoStatus = "Background scans are scheduled, but Android notifications are disabled. Telegram delivery can still work."
    }
    fun startMarketMonitoring() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            autoNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else scheduleMarketMonitoring()
    }
    fun stopMarketMonitoring() {
        prefs.edit().putBoolean(MarketSignalWorker.KEY_ENABLED, false).apply()
        MarketSignalScheduler.stop(context)
        autoMonitoring = false
        autoStatus = "Background market monitoring is stopped."
    }
    fun refreshAutoStatus() {
        autoStatus = prefs.getString(MarketSignalWorker.KEY_LAST_STATUS, "No background scan yet") ?: "No background scan yet"
        telegramStatus = prefs.getString(MarketSignalWorker.KEY_TELEGRAM_STATUS, "Telegram is not configured") ?: "Telegram is not configured"
    }
    fun saveTelegramSettings(tokenInput: String, chatIdInput: String, enabled: Boolean): String {
        return try {
            val cleanToken = tokenInput.trim()
            val cleanChatId = chatIdInput.trim()
            if (cleanToken.isNotEmpty()) TelegramTokenStore.save(context, cleanToken)
            val hasToken = TelegramTokenStore.hasToken(context)
            require(!enabled || hasToken) { "Enter a bot token before enabling Telegram signals." }
            require(!enabled || cleanChatId.isNotBlank()) { "Enter the Telegram chat ID before enabling Telegram signals." }
            prefs.edit()
                .putString(MarketSignalWorker.KEY_TELEGRAM_CHAT, cleanChatId)
                .putBoolean(MarketSignalWorker.KEY_TELEGRAM_ENABLED, enabled)
                .apply()
            telegramChatId = cleanChatId
            telegramEnabled = enabled
            telegramConfigured = hasToken && cleanChatId.isNotBlank()
            telegramStatus = if (enabled) "Telegram is configured; only new BUY/SELL signals will be sent." else "Telegram signal delivery is off."
            prefs.edit().putString(MarketSignalWorker.KEY_TELEGRAM_STATUS, telegramStatus).apply()
            "Saved. $telegramStatus"
        } catch (e: Exception) {
            e.message ?: "Could not save Telegram settings."
        }
    }
    fun testTelegram(tokenInput: String, chatIdInput: String) {
        scope.launch {
            telegramBusy = true
            telegramStatus = "Sending Telegram test message…"
            try {
                val token = tokenInput.trim().takeIf(String::isNotEmpty)?.also { TelegramTokenStore.save(context, it) }
                    ?: TelegramTokenStore.load(context)
                    ?: throw IllegalArgumentException("Enter and save your bot token first.")
                val chat = chatIdInput.trim().ifBlank { telegramChatId }
                require(chat.isNotBlank()) { "Enter the Telegram chat ID first." }
                withContext(Dispatchers.IO) {
                    TelegramNotifier.sendMessage(token, chat, "GoldLens Telegram test: connection works. Auto alerts will include new BUY/SELL signals only.")
                }
                prefs.edit().putString(MarketSignalWorker.KEY_TELEGRAM_CHAT, chat).apply()
                telegramChatId = chat
                telegramConfigured = true
                telegramStatus = "Telegram test message sent successfully. Save settings to enable automatic alerts."
            } catch (e: Exception) {
                telegramStatus = e.message ?: "Telegram test failed; check the token, chat ID, and connection."
            } finally { telegramBusy = false }
        }
    }
    fun findTelegramChat(tokenInput: String) {
        scope.launch {
            telegramBusy = true
            telegramStatus = "Finding the most recent chat for your bot…"
            try {
                val token = tokenInput.trim().takeIf(String::isNotEmpty)?.also { TelegramTokenStore.save(context, it) }
                    ?: TelegramTokenStore.load(context)
                    ?: throw IllegalArgumentException("Enter the bot token first.")
                val chat = withContext(Dispatchers.IO) { TelegramNotifier.findLatestChatId(token) }
                telegramChatId = chat
                prefs.edit().putString(MarketSignalWorker.KEY_TELEGRAM_CHAT, chat).apply()
                telegramConfigured = true
                telegramStatus = "Found chat ID $chat. Save settings, then send a test message."
            } catch (e: Exception) {
                telegramStatus = e.message ?: "Could not find a Telegram chat."
            } finally { telegramBusy = false }
        }
    }

    LaunchedEffect(Unit) {
        // WorkManager work can be dropped by force-stop/reboot handling; re-queue the periodic
        // scan (without an immediate run) whenever the user previously enabled monitoring.
        if (prefs.getBoolean(MarketSignalWorker.KEY_ENABLED, false)) {
            runCatching { MarketSignalScheduler.resumePeriodic(context) }
        }
    }
    LaunchedEffect(database) {
        try {
            val settings = withContext(Dispatchers.IO) {
                (database.settingDao().get("timeframe") ?: "1h") to (database.settingDao().get("instrument") ?: "XAU/USD")
            }
            val savedTimeframe = settings.first
            val savedInstrument = InstrumentRegistry.find(settings.second)?.symbol ?: "XAU/USD"
            timeframe = savedTimeframe
            instrument = savedInstrument
            val rows = withContext(Dispatchers.IO) { database.candleDao().latest(savedInstrument, savedTimeframe, 5_000).asReversed() }
            if (rows.isNotEmpty()) {
                val restored = MarketDataset(rows.map { it.toCandle() }, rows.first().toDataSourceKind(), rows.first().sourceLabel, savedTimeframe, System.currentTimeMillis(), savedInstrument, rows.first().sourcePrecision)
                dataset = restored
                analysis = withContext(Dispatchers.Default) { AnalysisEngine.analyze(restored) }
            }
        } catch (e: Exception) {
            error = "Local database could not be read: ${e.message ?: "unknown error"}"
        } finally { settingsLoaded = true }
        if (backendUrl.isNotBlank()) request("/health")
    }
    LaunchedEffect(timeframe, settingsLoaded) {
        if (settingsLoaded) {
            prefs.edit().putString("timeframe", timeframe).apply()
            withContext(Dispatchers.IO) { database.settingDao().put(SettingEntity("timeframe", timeframe)) }
        }
    }
    LaunchedEffect(instrument, settingsLoaded) {
        if (settingsLoaded) {
            prefs.edit().putString("analysis_symbol", instrument).apply()
            withContext(Dispatchers.IO) { database.settingDao().put(SettingEntity("instrument", instrument)) }
        }
    }
    val tabs = listOf("Overview", "Signals", "Learn", "Analysis", "S/R Zones", "Backtest", "Exports", "Risk", "Journal", "Settings")

        MaterialTheme(colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Color(0xFF17130A), secondary = Green,
            background = Screen, surface = Panel, onSurface = Color(0xFFE7EAF0),
            onBackground = Color(0xFFE7EAF0), error = Red,
            surfaceVariant = PanelElevated,
        )) {
        Scaffold(containerColor = Screen) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("GOLDLENS", color = Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("$instrument · Offline-first analysis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(dataset?.let { "${it.sourceLabel} · ${it.timeframe} · ${it.candles.size} candles" } ?: "No market feed configured · no sample prices", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = Screen, contentColor = Gold) {
                    tabs.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
                }
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (error.isNotBlank()) InfoCard(error, if (error.startsWith("Loaded") || error.startsWith("Exported") || error.startsWith("Fetched")) Green else Red)
                    when (tab) {
                        0 -> OverviewTab(dataset, analysis, timeframe, instrument, providerSnapshot, isBusy, configuredPointSize,
                            onImport = { csvPicker.launch(arrayOf("text/*", "text/csv", "application/vnd.ms-excel")) },
                            onFetchMarket = ::fetchMarketData,
                            onTimeframe = { nextTimeframe ->
                                timeframe = nextTimeframe
                                dataset = null
                                analysis = null
                                providerSnapshot = null
                                multiTimeframe = null
                                scope.launch {
                                    val rows = withContext(Dispatchers.IO) { database.candleDao().latest(instrument, nextTimeframe, 5_000).asReversed() }
                                    dataset = if (rows.isEmpty()) null else MarketDataset(rows.map { it.toCandle() }, rows.first().toDataSourceKind(), rows.first().sourceLabel, nextTimeframe, System.currentTimeMillis(), instrument, rows.first().sourcePrecision)
                                }
                            },
                            onInstrument = { next ->
                                instrument = next
                                dataset = null
                                analysis = null
                                providerSnapshot = null
                                multiTimeframe = null
                                scope.launch {
                                    val rows = withContext(Dispatchers.IO) { database.candleDao().latest(next, timeframe, 5_000).asReversed() }
                                    dataset = if (rows.isEmpty()) null else MarketDataset(rows.map { it.toCandle() }, rows.first().toDataSourceKind(), rows.first().sourceLabel, timeframe, System.currentTimeMillis(), next, rows.first().sourcePrecision)
                                }
                            },
                            onExport = { csvExportPicker.launch("goldlens-${instrument.replace('/', '-')}-${timeframe}.csv") },
                            onAnalyze = ::analyzeOffline, onRisk = { tab = 6 })
                        1 -> SignalsTab(analysis, dataset, configuredPointSize, telegramConfigured, telegramEnabled, telegramStatus)
                        2 -> LearnTab()
                        3 -> AnalysisTab(analysis, dataset, isBusy, multiTimeframe, configuredPointSize, ::analyzeOffline)
                        4 -> ZonesTab(analysis?.zones.orEmpty(), dataset?.candles.orEmpty(), analysis?.price, instrument, dataset?.sourcePrecision)
                        5 -> BacktestTab(dataset, backtestReport, isBusy, ::runBacktest)
                        6 -> ExportsTab(isBusy) { table ->
                            exportTable = table
                            reportExportPicker.launch("goldlens-$table.csv")
                        }
                        7 -> RiskTab(riskBalance, { riskBalance = it }, riskPercent, { riskPercent = it }, riskSide, { riskSide = it }, riskEntry, { riskEntry = it }, riskStop, { riskStop = it }, riskTarget, { riskTarget = it }, riskContract, { riskContract = it }, risk, ::calculateRisk, instrument, configuredPointSize)
                        8 -> JournalTab(database, timeframe, instrument, configuredPointSize)
                        else -> SettingsTab(
                            instrument = instrument,
                            metalPointSize = metalPointSize,
                            onMetalPointSize = { metalPointSize = it; prefs.edit().putString("metal_point_size_$instrument", it).apply() },
                            url = backendUrl,
                            onUrl = { backendUrl = it; prefs.edit().putString("backend_url", it).apply(); scope.launch(Dispatchers.IO) { database.settingDao().put(SettingEntity("backend_url", it)) } },
                            connection = backendState,
                            response = backendResponse,
                            busy = isBusy,
                            overlayAllowed = overlayAllowed,
                            overlayRunning = overlayRunning,
                            onEnableOverlay = ::enableOverlay,
                            onDisableOverlay = ::disableOverlay,
                            onHealth = { request("/health") },
                            onMarket = { request("/api/market/candles?timeframe=$timeframe&period=5d") },
                            onAnalysis = { request("/api/analysis?timeframe=$timeframe&period=3mo") },
                            onHistory = { request("/api/signals") },
                            onScreenshot = { screenshotPicker.launch("image/*") },
                            autoMonitoring = autoMonitoring,
                            autoStatus = autoStatus,
                            onStartMonitoring = ::startMarketMonitoring,
                            onStopMonitoring = ::stopMarketMonitoring,
                            onRefreshAutoStatus = ::refreshAutoStatus,
                            telegramConfigured = telegramConfigured,
                            telegramEnabled = telegramEnabled,
                            telegramChatId = telegramChatId,
                            telegramStatus = telegramStatus,
                            telegramBusy = telegramBusy,
                            onSaveTelegram = ::saveTelegramSettings,
                            onTestTelegram = ::testTelegram,
                            onFindTelegramChat = ::findTelegramChat,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    dataset: MarketDataset?, analysis: AnalysisResult?, timeframe: String, instrument: String,
    providerSnapshot: ProviderMarketSnapshot?, busy: Boolean, configuredPointSize: Double?,
    onImport: () -> Unit, onFetchMarket: () -> Unit, onTimeframe: (String) -> Unit, onInstrument: (String) -> Unit,
    onExport: () -> Unit, onAnalyze: () -> Unit, onRisk: () -> Unit,
) {
    InstrumentPicker(instrument, onInstrument)
        OutlinedButton(onClick = onFetchMarket, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Fetching market data…" else "Fetch provider candles · market data (unofficial)")
        }
    providerSnapshot?.let { snapshot ->
        InfoCard(
            "${snapshot.sourceLabel}\n${snapshot.instrument} · ${snapshot.timeframe} · ${snapshot.candles.size} candles\nLast candle close: ${snapshot.candles.lastOrNull()?.let { formatPrice(it.close, snapshot.instrument, snapshot.sourcePrecision) } ?: "Unavailable"}\nProvider reference: ${snapshot.providerReferencePrice?.let { formatPrice(it, snapshot.instrument, snapshot.sourcePrecision) } ?: "Unavailable"}\nBid / ask: ${snapshot.bid?.let { formatPrice(it, snapshot.instrument, snapshot.sourcePrecision) } ?: "Unavailable"} / ${snapshot.ask?.let { formatPrice(it, snapshot.instrument, snapshot.sourcePrecision) } ?: "Unavailable"}\nLast candle timestamp: ${snapshot.candles.lastOrNull()?.let { java.time.Instant.ofEpochMilli(it.timestampMillis) } ?: "Unavailable"}\nProvider reference timestamp: ${snapshot.providerPriceTimestampMillis?.let { java.time.Instant.ofEpochMilli(it) } ?: "Unavailable"}\nFetched at: ${java.time.Instant.ofEpochMilli(snapshot.fetchedAtMillis)}\n${snapshot.note}",
            Gold,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onImport, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Import OHLC CSV") }
        OutlinedButton(onClick = onAnalyze, enabled = dataset != null && !busy, modifier = Modifier.weight(1f)) { Text("Analyze locally") }
    }
    TimeframePicker(timeframe, onTimeframe)
    if (dataset != null) OutlinedButton(onClick = onExport, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Export ${dataset.candles.size} candles as CSV") }
    if (dataset == null) {
        InfoCard("Fetch provider candles or import a CSV containing timestamp, open, high, low and close. GoldLens calculates locally; no invented market values are displayed.", Gold)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline analysis ready", color = Gold, fontWeight = FontWeight.Bold)
                Text("Indicators, support/resistance, backtesting and risk calculations run on this device after provider candles are fetched or a candle CSV is imported.", color = Muted)
            }
        }
    } else {
        val latest = dataset.candles.last()
        val previous = dataset.candles.dropLast(1).lastOrNull()
        val currentPrice = providerSnapshot?.providerReferencePrice ?: latest.close
        MetricRow("Current price", formatPrice(currentPrice, dataset.instrument, dataset.sourcePrecision), if (providerSnapshot?.providerReferencePrice != null) "Provider reference · not an executable quote" else "Latest candle close")
        MetricRow("Last candle close", formatPrice(latest.close, dataset.instrument, dataset.sourcePrecision), "${dataset.source.name} · ${dataset.timeframe}")
        Text("Movement since prior close: ${previous?.let { InstrumentPrice.movement(dataset.instrument, it.close, latest.close, dataset.sourcePrecision, configuredPointSize) } ?: "Need at least two candles"}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Source: ${dataset.sourceLabel} · candle timestamp ${java.time.Instant.ofEpochMilli(latest.timestampMillis)} · age ${candleAge(latest.timestampMillis)}", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Candles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        CandlestickChart(dataset.candles, analysis?.zones.orEmpty(), analysis?.price)
        if (analysis == null) InfoCard("Candles are loaded locally. Run Analyze locally to calculate indicators and levels.", Gold)
        else SignalSummary(analysis, dataset, configuredPointSize)
        OutlinedButton(onClick = onRisk, modifier = Modifier.fillMaxWidth()) { Text("Open risk calculator") }
    }
}

@Composable
private fun SignalsTab(
    analysis: AnalysisResult?,
    dataset: MarketDataset?,
    configuredPointSize: Double?,
    telegramConfigured: Boolean,
    telegramEnabled: Boolean,
    telegramStatus: String,
) {
    val isSafe = analysis != null &&
        analysis.direction != Direction.WAIT &&
        analysis.rewardRisk != null && analysis.rewardRisk >= 2.0 &&
        analysis.score >= 3 && analysis.conflicts.isEmpty()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Signals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold)
        Text("All supported instruments appear here. Only the safest setups — R:R ≥ 2.0, evidence score ≥ 3, no conflicting signals — are eligible for Telegram delivery.", color = Muted, style = MaterialTheme.typography.bodySmall)

        if (analysis == null || dataset == null) {
            InfoCard("No signal yet. Fetch provider candles or import an OHLC CSV on Overview, then run Analyze locally.", Gold)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dataset.instrument, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Gold)
                        Text(dataset.timeframe, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    val directionColor = when (analysis.direction) { Direction.BUY -> Green; Direction.SELL -> Red; Direction.WAIT -> Gold }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(analysis.direction.name, color = directionColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val safeBadge = if (isSafe) "SAFE FOR TELEGRAM" else "NOT TELEGRAM-SAFE"
                        Text(safeBadge, color = if (isSafe) Green else Red, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(analysis.reason, color = Muted, style = MaterialTheme.typography.bodyMedium)

                    Text("Why this market was chosen", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    analysis.evidence.take(5).forEach { BulletLine(it, Green) }
                    if (analysis.conflicts.isNotEmpty()) {
                        Text("Cautions", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        analysis.conflicts.take(5).forEach { BulletLine(it, Red) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallMetric("Entry", analysis.entry, dataset.instrument, dataset.sourcePrecision, Modifier.weight(1f))
                        SmallMetric("Stop", analysis.stopLoss, dataset.instrument, dataset.sourcePrecision, Modifier.weight(1f))
                        SmallMetric("Target", analysis.takeProfit, dataset.instrument, dataset.sourcePrecision, Modifier.weight(1f))
                        SmallMetric("R:R", analysis.rewardRisk, dataset.instrument, dataset.sourcePrecision, Modifier.weight(1f), priceValue = false)
                    }
                    Text("Evidence score ${analysis.score}; not a probability or a guarantee. This is analysis, not an order.", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text("Candle time: ${java.time.Instant.ofEpochMilli(analysis.timestampMillis ?: 0L)}", color = Muted, style = MaterialTheme.typography.labelSmall)

                    if (!telegramConfigured || !telegramEnabled) {
                        InfoCard("Telegram is not configured or disabled. Open Settings to add your bot token and chat ID. Only safe BUY/SELL signals will be sent.", Muted)
                    } else if (!isSafe) {
                        InfoCard("This signal is held back from Telegram — it does not meet the safety threshold (R:R ≥ 2.0, score ≥ 3, no conflicts). $telegramStatus", Muted)
                    } else {
                        Text("This signal qualifies for Telegram delivery.", color = Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("All supported instruments", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        InstrumentRegistry.instruments.forEach { inst ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(inst.symbol, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color(0xFFE7EAF0))
                    Text(inst.displayName, style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Text(if (inst.symbol == dataset?.instrument) analysis?.direction?.name ?: "—" else "—", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Divider()
        }
    }
}

@Composable
private fun LearnTab() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Forex Tutoring", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold)
        Text("Learn the fundamentals of foreign exchange trading and risk management. All content is educational, not financial advice.", color = Muted, style = MaterialTheme.typography.bodySmall)

        LessonCard(
            title = "What is Forex?",
            body = "Forex (foreign exchange) is the global market for trading currencies. You buy one currency while selling another, always in pairs such as EUR/USD or XAU/USD. Price moves reflect supply, demand, interest rates, and economic news. GoldLens reads historical candles and calculates signals locally — it does not execute trades.",
        )
        LessonCard(
            title = "Reading Candlesticks",
            body = "Each candle shows open, high, low, and close for a time period. A green (bullish) candle means close ≥ open; red (bearish) means close < open. The wicks show the high and low reached. GoldLens draws candles on the Overview and S/R Zones tabs using only confirmed historical data.",
        )
        LessonCard(
            title = "Support and Resistance",
            body = "Support is a price level where buying interest tends to stop a decline. Resistance is where selling pressure tends to stop a rally. GoldLens calculates S/R zones from confirmed swing pivots and highlights them as shaded bands on the chart. Zones with more touches are stronger.",
        )
        LessonCard(
            title = "Risk Management Rules",
            body = "1. Risk no more than 1–2% of your account per trade (GoldLens caps the calculator at 5%).\n2. Always set a stop loss before entering.\n3. Make your target at least 2× your stop distance (R:R ≥ 2.0).\n4. Never add to a losing position.\n5. Use the Risk tab to calculate position size before every trade.",
        )
        LessonCard(
            title = "Understanding Signals",
            body = "GoldLens labels each analysis BUY, SELL, or WAIT. BUY/SELL means the indicators align in that direction; WAIT means evidence is mixed or insufficient. Only new direction changes trigger notifications. Telegram receives only signals with R:R ≥ 2.0, evidence score ≥ 3, and no conflicts.",
        )
        LessonCard(
            title = "Important Disclaimers",
            body = "GoldLens uses historical/reference market data from an unofficial provider. Data may be delayed or rate-limited. Signals are not broker quotes, not executable orders, and not financial advice. Past performance does not predict future results. You alone are responsible for your trading decisions.",
        )
    }
}

@Composable
private fun LessonCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AnalysisTab(analysis: AnalysisResult?, dataset: MarketDataset?, busy: Boolean, mtf: MultiTimeframeResult?, configuredPointSize: Double?, onAnalyze: () -> Unit) {
    if (dataset == null) {
        InfoCard("Fetch historical provider candles or import OHLC on Overview to run local analysis without a backend.", Gold)
        return
    }
    Button(onClick = onAnalyze, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Calculating…" else "Recalculate locally") }
    if (analysis == null) {
        InfoCard("No analysis result yet. No signal or price has been fabricated.", Muted)
        return
    }
    SignalSummary(analysis, dataset, configuredPointSize, showEvidence = false)
    val values = analysis.indicators
    if (values != null) {
        Text("Indicators · ${analysis.timeframe}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IndicatorRow("EMA 20 / 50", values.ema20, values.ema50, dataset.instrument, dataset.sourcePrecision, priceLike = true)
        IndicatorRow("EMA 100 / 200", values.ema100, values.ema200, dataset.instrument, dataset.sourcePrecision, priceLike = true)
        IndicatorRow("RSI (14)", values.rsi14)
        IndicatorRow("MACD histogram", values.macdHistogram, instrument = dataset.instrument, sourcePrecision = dataset.sourcePrecision, priceLike = true)
        IndicatorRow("ATR (14)", values.atr14, instrument = dataset.instrument, sourcePrecision = dataset.sourcePrecision, priceLike = true)
        IndicatorRow("Bollinger upper / lower", values.bollingerUpper, values.bollingerLower, dataset.instrument, dataset.sourcePrecision, priceLike = true)
        IndicatorRow("Stochastic %K / %D", values.stochasticK, values.stochasticD)
    }
    analysis.structure?.let { InfoCard("Market structure: ${it.trend}\nSwing highs: ${it.swingHighs.joinToString { p -> formatPrice(p, dataset.instrument, dataset.sourcePrecision) }.ifEmpty { "—" }}\nSwing lows: ${it.swingLows.joinToString { p -> formatPrice(p, dataset.instrument, dataset.sourcePrecision) }.ifEmpty { "—" }}", Gold) }
    Text("Evidence score: ${analysis.score} · not a calibrated probability", color = Muted, style = MaterialTheme.typography.bodySmall)
    analysis.evidence.forEach { BulletLine(it, Green) }
    analysis.conflicts.forEach { BulletLine(it, Red) }
    mtf?.let { summary ->
        Text("Multi-timeframe · ${summary.agreement}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(summary.explanation, color = Muted, style = MaterialTheme.typography.bodySmall)
        summary.assessments.forEach { assessment ->
            val item = assessment.result
            InfoCard(
                "${assessment.timeframe} · ${item?.direction?.name ?: "UNAVAILABLE"} · ${assessment.candleCount} complete candles\n${item?.structure?.trend ?: assessment.status}\nS/R zones: ${item?.zones?.size ?: 0}",
                when (item?.direction) { Direction.BUY -> Green; Direction.SELL -> Red; else -> Muted },
            )
        }
    }
}

@Composable
private fun SignalSummary(result: AnalysisResult, dataset: MarketDataset?, configuredPointSize: Double?, showEvidence: Boolean = true) {
    val directionColor = when (result.direction) { Direction.BUY -> Green; Direction.SELL -> Red; Direction.WAIT -> Gold }
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(result.direction.name, color = directionColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column {
                    Text(result.direction.name, color = directionColor, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${result.instrument} · ${result.timeframe} · ${result.dataSource.name}", color = MutedDim, style = MaterialTheme.typography.bodySmall)
                }
            }
            result.price?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Close", color = MutedDim, style = MaterialTheme.typography.labelSmall)
                    Text(formatPrice(it, result.instrument, dataset?.sourcePrecision), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }
            dataset?.let {
                Text("Source: ${it.sourceLabel} · candle age ${result.timestampMillis?.let(::candleAge) ?: "unknown"}", color = MutedDim, style = MaterialTheme.typography.bodySmall)
            }
            Text(result.reason, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallMetric("Entry", result.entry, result.instrument, dataset?.sourcePrecision, Modifier.weight(1f))
                SmallMetric("Stop", result.stopLoss, result.instrument, dataset?.sourcePrecision, Modifier.weight(1f))
                SmallMetric("Target", result.takeProfit, result.instrument, dataset?.sourcePrecision, Modifier.weight(1f))
                SmallMetric("R:R", result.rewardRisk, result.instrument, dataset?.sourcePrecision, Modifier.weight(1f), priceValue = false)
                if (result.trailingStop != null) {
                    SmallMetric("Trail Stop", result.trailingStop, result.instrument, dataset?.sourcePrecision, Modifier.weight(1f))
                }
            }
            if (result.direction != Direction.WAIT) {
                val stopDistance = InstrumentPrice.unitDistanceText(result.instrument, result.entry, result.stopLoss, configuredPointSize)
                    ?: if (result.entry != null && result.stopLoss != null) "${formatPrice(kotlin.math.abs(result.entry - result.stopLoss), result.instrument, dataset?.sourcePrecision)} price units" else "—"
                val targetDistance = InstrumentPrice.unitDistanceText(result.instrument, result.entry, result.takeProfit, configuredPointSize)
                    ?: if (result.entry != null && result.takeProfit != null) "${formatPrice(kotlin.math.abs(result.takeProfit - result.entry), result.instrument, dataset?.sourcePrecision)} price units" else "—"
                Text("Stop distance: $stopDistance · take-profit distance: $targetDistance", color = MutedDim, style = MaterialTheme.typography.bodySmall)
            }
            if (result.direction != Direction.WAIT) {
                Text("Level method: entry = latest candle close; stop = 1.5 × ATR(14); target = 3 × ATR(14). Trailing stop at recent swing level or 2 × ATR. R:R is theoretical before spread, slippage, commission and broker contract rules.", color = MutedDim, style = MaterialTheme.typography.bodySmall)
            }
            if (showEvidence && result.evidence.isNotEmpty()) {
                Text("Why this signal", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                result.evidence.take(4).forEach { BulletLine(it, Green) }
            }
            if (showEvidence && result.conflicts.isNotEmpty()) {
                Text("Cautions / conflicting evidence", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                result.conflicts.take(4).forEach { BulletLine(it, Gold) }
            }
            Text("Evidence score ${result.score} · not a probability or guarantee · analysis, not an order.", color = MutedDim, style = MaterialTheme.typography.labelSmall)
            if (result.timestampMillis != null) Text("Candle time: ${java.time.Instant.ofEpochMilli(result.timestampMillis)}", color = MutedDim, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ZonesTab(zones: List<PriceZone>, candles: List<Candle>, price: Double?, instrument: String, sourcePrecision: Int?) {
    if (candles.isNotEmpty()) CandlestickChart(candles, zones, price)
    if (zones.isEmpty()) {
        InfoCard("Support and resistance are calculated from confirmed historical swing pivots after a local analysis. Import enough candles; no manual or sample levels are shown.", Gold)
        return
    }
    zones.sortedWith(compareBy<PriceZone> { if (it.side == ZoneSide.SUPPORT) 0 else 1 }.thenBy { kotlin.math.abs((price ?: it.midpoint) - it.midpoint) })
        .forEach { zone ->
            val color = if (zone.side == ZoneSide.SUPPORT) Green else Red
            Card(
                colors = CardDefaults.cardColors(containerColor = PanelElevated),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${zone.strength.uppercase()} ${zone.side.name} · ${zone.timeframe}", color = color, fontWeight = FontWeight.Bold)
                        Text("Status: ${zone.status.name.lowercase()}", color = MutedDim, style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${formatPrice(zone.lower, instrument, sourcePrecision)} – ${formatPrice(zone.upper, instrument, sourcePrecision)} · ${zone.touches} separated pivot touches", style = MaterialTheme.typography.bodyMedium)
                    Text("Rejection score: ${"%.2f".format(java.util.Locale.US, zone.rejectionScore)} · Last reaction: ${java.time.Instant.ofEpochMilli(zone.lastTouchMillis)}", color = MutedDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
}

@Composable
private fun ExportsTab(busy: Boolean, onExport: (String) -> Unit) {
    InfoCard("Export locally stored records as CSV through Android's document picker. Exports stay on your device unless you choose a destination.", Gold)
    listOf(
        "analyses" to "Export analysis signals",
        "zones" to "Export support/resistance zones",
        "journal" to "Export trade journal",
        "backtests" to "Export backtest runs",
    ).forEach { (table, label) ->
        OutlinedButton(onClick = { onExport(table) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun BacktestTab(dataset: MarketDataset?, report: BacktestReport?, busy: Boolean, onRun: (BacktestCosts) -> Unit) {
    var spread by remember { mutableStateOf("") }
    var slippage by remember { mutableStateOf("") }
    var commission by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf("") }
    InfoCard("Offline EMA 20/50 crossover baseline. Enter spread per round trip, plus slippage and commission per side, in price units. A zero must be chosen deliberately; results are not forecasts.", Gold)
    if (dataset == null) InfoCard("Load local CSV or provider OHLC first.", Muted)
    else Text("Data: ${dataset.instrument} · ${dataset.timeframe} · ${dataset.sourceLabel} · ${dataset.candles.size} candles", color = Muted)
    NumericField("Spread per round trip · price units", spread, { spread = it })
    NumericField("Slippage in price units per side", slippage, { slippage = it })
    NumericField("Commission in price units per side", commission, { commission = it })
    if (formError.isNotBlank()) Text(formError, color = Red)
    Button(onClick = {
        try {
            onRun(BacktestCosts(spread.toDouble(), slippage.toDouble(), commission.toDouble()))
            formError = ""
        } catch (e: Exception) { formError = e.message ?: "Enter finite non-negative cost assumptions." }
    }, enabled = dataset != null && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Running backtest…" else "Run local historical backtest") }
    report?.let { result ->
        Text("${result.instrument} · ${result.timeframe} · ${result.barCount} bars · split ${result.splitIndex}/70:30", color = Muted)
        MetricRow("Closed trades", result.full.totalTrades.toString(), "${result.full.wins} wins · ${result.full.losses} losses")
        MetricRow("Win rate", result.full.winRatePercent?.let { "${"%.1f".format(java.util.Locale.US, it)}%" } ?: "Unavailable", "Full history")
        MetricRow("Profit factor", result.full.profitFactor?.let { if (it.isFinite()) "${"%.2f".format(java.util.Locale.US, it)}" else "Infinite (no losses)" } ?: "Unavailable", "Unit price P&L")
        MetricRow("Net · max drawdown", "${formatPrice(result.full.netPnlPerUnit, result.instrument, result.sourcePrecision)} · ${formatPrice(result.full.maxDrawdownPerUnit, result.instrument, result.sourcePrecision)}", "Per unit, not account currency")
        Text("In-sample: ${result.inSample.totalTrades} trades · net ${formatPrice(result.inSample.netPnlPerUnit, result.instrument, result.sourcePrecision)} · PF ${result.inSample.profitFactor?.let { if (it.isFinite()) "${"%.2f".format(java.util.Locale.US, it)}" else "∞" } ?: "—"}", color = Muted)
        Text("Out-of-sample: ${result.outOfSample.totalTrades} trades · net ${formatPrice(result.outOfSample.netPnlPerUnit, result.instrument, result.sourcePrecision)} · PF ${result.outOfSample.profitFactor?.let { if (it.isFinite()) "${"%.2f".format(java.util.Locale.US, it)}" else "∞" } ?: "—"}", color = Muted)
        Text("Equity curve · cumulative closed-trade P&L per unit", style = MaterialTheme.typography.titleSmall)
        BacktestEquityCurve(result.full.equityCurve)
        result.assumptions.forEach { BulletLine(it, Muted) }
    }
}

@Composable
private fun BacktestEquityCurve(values: List<Double>) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        if (values.size < 2) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Text("Equity curve appears after at least two closed trades.", color = Muted, style = MaterialTheme.typography.bodySmall)
        } else Canvas(Modifier.fillMaxWidth().height(150.dp).padding(12.dp)) {
            val low = minOf(0.0, values.minOrNull() ?: 0.0)
            val high = maxOf(0.0, values.maxOrNull() ?: 0.0)
            val span = (high - low).coerceAtLeast(1e-9)
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1).toFloat()
                val y = size.height - ((value - low) / span).toFloat() * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Green, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

@Composable
private fun RiskTab(
    balance: String, onBalance: (String) -> Unit, percent: String, onPercent: (String) -> Unit,
    side: TradeSide, onSide: (TradeSide) -> Unit,
    entry: String, onEntry: (String) -> Unit, stop: String, onStop: (String) -> Unit,
    target: String, onTarget: (String) -> Unit, contract: String, onContract: (String) -> Unit,
    result: RiskResult?, onCalculate: () -> Unit, instrument: String, configuredPointSize: Double?,
) {
    InfoCard("Manual calculator works offline. Enter the contract-size multiplier verified for your broker and instrument. GoldLens does not assume a universal lot size or perform account-currency conversion.", Gold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = side == TradeSide.LONG, onClick = { onSide(TradeSide.LONG) }, label = { Text("LONG / BUY") })
        FilterChip(selected = side == TradeSide.SHORT, onClick = { onSide(TradeSide.SHORT) }, label = { Text("SHORT / SELL") })
    }
    NumericField("Account balance", balance, onBalance)
    NumericField("Risk percent (max 5%)", percent, onPercent)
    NumericField("Entry", entry, onEntry)
    NumericField("Stop loss", stop, onStop)
    NumericField("Take profit", target, onTarget)
    val entryValue = entry.toDoubleOrNull()
    val stopValue = stop.toDoubleOrNull()
    val targetValue = target.toDoubleOrNull()
    if (entryValue != null && stopValue != null) Text("Stop distance: ${InstrumentPrice.unitDistanceText(instrument, entryValue, stopValue, configuredPointSize) ?: "${formatPrice(kotlin.math.abs(entryValue - stopValue), instrument)} price units"}", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (entryValue != null && targetValue != null) Text("Target distance: ${InstrumentPrice.unitDistanceText(instrument, entryValue, targetValue, configuredPointSize) ?: "${formatPrice(kotlin.math.abs(targetValue - entryValue), instrument)} price units"}", color = Muted, style = MaterialTheme.typography.bodySmall)
    NumericField("Contract size / units per lot", contract, onContract)
    Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth()) { Text("Calculate position size") }
    result?.let {
        MetricRow("Monetary risk", money(it.riskAmount), "${it.riskPercent}% of balance")
        MetricRow("Position size", "${"%.4f".format(java.util.Locale.US, it.positionLots)} lots", "${money(it.riskPerLot)} risk per lot")
        MetricRow("Potential loss / profit", "${money(it.potentialLoss)} / ${money(it.potentialProfit)}", "R:R ${"%.2f".format(java.util.Locale.US, it.rewardRisk)}")
        Text(it.warning, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun JournalTab(database: GoldLensDatabase, timeframe: String, instrument: String, configuredPointSize: Double?) {
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
                        openedAtMillis = System.currentTimeMillis(), symbol = instrument, direction = direction,
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
                Text("${item.symbol} · ${item.direction} · ${item.timeframe} · ${formatPrice(item.entry, item.symbol)}", color = if (item.direction == "BUY") Green else Red, fontWeight = FontWeight.Bold)
                Text("Stop ${item.stopLoss?.let { formatPrice(it, item.symbol) } ?: "—"} · Target ${item.takeProfit?.let { formatPrice(it, item.symbol) } ?: "—"}", color = Muted, style = MaterialTheme.typography.bodySmall)
                val distances = listOfNotNull(
                    item.stopLoss?.let { "stop ${InstrumentPrice.unitDistanceText(item.symbol, item.entry, it, configuredPointSize) ?: "${formatPrice(kotlin.math.abs(item.entry - it), item.symbol)} price units"}" },
                    item.takeProfit?.let { "target ${InstrumentPrice.unitDistanceText(item.symbol, item.entry, it, configuredPointSize) ?: "${formatPrice(kotlin.math.abs(it - item.entry), item.symbol)} price units"}" },
                ).joinToString(" · ")
                if (distances.isNotBlank()) Text("Distances: $distances", color = Muted, style = MaterialTheme.typography.labelSmall)
                if (item.notes.isNotBlank()) Text(item.notes)
                val closeDescription = if (item.closedAtMillis == null) "OPEN" else "Closed at ${item.exitPrice?.let { formatPrice(it, item.symbol) } ?: "—"}"
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
    instrument: String, metalPointSize: String, onMetalPointSize: (String) -> Unit,
    url: String, onUrl: (String) -> Unit, connection: String, response: String, busy: Boolean,
    overlayAllowed: Boolean, overlayRunning: Boolean, onEnableOverlay: () -> Unit, onDisableOverlay: () -> Unit,
    onHealth: () -> Unit, onMarket: () -> Unit, onAnalysis: () -> Unit, onHistory: () -> Unit, onScreenshot: () -> Unit,
    autoMonitoring: Boolean, autoStatus: String, onStartMonitoring: () -> Unit, onStopMonitoring: () -> Unit, onRefreshAutoStatus: () -> Unit,
    telegramConfigured: Boolean, telegramEnabled: Boolean, telegramChatId: String, telegramStatus: String, telegramBusy: Boolean,
    onSaveTelegram: (String, String, Boolean) -> String, onTestTelegram: (String, String) -> Unit,
    onFindTelegramChat: (String) -> Unit,
) {
    var telegramTokenInput by remember { mutableStateOf("") }
    var telegramChatInput by remember(telegramChatId) { mutableStateOf(telegramChatId) }
    var telegramToggle by remember(telegramEnabled) { mutableStateOf(telegramEnabled) }
    var telegramFormStatus by remember { mutableStateOf("") }
    InfoCard("Offline CSV analysis and risk tools do not require this optional API. Enter a real verified HTTPS endpoint only if you have one.", Gold)
    if (InstrumentRegistry.find(instrument)?.assetClass == com.goldlens.analyst.core.AssetClass.PRECIOUS_METAL) {
        NumericField("$instrument display point size · optional", metalPointSize, onMetalPointSize)
        Text("Enter quote-price units per display point (for example, 0.01 only if it matches your feed). This setting labels movement; it is not a broker tick specification and does not affect risk sizing.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    Text("Floating assistant over MT5",  style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    InfoCard("The overlay permission lets GoldLens draw its draggable bubble and panel over other apps. It cannot read or control MT5, place orders, or capture the screen by itself. You can revoke the permission in Android Settings and stop the service here.", Muted)
    Text("Overlay permission: ${if (overlayAllowed) "Allowed" else "Not granted"} · Service: ${if (overlayRunning) "Running" else "Stopped"}", color = if (overlayAllowed && overlayRunning) Green else Muted)
    if (overlayRunning) OutlinedButton(onClick = onDisableOverlay, modifier = Modifier.fillMaxWidth()) { Text("Stop floating assistant") }
    else Button(onClick = onEnableOverlay, modifier = Modifier.fillMaxWidth()) { Text(if (overlayAllowed) "Start floating assistant" else "Grant permission and start") }

    Text("Background market monitoring", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    InfoCard("GoldLens checks all ${InstrumentRegistry.instruments.size} supported instruments on the selected $instrument timeframe. WorkManager queues an immediate scan and repeats every 15 minutes when Android permits. Each scan replaces the stored candles for that instrument/timeframe with the latest provider snapshot. Provider data is historical/reference and can be delayed or rate-limited; this is not a real-time or guaranteed alert service.", Gold)
    Text(autoStatus, color = Muted, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (autoMonitoring) OutlinedButton(onClick = onStopMonitoring, modifier = Modifier.weight(1f)) { Text("Stop background scans") }
        else Button(onClick = onStartMonitoring, modifier = Modifier.weight(1f)) { Text("Start background scans") }
        OutlinedButton(onClick = onRefreshAutoStatus, modifier = Modifier.weight(1f)) { Text("Refresh status") }
    }

    Text("Telegram signal delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    InfoCard("Create your own Telegram bot and enter its token here in GoldLens—never paste it into chat. The token is encrypted with Android Keystore on this device. Only new BUY/SELL signals are sent; WAIT results are suppressed. Telegram messages include market-data source caveats and do not place trades.", Muted)
    OutlinedTextField(
        value = telegramTokenInput,
        onValueChange = { telegramTokenInput = it },
        label = { Text(if (telegramConfigured) "Bot token (saved; enter to replace)" else "Telegram bot token") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(value = telegramChatInput, onValueChange = { telegramChatInput = it }, label = { Text("Telegram chat ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Send new BUY/SELL signals to Telegram")
        Switch(checked = telegramToggle, onCheckedChange = { telegramToggle = it })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            telegramFormStatus = onSaveTelegram(telegramTokenInput, telegramChatInput, telegramToggle)
            if (telegramFormStatus.startsWith("Saved")) telegramTokenInput = ""
        }, modifier = Modifier.weight(1f)) { Text("Save Telegram settings") }
        OutlinedButton(onClick = { onTestTelegram(telegramTokenInput, telegramChatInput) }, enabled = !telegramBusy, modifier = Modifier.weight(1f)) { Text(if (telegramBusy) "Sending…" else "Send test message") }
    }
    OutlinedButton(onClick = { onFindTelegramChat(telegramTokenInput) }, enabled = !telegramBusy, modifier = Modifier.fillMaxWidth()) {
        Text("Find chat ID from my bot messages")
    }
    Text("To use this, open your bot in Telegram, press Start, and send it any message first. GoldLens then reads the latest chat ID from Telegram updates.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Text(if (telegramFormStatus.isNotBlank()) telegramFormStatus else telegramStatus, color = Muted, style = MaterialTheme.typography.bodySmall)

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
private fun InstrumentPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val instrument = InstrumentRegistry.find(selected)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Instrument: ${instrument?.symbol ?: selected} · ${instrument?.displayName ?: "Custom"}  ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                InstrumentRegistry.instruments.forEach { item ->
                    DropdownMenuItem(
                        text = { Text("${item.symbol} · ${item.displayName}") },
                        onClick = { onSelect(item.symbol); expanded = false },
                    )
                }
            }
        }
        Text("Broker alias is a starter symbol only. Tick value, contract size and minimum volume are unset until verified with your broker.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelElevated),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (candles.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No candle data", color = MutedDim)
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(260.dp).padding(16.dp)) {
                val visible = candles.takeLast(80)
                val lo = visible.minOf(Candle::low)
                val hi = visible.maxOf(Candle::high)
                val span = (hi - lo).coerceAtLeast(1e-9)
                fun y(v: Double) = size.height - ((v - lo) / span * size.height).toFloat()

                // Close-price gradient area
                val path = androidx.compose.ui.graphics.Path()
                val step = size.width / visible.size
                visible.forEachIndexed { index, candle ->
                    val x = step * (index + 0.5f)
                    val yy = y(candle.close)
                    if (index == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                path.lineTo(size.width, size.height)
                path.lineTo(0f, size.height)
                path.close()
                val gradient = Brush.linearGradient(
                    colors = listOf(Green.copy(alpha = 0.10f), Green.copy(alpha = 0.0f)),
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                )
                drawPath(path, brush = gradient)

                // Grid lines
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val v = lo + (hi - lo) * i / gridSteps
                    val yy = y(v)
                    drawLine(SurfaceBorder.copy(alpha = 0.3f), Offset(0f, yy), Offset(size.width, yy), strokeWidth = 0.5f)
                }

                // Zones
                zones.forEach { zone ->
                    if (zone.upper >= lo && zone.lower <= hi) {
                        val c = if (zone.side == ZoneSide.SUPPORT) Green else Red
                        val top = y(zone.upper)
                        val bottom = y(zone.lower)
                        drawRect(c.copy(alpha = 0.08f), topLeft = Offset(0f, top), size = Size(size.width, (bottom - top).coerceAtLeast(1f)))
                        drawRect(c.copy(alpha = 0.5f), topLeft = Offset(0f, top), size = Size(4f, (bottom - top).coerceAtLeast(1f)))
                    }
                }

                // Candles
                visible.forEachIndexed { index, candle ->
                    val centerX = step * (index + 0.5f)
                    val color = if (candle.close >= candle.open) Green else Red
                    drawLine(color, Offset(centerX, y(candle.high)), Offset(centerX, y(candle.low)), strokeWidth = 1.2f)
                    val top = y(maxOf(candle.open, candle.close))
                    val bottom = y(minOf(candle.open, candle.close))
                    drawRect(color, Offset(centerX - step * 0.26f, top), Size((step * 0.52f).coerceAtLeast(1.0f), (bottom - top).coerceAtLeast(1.2f)))
                }

                // Last price line
                if (lastPrice != null && lastPrice in lo..hi) {
                    drawLine(Gold, Offset(0f, y(lastPrice)), Offset(size.width, y(lastPrice)), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                }

                // Border
                drawRect(Color(0xFF30363D), size = size, style = Stroke(width = 1f))
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
private fun IndicatorRow(
    label: String, first: Double?, second: Double? = null,
    instrument: String = "XAU/USD", sourcePrecision: Int? = null, priceLike: Boolean = false,
) {
    val formatter: (Double) -> String = if (priceLike) ({ value -> formatPrice(value, instrument, sourcePrecision) })
        else ({ value -> String.format(java.util.Locale.US, "%.2f", value) })
    val value = listOfNotNull(first?.let(formatter), second?.let(formatter)).joinToString(" / ").ifBlank { "warming up" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Muted); Text(value) }
}

@Composable
private fun SmallMetric(label: String, value: Double?, instrument: String, sourcePrecision: Int?, modifier: Modifier = Modifier, priceValue: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Text(value?.let { if (priceValue) formatPrice(it, instrument, sourcePrecision) else String.format(java.util.Locale.US, "%.2f", it) } ?: "—", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
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
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
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

private fun candleAge(timestampMillis: Long): String {
    val age = System.currentTimeMillis() - timestampMillis
    if (age < -60_000L) return "timestamp is in the future"
    val minutes = age.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1 -> "under 1 minute"
        minutes < 60 -> "$minutes minutes"
        minutes < 1_440 -> "${minutes / 60} hours"
        else -> "${minutes / 1_440} days"
    }
}

private fun formatPrice(value: Double, instrument: String, sourcePrecision: Int? = null): String = InstrumentPrice.format(value, instrument, sourcePrecision)
private fun money(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

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

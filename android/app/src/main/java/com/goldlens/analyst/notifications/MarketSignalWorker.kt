package com.goldlens.analyst.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldlens.analyst.R
import com.goldlens.analyst.notifications.ChartRenderer.ZoneRect
import com.goldlens.analyst.MainActivity
import com.goldlens.analyst.core.AnalysisEngine
import com.goldlens.analyst.core.Direction
import com.goldlens.analyst.core.InstrumentPrice
import com.goldlens.analyst.core.InstrumentRegistry
import com.goldlens.analyst.core.MarketDataset
import com.goldlens.analyst.core.MultiTimeframeAnalyzer
import com.goldlens.analyst.core.SignalOutcomeTracker
import com.goldlens.analyst.data.GoldLensDatabase
import com.goldlens.analyst.data.replaceCandleSet
import com.goldlens.analyst.data.saveAnalysis
import com.goldlens.analyst.data.toEntity
import com.goldlens.analyst.data.market.YahooFinanceChartProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant

class MarketSignalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return@withContext Result.success()

        val timeframe = prefs.getString("timeframe", "1h") ?: "1h"
        val provider = YahooFinanceChartProvider()
        val database = GoldLensDatabase.get(applicationContext)
        var fetched = 0
        var failures = 0
        val failureNotes = mutableListOf<String>()

        for (instrument in InstrumentRegistry.instruments) {
            if (!prefs.getBoolean(KEY_ENABLED, false)) break
            try {
                val snapshot = fetchWithRetry(provider, instrument.symbol, timeframe)
                val existingCandles = withContext(Dispatchers.IO) {
                    database.candleDao().latest(instrument.symbol, timeframe, 1000).asReversed()
                }
                val mergedCandles = mergeCandles(existingCandles.map { it.toCandle() }, snapshot.candles)
                val dataset = MarketDataset(
                    candles = mergedCandles,
                    source = snapshot.source,
                    sourceLabel = snapshot.sourceLabel,
                    timeframe = timeframe,
                    loadedAtMillis = snapshot.fetchedAtMillis,
                    instrument = instrument.symbol,
                    sourcePrecision = snapshot.sourcePrecision,
                )
                val result = withContext(Dispatchers.Default) {
                    val base = AnalysisEngine.analyze(dataset)
                    MultiTimeframeAnalyzer.applyGate(base, MultiTimeframeAnalyzer.analyze(dataset))
                }

                val lastCandleMillis = snapshot.candles.lastOrNull()?.timestampMillis
                val existingLatest = existingCandles.firstOrNull()?.timestampMillis
                val newClosedCandle = lastCandleMillis != null && existingLatest != lastCandleMillis
                if (newClosedCandle && mergedCandles.size > existingCandles.size) {
                    database.replaceCandleSet(
                        timeframe, mergedCandles, instrument.symbol, snapshot.source,
                        snapshot.sourceLabel, snapshot.sourcePrecision,
                    )
                    database.saveAnalysis(result.toEntity(), result.zones.map { it.toEntity() })
                }

                deliverSignal(instrument.symbol, timeframe, result, dataset, prefs)
                fetched++
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                failures++
                failureNotes += "${instrument.symbol}: ${error.message ?: "unavailable"}"
                Log.w(TAG, "Background scan failed for ${instrument.symbol}: ${error.message}")
            }
        }

        val summary = "Background scan ${Instant.now()} · refreshed $fetched/${InstrumentRegistry.instruments.size} instruments · $failures unavailable · $timeframe"
        prefs.edit().putString(KEY_LAST_STATUS, summary).apply()
        if (fetched == 0) {
            Log.w(TAG, "No market symbols refreshed: ${failureNotes.take(3).joinToString()}")
        }
        // Keep the periodic cadence stable; one unavailable symbol must not trigger an immediate
        // retry of all 14 requests and increase the chance of provider throttling.
        Result.success()
    }

    private fun deliverSignal(
        symbol: String,
        timeframe: String,
        result: com.goldlens.analyst.core.AnalysisResult,
        dataset: MarketDataset,
        prefs: android.content.SharedPreferences,
    ) {
        val pairKey = "${symbol.replace('/', '_')}_${timeframe}"
        val localStateKey = "local_direction_$pairKey"
        val telegramStateKey = "telegram_direction_$pairKey"
        if (result.direction == Direction.WAIT) {
            prefs.edit().putString(localStateKey, Direction.WAIT.name).apply()
            if (prefs.getBoolean(KEY_TELEGRAM_ENABLED, false)) prefs.edit().putString(telegramStateKey, Direction.WAIT.name).apply()
            return
        }
        val text = signalMessage(symbol, timeframe, result, dataset, prefs)

        // Record signal for outcome tracking
        SignalOutcomeTracker.recordSignal(
            symbol = symbol,
            timeframe = timeframe,
            direction = result.direction,
            entry = result.entry,
            stopLoss = result.stopLoss,
            takeProfit = result.takeProfit,
            score = result.score,
            rewardRisk = result.rewardRisk,
        )

        if (SignalDeliveryPolicy.shouldDeliver(prefs.getString(localStateKey, null), result.direction) && postLocalNotification(symbol, timeframe, result.direction, text)) {
            prefs.edit().putString(localStateKey, result.direction.name).apply()
        }

        val chatId = prefs.getString(KEY_TELEGRAM_CHAT, "")?.trim().orEmpty()
        val token = if (prefs.getBoolean(KEY_TELEGRAM_ENABLED, false) && chatId.isNotBlank()) {
            TelegramTokenStore.load(applicationContext)
        } else null
        // Only the safest signals go to Telegram: a new BUY/SELL direction with a favorable
        // risk/reward ratio and enough supporting evidence. Lower-confidence signals still post
        // a local notification but are kept out of Telegram.
        val isSafeForTelegram = result.direction != Direction.WAIT &&
            result.rewardRisk != null && result.rewardRisk >= 2.0 &&
            result.score >= 3 && result.conflicts.isEmpty()
        if (token != null && isSafeForTelegram &&
            SignalDeliveryPolicy.shouldDeliver(prefs.getString(telegramStateKey, null), result.direction)) {
            try {
                val zoneRects = result.zones.map { zone ->
                    ZoneRect(zone.side.name, zone.lower, zone.upper)
                }
                val chartBitmap = ChartRenderer.render(
                    candles = dataset.candles,
                    symbol = symbol,
                    timeframe = timeframe,
                    direction = result.direction.name,
                    lastPrice = result.price,
                    zones = zoneRects,
                )
                val pngBytes = ChartRenderer.bitmapToPngBytes(chartBitmap)
                val caption = buildString {
                    append("GoldLens signal · ${result.direction.name}\n")
                    append("$symbol · $timeframe\n")
                    result.price?.let { append("Close: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}\n") }
                    result.entry?.let { append("Entry: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}\n") }
                    result.stopLoss?.let { append("Stop: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}\n") }
                    result.takeProfit?.let { append("Target: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}\n") }
                    append("R:R ${result.rewardRisk?.let { "%.2f".format(it) } ?: "—"} · Score ${result.score}\n")
                    append(result.reason)
                }
                TelegramNotifier.sendPhoto(token, chatId, pngBytes, caption.toString())
                prefs.edit().putString(telegramStateKey, result.direction.name).putString(KEY_TELEGRAM_STATUS, "Telegram: chart signal sent at ${Instant.now()}.").apply()
            } catch (error: Exception) {
                prefs.edit().putString(KEY_TELEGRAM_STATUS, "Telegram chart failed: ${error.message ?: "check token/chat ID and network"}; falling back to text.").apply()
                try {
                    TelegramNotifier.sendMessage(token, chatId, text)
                    prefs.edit().putString(telegramStateKey, result.direction.name).putString(KEY_TELEGRAM_STATUS, "Telegram: text signal sent at ${Instant.now()}.").apply()
                } catch (textError: Exception) {
                    prefs.edit().putString(KEY_TELEGRAM_STATUS, "Telegram delivery failed: ${textError.message ?: "check token/chat ID and network"}").apply()
                    Log.w(TAG, "Telegram delivery failed for $symbol; will retry on a later scan.", error)
                }
            }
        } else if (token != null && !isSafeForTelegram && result.direction != Direction.WAIT) {
            prefs.edit().putString(KEY_TELEGRAM_STATUS, "Telegram: $symbol ${result.direction.name} signal held — not meeting safety threshold (R:R ≥ 2.0, score ≥ 3, no conflicts).").apply()
        }
    }

    /** Fetch with 10s timeout and one retry after 2s backoff. */
    private suspend fun fetchWithRetry(provider: YahooFinanceChartProvider, symbol: String, timeframe: String): com.goldlens.analyst.data.market.ProviderMarketSnapshot {
        return withContext(Dispatchers.IO.withTimeout(10_000L)) {
            runCatching { provider.fetch(symbol, timeframe) }.getOrElse { firstError ->
                Log.w(TAG, "First fetch failed for $symbol: ${firstError.message}, retrying...")
                withContext(Dispatchers.IO) { delay(2_000L) }
                runCatching { provider.fetch(symbol, timeframe) }.getOrElse { secondError ->
                    throw Exception("Fetch failed after 2 attempts: ${secondError.message ?: firstError.message ?: "unavailable"}")
                }
            }
        }
    }

    /** Merge new candles with existing history, keeping oldest first, deduped by timestamp, capped at 1000. */
    private fun mergeCandles(existing: List<com.goldlens.analyst.core.Candle>, fresh: List<com.goldlens.analyst.core.Candle>): List<com.goldlens.analyst.core.Candle> {
        val byTimestamp = linkedMapOf<Long, com.goldlens.analyst.core.Candle>()
        existing.forEach { byTimestamp[it.timestampMillis] = it }
        fresh.forEach { byTimestamp[it.timestampMillis] = it }
        return byTimestamp.values.sortedBy { it.timestampMillis }.takeLast(1000)
    }

    private fun signalMessage(
        symbol: String,
        timeframe: String,
        result: com.goldlens.analyst.core.AnalysisResult,
        dataset: MarketDataset,
        prefs: android.content.SharedPreferences,
    ): String = buildString {
        append("GoldLens signal · ${result.direction.name}\n")
        append("$symbol · $timeframe\n")
        append("Source: ${dataset.sourceLabel} (historical/reference data, not a broker quote)\n")
        result.price?.let { append("Last candle close: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}\n") }
        result.entry?.let { append("Entry: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}\n") }
        result.stopLoss?.let {
            val pointSize = prefs.getString("metal_point_size_$symbol", "")?.toDoubleOrNull()
            val distance = InstrumentPrice.unitDistanceText(symbol, result.entry, it, pointSize)
            append("Stop: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}")
            if (distance != null) append(" · Stop distance: $distance")
            appendLine()
        }
        result.takeProfit?.let {
            val pointSize = prefs.getString("metal_point_size_$symbol", "")?.toDoubleOrNull()
            val distance = InstrumentPrice.unitDistanceText(symbol, result.entry, it, pointSize)
            append("Target: ${InstrumentPrice.format(it, symbol, dataset.sourcePrecision)}")
            if (distance != null) append(" · Target distance: $distance")
            appendLine()
        }
        append("Closed candle: ${result.timestampMillis?.let { Instant.ofEpochMilli(it) } ?: "unknown"}\n")
        append(result.reason)
    }

    private fun postLocalNotification(symbol: String, timeframe: String, direction: Direction, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "GoldLens market signals", NotificationManager.IMPORTANCE_DEFAULT))
            }
            val openIntent = PendingIntent.getActivity(
                applicationContext,
                (symbol + timeframe).hashCode(),
                Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("GoldLens · $symbol · ${direction.name}")
                .setContentText(text.lineSequence().drop(2).firstOrNull() ?: "New directional signal")
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(applicationContext).notify((symbol + timeframe).hashCode(), notification)
            true
        } catch (error: SecurityException) {
            false
        }
    }

    companion object {
        const val PREFS = "goldlens-settings"
        const val KEY_ENABLED = "auto_monitoring_enabled"
        const val KEY_LAST_STATUS = "auto_monitoring_last_status"
        const val KEY_TELEGRAM_CHAT = "telegram_chat_id"
        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_TELEGRAM_STATUS = "telegram_status"
        const val PERIODIC_WORK = "goldlens_market_signal_periodic"
        const val IMMEDIATE_WORK = "goldlens_market_signal_now"
        private const val TAG = "GoldLensAutoScan"
        private const val CHANNEL_ID = "goldlens_market_signals"
    }
}

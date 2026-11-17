package com.goldlens.analyst.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.goldlens.analyst.MainActivity
import com.goldlens.analyst.R
import com.goldlens.analyst.core.AnalysisEngine
import com.goldlens.analyst.core.DataSourceKind
import com.goldlens.analyst.core.MarketDataset
import com.goldlens.analyst.core.MultiTimeframeAnalyzer
import com.goldlens.analyst.data.GoldLensDatabase
import com.goldlens.analyst.data.toCandle
import com.goldlens.analyst.data.toDataSourceKind
import com.goldlens.analyst.data.toEntity
import com.goldlens.analyst.data.saveAnalysis
import com.goldlens.analyst.data.toEntity as zoneToEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FloatingAssistantService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var bubble: TextView? = null
    private var headerTitle: TextView? = null
    private var headerMinimize: TextView? = null
    private var headerClose: TextView? = null
    private lateinit var params: WindowManager.LayoutParams
    private val prefs by lazy { getSharedPreferences("goldlens-settings", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, makeNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, makeNotification())
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        buildOverlay()
        prefs.edit().putBoolean("overlay_running", true).apply()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_ANALYZE) analyzeStoredData()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        prefs.edit().putBoolean("overlay_running", false).apply()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildOverlay() {
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleSize = (56 * density).toInt()
        val prefsX = prefs.getInt("overlay_x", 16)
        val prefsY = prefs.getInt("overlay_y", 180)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xFF151A23.toInt(), (18 * density).toFloat())
            elevation = 10 * density
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            background = rounded(0xFF202838.toInt(), (18 * density).toFloat())
        }
        val icon = TextView(this).apply {
            text = "Au"
            textSize = 17f
            setTextColor(0xFFFFD66B.toInt())
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(0xFF2A3140.toInt(), (24 * density).toFloat())
            layoutParams = LinearLayout.LayoutParams(bubbleSize - (8 * density).toInt(), bubbleSize - (8 * density).toInt())
        }
        bubble = icon
        header.addView(icon)
        val title = TextView(this).apply {
            text = prefs.getString("overlay_signal", "WAIT") ?: "WAIT"
            textSize = 14f
            setTextColor(0xFFE8EAF0.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding((10 * density).toInt(), 0, (8 * density).toInt(), 0)
        }
        val minimize = textButton("−", onClick = { setExpanded(false) })
        val close = textButton("×", onClick = { stopSelf() })
        headerTitle = title
        headerMinimize = minimize
        headerClose = close
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(minimize)
        header.addView(close)
        if (!prefs.getBoolean("overlay_expanded", false)) {
            title.visibility = View.GONE
            minimize.visibility = View.GONE
            close.visibility = View.GONE
        }
        container.addView(header)

        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
        }
        panel = panelContent
        val scroll = ScrollView(this).apply { addView(panelContent) }
        val width = min((screenWidth * 0.88f).toInt(), (380 * density).toInt())
        container.layoutParams = LinearLayout.LayoutParams(width, -2)
        container.addView(scroll, LinearLayout.LayoutParams(-1, (360 * density).toInt()))
        refreshPanel()
        scroll.visibility = View.GONE
        root = container

        params = WindowManager.LayoutParams(
            if (prefs.getBoolean("overlay_expanded", false)) width else bubbleSize,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(0, min(prefsX, screenWidth - bubbleSize))
            y = max(0, prefsY)
        }
        windowManager.addView(container, params)
        if (prefs.getBoolean("overlay_expanded", false)) setExpanded(true)
        header.setOnTouchListener(dragListener(header, bubbleSize))
        icon.setOnClickListener { setExpanded(true) }
        title.setOnClickListener { setExpanded(true) }
    }

    private fun refreshPanel() {
        val content = panel ?: return
        content.removeAllViews()
        val d = resources.displayMetrics.density
        fun label(text: String, gold: Boolean = false, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            textSize = if (bold) 16f else 13f
            setTextColor(if (gold) 0xFFFFD66B.toInt() else 0xFFE3E6ED.toInt())
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setPadding(0, (4 * d).toInt(), 0, (4 * d).toInt())
        }
        headerTitle?.text = prefs.getString("overlay_signal", "WAIT") ?: "WAIT"
        bubble?.text = when (prefs.getString("overlay_signal", "WAIT")) { "BUY" -> "↑"; "SELL" -> "↓"; else -> "Au" }
        val symbol = prefs.getString("analysis_symbol", "XAU/USD") ?: "XAU/USD"
        content.addView(label("GOLDLENS · $symbol", gold = true, bold = true))
        content.addView(label(prefs.getString("overlay_details", "No saved analysis yet. Import candles and analyze in GoldLens.") ?: "No analysis."))
        content.addView(label("Overlay analysis uses only locally imported candles; no live price is assumed.", gold = false))
        content.addView(textButton("Analyze saved candles", onClick = { analyzeStoredData() }, fullWidth = true))
        content.addView(textButton("Open full dashboard", onClick = { openDashboard() }, fullWidth = true))
        content.addView(textButton("Close floating assistant", onClick = { stopSelf() }, fullWidth = true))
    }

    private fun analyzeStoredData() {
        val timeframe = prefs.getString("timeframe", "1h") ?: "1h"
        val instrument = prefs.getString("analysis_symbol", "XAU/USD") ?: "XAU/USD"
        scope.launch {
            val dataset = withContext(Dispatchers.IO) {
                val rows = GoldLensDatabase.get(this@FloatingAssistantService).candleDao().latest(instrument, timeframe, 5_000).asReversed()
                if (rows.isEmpty()) null else MarketDataset(rows.map { it.toCandle() }, rows.first().toDataSourceKind(), rows.first().sourceLabel, timeframe, System.currentTimeMillis(), instrument)
            }
            if (dataset == null) {
                prefs.edit().putString("overlay_details", "No saved candle data for $timeframe. Fetch provider candles or import an OHLC CSV in GoldLens first.").apply()
                refreshPanel()
                return@launch
            }
            val (baseResult, mtf) = withContext(Dispatchers.Default) {
                val base = AnalysisEngine.analyze(dataset)
                base to MultiTimeframeAnalyzer.analyze(dataset)
            }
            val result = MultiTimeframeAnalyzer.applyGate(baseResult, mtf)
            val database = GoldLensDatabase.get(this@FloatingAssistantService)
            withContext(Dispatchers.IO) { database.saveAnalysis(result.toEntity(), result.zones.map { it.zoneToEntity() }) }
            val summary = buildString {
                append("${result.direction} · ${result.instrument} · ${result.timeframe} · ${result.reason}\n")
                append("Multi-timeframe: ${mtf.agreement} · ${mtf.explanation}").appendLine()
                result.price?.let { append("Last saved candle close: ${format(it)}\n") }
                result.stopLoss?.let { append("Stop ${format(it)} · Target ${format(result.takeProfit ?: it)} · R:R ${format(result.rewardRisk ?: 0.0)}\n") }
                result.zones.take(3).forEach { append("${it.side} ${it.strength}: ${format(it.lower)}–${format(it.upper)} (${it.touches} touches, ${it.status})\n") }
            }
            prefs.edit().putString("overlay_signal", result.direction.name).putString("overlay_details", summary).apply()
            refreshPanel()
        }
    }

    private fun setExpanded(expanded: Boolean) {
        val view = root ?: return
        val scroll = (view.getChildAt(1) as? ScrollView) ?: return
        scroll.visibility = if (expanded) View.VISIBLE else View.GONE
        headerTitle?.visibility = if (expanded) View.VISIBLE else View.GONE
        headerMinimize?.visibility = if (expanded) View.VISIBLE else View.GONE
        headerClose?.visibility = if (expanded) View.VISIBLE else View.GONE
        val density = resources.displayMetrics.density
        params.width = if (expanded) min((resources.displayMetrics.widthPixels * 0.88f).toInt(), (380 * density).toInt()) else (56 * density).toInt()
        params.x = params.x.coerceIn(0, max(0, resources.displayMetrics.widthPixels - params.width))
        params.y = params.y.coerceIn(0, max(0, resources.displayMetrics.heightPixels - (if (expanded) (360 * density).toInt() else (56 * density).toInt())))
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        prefs.edit().putBoolean("overlay_expanded", expanded).apply()
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dragListener(handle: View, bubbleSize: Int) = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; originX = params.x; originY = params.y; moved = false; true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) moved = true
                val width = root?.width ?: bubbleSize
                params.x = (originX + dx).coerceIn(0, max(0, resources.displayMetrics.widthPixels - width))
                params.y = (originY + dy).coerceIn(0, max(0, resources.displayMetrics.heightPixels - bubbleSize))
                root?.let { view -> runCatching { windowManager.updateViewLayout(view, params) } }
                true
            }
            MotionEvent.ACTION_UP -> {
                prefs.edit().putInt("overlay_x", params.x).putInt("overlay_y", params.y).apply()
                if (!moved) setExpanded(prefs.getBoolean("overlay_expanded", false).not())
                true
            }
            else -> true
        }
    }

    private fun openDashboard() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun textButton(text: String, onClick: () -> Unit, fullWidth: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(0xFFFFD66B.toInt())
        gravity = Gravity.CENTER
        setPadding((8 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt(), (8 * resources.displayMetrics.density).toInt())
        setOnClickListener { onClick() }
        if (fullWidth) layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius; setStroke((resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xFF394252.toInt())
    }

    private fun makeNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "GoldLens floating assistant", NotificationManager.IMPORTANCE_LOW))
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, FloatingAssistantService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openIntent = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GoldLens floating assistant is active")
            .setContentText("Tap to return to GoldLens; stop it here or in Settings.")
            .setContentIntent(openIntent).setOngoing(true)
            .addAction(0, "Stop overlay", stopIntent).build()
    }

    private fun format(value: Double) = String.format(Locale.US, "%.2f", value)

    private var downX = 0f
    private var downY = 0f
    private var originX = 0
    private var originY = 0
    private var moved = false

    companion object {
        const val ACTION_STOP = "com.goldlens.analyst.overlay.STOP"
        const val ACTION_ANALYZE = "com.goldlens.analyst.overlay.ANALYZE"
        private const val CHANNEL_ID = "goldlens_overlay"
        private const val NOTIFICATION_ID = 4101
    }
}

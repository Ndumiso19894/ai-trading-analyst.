package com.goldlens.analyst

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoldLensScreen(this) }
    }
}

@Composable
private fun GoldLensScreen(context: Context) {
    val preferences = remember(context) { context.getSharedPreferences("goldlens-settings", Context.MODE_PRIVATE) }
    var baseUrl by remember {
        mutableStateOf(preferences.getString("backend_url", BuildConfig.DEFAULT_BACKEND_URL) ?: BuildConfig.DEFAULT_BACKEND_URL)
    }
    var response by remember { mutableStateOf("Connecting to the GoldLens API. No sample prices are shown.") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var connectionState by remember { mutableStateOf("Checking secure API connection…") }
    var showAdvanced by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun request(path: String, file: Uri? = null) {
        scope.launch {
            busy = true
            error = ""
            if (path == "/health") connectionState = "Connecting…"
            try {
                val root = baseUrl.trim().trimEnd('/')
                require(root.startsWith("https://") || BuildConfig.DEBUG && root.startsWith("http://")) {
                    "Use an HTTPS backend URL. HTTP is allowed only in debug builds."
                }
                val body = withContext(Dispatchers.IO) {
                    if (file == null) getJson(root + path) else uploadImage(context, root + path, file)
                }
                response = body
                if (path == "/health") {
                    connectionState = if (JSONObject(body).optString("status") == "ok") "Connected securely" else "API responded; health status unknown"
                }
            } catch (e: Exception) {
                error = e.message ?: "Request failed"
                if (path == "/health") connectionState = "Backend unavailable — retry when online"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { request("/health") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) request("/api/screenshot/analyze", uri)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF91A7FF),
            background = androidx.compose.ui.graphics.Color(0xFF0B0D12),
            surface = androidx.compose.ui.graphics.Color(0xFF141923)
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("GoldLens", style = MaterialTheme.typography.headlineMedium)
            Text("AI Trading Analyst · XAU/USD", color = MaterialTheme.colorScheme.secondary)
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(connectionState, style = MaterialTheme.typography.titleSmall)
                    Text(baseUrl, style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide advanced connection settings" else "Advanced connection settings")
            }
            AnimatedVisibility(showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            preferences.edit().putString("backend_url", it).apply()
                        },
                        label = { Text("Backend HTTPS URL") },
                        placeholder = { Text("https://your-api.onrender.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("The default API is preconfigured. Only change this if you have another trusted HTTPS server.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { request("/health") }, enabled = !busy) { Text("Test connection") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { request("/api/analysis?timeframe=1h&period=3mo") }, enabled = !busy) { Text("Analyze Gold") }
                OutlinedButton(onClick = { request("/api/market/candles?timeframe=1h&period=5d") }, enabled = !busy) { Text("Market data") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { request("/api/signals") }, enabled = !busy) { Text("Signal history") }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("Analyze screenshot") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text("API response", style = MaterialTheme.typography.titleMedium)
            Surface(
                Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(response, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
            Text("Market data may be delayed. Analysis is educational and not financial advice.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun getJson(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 30000
    connection.readTimeout = 90000 // Render free services can cold-start after inactivity.
    connection.requestMethod = "GET"
    connection.useCaches = false
    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
        pretty(body)
    } finally {
        connection.disconnect()
    }
}

private fun uploadImage(context: Context, address: String, uri: Uri): String {
    val boundary = "----GoldLens${UUID.randomUUID()}"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 30000
    connection.readTimeout = 120000
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    try {
        connection.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"chart.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            context.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(out) }
                ?: throw IllegalArgumentException("Cannot read selected image")
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
        return pretty(body)
    } finally {
        connection.disconnect()
    }
}

private fun pretty(raw: String): String = try { JSONObject(raw).toString(2) } catch (_: Exception) { raw }

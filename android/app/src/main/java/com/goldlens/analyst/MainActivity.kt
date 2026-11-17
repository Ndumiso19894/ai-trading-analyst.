package com.goldlens.analyst

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
    var baseUrl by remember { mutableStateOf("http://10.0.2.2:8000") }
    var response by remember { mutableStateOf("Connect to the FastAPI backend to load actual market data. No demo prices are displayed.") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun run(path: String, file: Uri? = null) {
        scope.launch {
            busy = true; error = ""
            try { response = withContext(Dispatchers.IO) { if (file == null) getJson(baseUrl.trimEnd('/') + path) else uploadImage(context, baseUrl.trimEnd('/') + path, file) } }
            catch (e: Exception) { error = e.message ?: "Request failed" }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) run("/api/screenshot/analyze", uri) }
    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF91A7FF), background = androidx.compose.ui.graphics.Color(0xFF0B0D12), surface = androidx.compose.ui.graphics.Color(0xFF141923))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("GoldLens", style = MaterialTheme.typography.headlineMedium)
            Text("AI Trading Analyst · XAU/USD", color = MaterialTheme.colorScheme.secondary)
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Android emulator default: 10.0.2.2. For a phone, enter the reachable backend URL.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run("/api/analysis?timeframe=1h&period=3mo") }, enabled = !busy) { Text("Analyze Gold") }
                OutlinedButton(onClick = { run("/api/market/candles?timeframe=1h&period=5d") }, enabled = !busy) { Text("Market data") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { run("/api/signals") }, enabled = !busy) { Text("Signal history") }
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("Analyze screenshot") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Text("API response", style = MaterialTheme.typography.titleMedium)
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                Text(response, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
            Text("Analysis is educational, may use delayed provider prices, and is not financial advice.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun getJson(address: String): String {
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000; connection.readTimeout = 15000; connection.requestMethod = "GET"
    return try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
        pretty(body)
    } finally { connection.disconnect() }
}

private fun uploadImage(context: Context, address: String, uri: Uri): String {
    val boundary = "----GoldLens${UUID.randomUUID()}"
    val connection = URL(address).openConnection() as HttpURLConnection
    connection.connectTimeout = 10000; connection.readTimeout = 30000; connection.requestMethod = "POST"
    connection.doOutput = true; connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    try {
        connection.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"chart.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            context.contentResolver.openInputStream(uri)?.use { input -> input.copyTo(out) } ?: throw IllegalArgumentException("Cannot read selected image")
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: $body")
        return pretty(body)
    } finally { connection.disconnect() }
}

private fun pretty(raw: String): String = try { JSONObject(raw).toString(2) } catch (_: Exception) { raw }

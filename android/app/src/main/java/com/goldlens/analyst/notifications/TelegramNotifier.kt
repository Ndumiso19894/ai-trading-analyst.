package com.goldlens.analyst.notifications

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Telegram bot token is encrypted with a non-exportable Android Keystore AES-GCM key. */
object TelegramTokenStore {
    private const val PREFS = "goldlens-settings"
    private const val TOKEN_KEY = "telegram_token_aes_gcm"
    private const val KEY_ALIAS = "goldlens_telegram_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun save(context: Context, token: String) {
        require(token.isNotBlank() && token.length <= 512) { "Enter a valid Telegram bot token." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): String? = runCatching {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_KEY, null) ?: return null
        val packed = Base64.decode(stored, Base64.NO_WRAP)
        require(packed.size > 12)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
    }.getOrNull()

    fun hasToken(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN_KEY).apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}

/** Minimal Telegram Bot API sender. Never logs credentials or response bodies. */
object TelegramNotifier {
    fun sendMessage(token: String, chatId: String, message: String) {
        require(token.isNotBlank() && chatId.isNotBlank()) { "Telegram bot token and chat ID are required." }
        require(message.length <= 4_000) { "Telegram message is too long." }
        val connection = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject().put("chat_id", chatId).put("text", message).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val responseStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299 || !runCatching { JSONObject(response).optBoolean("ok") }.getOrDefault(false)) {
                throw IllegalStateException("Telegram rejected the message (HTTP $status). Check the bot token and chat ID.")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Finds the most recent private/group chat that has sent a message to this bot. */
    fun findLatestChatId(token: String): String {
        require(token.isNotBlank()) { "Enter your Telegram bot token first." }
        val connection = URL("https://api.telegram.org/bot$token/getUpdates").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val payload = runCatching { JSONObject(response) }.getOrElse {
                throw IllegalStateException("Could not read Telegram's response (HTTP $status).")
            }
            require(status in 200..299 && payload.optBoolean("ok")) {
                "Telegram could not read updates. Verify the bot token and try again."
            }
            val updates = payload.optJSONArray("result")
            if (updates != null) for (index in updates.length() - 1 downTo 0) {
                val update = updates.optJSONObject(index) ?: continue
                val chat = update.optJSONObject("message")?.optJSONObject("chat")
                    ?: update.optJSONObject("channel_post")?.optJSONObject("chat")
                    ?: update.optJSONObject("edited_message")?.optJSONObject("chat")
                val id = chat?.opt("id")?.toString()?.takeIf(String::isNotBlank)
                if (id != null) return id
            }
            throw IllegalStateException("No chat found yet. Open your bot in Telegram, press Start, send it a message, then try again.")
        } finally {
            connection.disconnect()
        }
    }
}

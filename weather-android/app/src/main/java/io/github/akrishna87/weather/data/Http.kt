package io.github.akrishna87.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

/** Plain HTTPS GETs. Every source used here is free and needs no key, only a polite User-Agent. */
object Http {
    const val USER_AGENT = "Vaanilai/1.0 (+https://github.com/Akrishna87/Akrishna87)"

    /** Open-Meteo's free tier refuses too many requests at once, so at most this many run together. */
    private val openMeteoSlots = Semaphore(2)

    /** GET with a retry or two when the server says "too many requests" or is slow to answer. */
    suspend fun get(url: String, accept: String = "application/json", timeoutMs: Int = 20_000): String {
        val limited = "open-meteo.com" in url
        var attempt = 0
        while (true) {
            try {
                return if (limited) openMeteoSlots.withPermit { fetch(url, accept, timeoutMs) } else fetch(url, accept, timeoutMs)
            } catch (e: Exception) {
                val retry = (e is HttpException && e.code == 429 && attempt < 3) || (e is SocketTimeoutException && attempt < 1)
                if (!retry) throw e
                attempt++
                delay(1_500L * attempt)
            }
        }
    }

    private suspend fun fetch(url: String, accept: String, timeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", accept)
                val code = conn.responseCode
                if (code !in 200..299) {
                    val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                    throw HttpException(code, URL(url).host, body)
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
}

class HttpException(val code: Int, host: String, body: String?) : IOException(
    // Open-Meteo explains refusals in a "reason" field; show that rather than a bare status code.
    runCatching { JSONObject(body ?: "").optString("reason") }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: when (code) {
            429 -> "Too many requests to $host just now; try again in a minute"
            404 -> "Not found at $host"
            else -> "HTTP $code from $host"
        },
)

/** Formats a coordinate the way every API here accepts it (dot decimal, at most 4 places). */
fun coord(v: Double): String = String.format(Locale.US, "%.4f", v)

fun JSONObject.dbl(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

fun JSONArray?.dblAt(i: Int): Double? =
    if (this != null && i < length() && !isNull(i)) optDouble(i).takeUnless { it.isNaN() } else null

fun JSONArray?.strAt(i: Int): String? =
    if (this != null && i < length() && !isNull(i)) optString(i).takeIf { it.isNotBlank() } else null

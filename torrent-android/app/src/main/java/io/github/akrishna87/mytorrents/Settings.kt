package io.github.akrishna87.mytorrents

import android.content.Context
import io.github.akrishna87.mytorrents.engine.EngineSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The person's choices, kept in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(WIFI_ONLY, false))
    private val _seedWhenFinished = MutableStateFlow(prefs.getBoolean(SEED, false))
    private val _downloadLimitKb = MutableStateFlow(prefs.getInt(DOWN_LIMIT, 0))
    private val _uploadLimitKb = MutableStateFlow(prefs.getInt(UP_LIMIT, 0))

    /** Only download and share while on Wi-Fi (or another network that isn't charged by the MB). */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly

    /** Keep sharing a torrent with others after it has finished downloading. */
    val seedWhenFinished: StateFlow<Boolean> = _seedWhenFinished

    /** KB/s, 0 for no limit. */
    val downloadLimitKb: StateFlow<Int> = _downloadLimitKb

    /** KB/s, 0 for no limit. */
    val uploadLimitKb: StateFlow<Int> = _uploadLimitKb

    fun setWifiOnly(on: Boolean) {
        prefs.edit().putBoolean(WIFI_ONLY, on).apply()
        _wifiOnly.value = on
    }

    fun setSeedWhenFinished(on: Boolean) {
        prefs.edit().putBoolean(SEED, on).apply()
        _seedWhenFinished.value = on
    }

    fun setDownloadLimitKb(kb: Int) {
        prefs.edit().putInt(DOWN_LIMIT, kb).apply()
        _downloadLimitKb.value = kb
    }

    fun setUploadLimitKb(kb: Int) {
        prefs.edit().putInt(UP_LIMIT, kb).apply()
        _uploadLimitKb.value = kb
    }

    fun engineSettings() = EngineSettings(downloadLimitKb.value, uploadLimitKb.value)

    private companion object {
        const val WIFI_ONLY = "wifi_only"
        const val SEED = "seed_when_finished"
        const val DOWN_LIMIT = "download_limit_kb"
        const val UP_LIMIT = "upload_limit_kb"
    }
}

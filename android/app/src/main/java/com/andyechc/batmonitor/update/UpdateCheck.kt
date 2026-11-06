package com.andyechc.batmonitor.update

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Version manifest published next to the landing site.
 *
 * Exact contract (any other shape is ignored):
 * ```
 * {
 *   "versionName": "1.0",
 *   "versionCode": 1,
 *   "notes": { "es": "…", "en": "…" },
 *   "uptodownUrl": "https://batmonitor.uptodown.com/android",
 *   "apkUrl": "https://github.com/andyechc/batmonitor/releases/latest/download/batmonitor.apk"
 * }
 * ```
 * Everything is best effort: no network, a timeout, an HTTP error or bad JSON all stay silent.
 */
object UpdateCheck {
    /**
     * PRODUCCIÓN. Debe apuntar al mismo despliegue que `landing/`.
     *
     * How to verify the dialog locally without publishing anything:
     * 1. `mkdir -p /tmp/upd/api` and write `/tmp/upd/api/version.json` with a `versionCode` higher
     *    than the installed one; `cd /tmp/upd && python3 -m http.server 8000`.
     * 2. `adb reverse tcp:8000 tcp:8000` (the emulator reaches the host at localhost).
     * 3. Point this constant at `http://localhost:8011/api/version.json`.
     * 4. Cleartext HTTP is blocked on targetSdk 28+, so temporarily add
     *    `android:usesCleartextTraffic="true"` to the `<application>` tag of AndroidManifest.xml.
     * 5. `./gradlew :app:assembleDebug`, install, open the app once: the AlertDialog appears.
     * 6. REVERT both the constant and the manifest attribute; neither belongs in a release build.
     */
    const val UPDATE_URL = "https://batmonitor.app/api/version.json" // TODO: debe coincidir con el despliegue de landing/

    /** Fallback when the manifest omits `uptodownUrl`. */
    const val DEFAULT_DOWNLOAD_URL = "https://batmonitor.uptodown.com/android"

    private const val TIMEOUT_MS = 8_000

    data class AvailableUpdate(
        val versionName: String,
        val versionCode: Long,
        val notes: String,
        val downloadUrl: String,
        val apkUrl: String?,
    )

    /** One check per process, shared by every screen that hooks the dialog. */
    object Gate {
        private val checked = AtomicBoolean(false)

        /** True only for the first caller in this process. */
        fun claim(): Boolean = checked.compareAndSet(false, true)
    }

    /** Returns the pending update, or null when there is nothing to show (or anything failed). */
    suspend fun check(context: Context): AvailableUpdate? = withContext(Dispatchers.IO) {
        try {
            val installed = installedVersionCode(context)
            val json = fetch(UPDATE_URL) ?: return@withContext null
            val remote = parse(json) ?: return@withContext null
            if (remote.versionCode > installed) remote else null
        } catch (_: Exception) {
            null
        }
    }

    /** Pure parser, kept separate so the JSON contract can be checked without a network. */
    fun parse(json: String): AvailableUpdate? = try {
        val o = JSONObject(json)
        val name = o.optString("versionName").ifBlank { o.optLong("versionCode").toString() }
        AvailableUpdate(
            versionName = name,
            versionCode = o.optLong("versionCode", 0L),
            notes = notes(o, Locale.getDefault().language),
            downloadUrl = o.optString("uptodownUrl").ifBlank { DEFAULT_DOWNLOAD_URL },
            apkUrl = o.optString("apkUrl").ifBlank { null },
        )
    } catch (_: Exception) {
        null
    }

    private fun notes(o: JSONObject, language: String): String {
        val notes = o.opt("notes") ?: return ""
        if (notes is String) return notes
        val obj = notes as? JSONObject ?: return ""
        return when {
            language == "en" -> obj.optString("en").ifBlank { obj.optString("es") }
            else -> obj.optString("es").ifBlank { obj.optString("en") }
        }
    }

    private fun fetch(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            // The CDN in front of the landing may serve a cached manifest; a stale one is harmless,
            // but asking for the fresh copy keeps a brand-new release visible right away.
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun installedVersionCode(context: Context): Long = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION") // versionCode is the only getter before API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    } catch (_: Exception) {
        Long.MAX_VALUE
    }
}

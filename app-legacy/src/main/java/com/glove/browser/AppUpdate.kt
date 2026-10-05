package com.glove.browser

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object AppUpdate {
    data class Release(val version: String, val apkUrl: String)

    fun check(activity: Activity, legacy: Boolean, localVersion: String, onLater: () -> Unit = {}) {
        thread(name = "glove-update") {
            val release = latest(legacy) ?: return@thread
            if (!newer(release.version, localVersion)) return@thread
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                AlertDialog.Builder(activity)
                    .setTitle("Glove Browser")
                    .setMessage("Вышло новое обновление Glove Браузера, обновить сейчас?")
                    .setPositiveButton("Обновить сейчас") { _, _ -> download(activity, release) }
                    .setNegativeButton("Обновить позже") { _, _ -> onLater() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun latest(legacy: Boolean): Release? {
        val body = get("https://api.github.com/repos/VMRcompany/glove-browser/releases/latest") ?: return null
        return try {
            val json = JSONObject(body)
            val version = json.optString("tag_name").removePrefix("v").trim()
            if (version.isBlank()) return null
            val assets = json.optJSONArray("assets") ?: return null
            val wanted = if (legacy) "GloveBrowser-Legacy.apk" else "GloveBrowser.apk"
            var url = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").equals(wanted, true)) {
                    url = asset.optString("browser_download_url")
                    break
                }
            }
            if (url.isBlank()) return null
            Release(version, url)
        } catch (_: Exception) {
            null
        }
    }

    private fun download(activity: Activity, release: Release) {
        @Suppress("DEPRECATION")
        val progress = ProgressDialog(activity).apply {
            setTitle("Обновление Glove Browser")
            setMessage("Загрузка обновления… 0%")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }
        thread(name = "glove-update-dl") {
            try {
                val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "GloveBrowser/" + release.version)
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val total = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    conn.contentLengthLong.coerceAtLeast(0L)
                } else {
                    conn.contentLength.toLong().coerceAtLeast(0L)
                }
                val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
                val file = File(dir, if (release.apkUrl.contains("Legacy", true)) "GloveBrowser-Legacy.apk" else "GloveBrowser.apk")
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            read += n
                            val pct = if (total > 0) ((read * 100) / total).toInt().coerceIn(0, 100) else 0
                            activity.runOnUiThread {
                                if (progress.isShowing) {
                                    progress.progress = pct
                                    progress.setMessage("Загрузка обновления… $pct%")
                                }
                            }
                        }
                    }
                }
                activity.runOnUiThread {
                    if (progress.isShowing) progress.dismiss()
                    install(activity, file)
                }
            } catch (error: Exception) {
                activity.runOnUiThread {
                    if (progress.isShowing) progress.dismiss()
                    Toast.makeText(activity, "Не удалось скачать обновление", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun install(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setMessage("Разрешите установку из этого источника, затем нажмите «Обновить сейчас» ещё раз.")
                .setPositiveButton("Настройки") { _, _ ->
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName))
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(activity, "Не удалось открыть установщик", Toast.LENGTH_LONG).show()
        }
    }

    fun newer(remote: String, local: String): Boolean {
        val left = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val right = local.split(".", "-").mapNotNull { it.toIntOrNull() }
        val count = maxOf(left.size, right.size)
        for (i in 0 until count) {
            val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }

    private fun get(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "GloveBrowser")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (_: Exception) {
        null
    }
}

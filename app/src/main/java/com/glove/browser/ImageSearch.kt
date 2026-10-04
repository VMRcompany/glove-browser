package com.glove.browser

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ImageSearch {
    fun resultUrl(file: File): String {
        return try {
            upload(file)
        } catch (_: Exception) {
            "https://yandex.ru/images/"
        }
    }

    private fun upload(file: File): String {
        val boundary = "----GloveBoundary" + System.currentTimeMillis()
        val conn = (URL("https://yandex.ru/images/search?rpt=imageview&format=json").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            instanceFollowRedirects = false
            connectTimeout = 20000
            readTimeout = 20000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"upfile\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        val code = conn.responseCode
        val location = conn.getHeaderField("Location")
        if (!location.isNullOrBlank()) return absolute(location)
        val stream = if (code in 200..399) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val match = Regex("https:\\\\?/\\\\?/yandex\\.ru\\\\?/images\\\\?/search\\?[^\\s\"\\\\]+").find(body)
            ?: Regex("https://yandex\\.ru/images/search\\?[^\\s\"]+").find(body)
        return match?.value
            ?.replace("\\/", "/")
            ?.replace("\\u0026", "&")
            ?.let(::absolute)
            ?: "https://yandex.ru/images/"
    }

    private fun absolute(value: String): String {
        return if (value.startsWith("http")) value else "https://yandex.ru$value"
    }
}

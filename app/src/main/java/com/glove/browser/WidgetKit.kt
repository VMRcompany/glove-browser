package com.glove.browser

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

object WidgetKit {
    fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    fun openApp(context: Context, requestCode: Int, configure: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configure()
        }
        return PendingIntent.getActivity(context, requestCode, intent, pendingFlags())
    }

    fun lastCoords(context: Context): Pair<Double, Double>? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var best: android.location.Location? = null
            for (p in providers) {
                try {
                    val loc = lm.getLastKnownLocation(p) ?: continue
                    if (best == null || loc.time > best!!.time) best = loc
                } catch (_: Exception) {
                }
            }
            best?.let { it.latitude to it.longitude }
        } catch (_: Exception) {
            null
        }
    }

    fun weatherIcon(condition: String, label: String): String {
        val c = (condition + " " + label).lowercase()
        return when {
            c.contains("гроз") || c.contains("thunder") -> "⛈️"
            c.contains("снег") || c.contains("snow") || c.contains("град") || c.contains("hail") -> "🌨️"
            c.contains("дожд") || c.contains("лив") || c.contains("rain") || c.contains("drizzle") || c.contains("shower") -> "🌧️"
            c.contains("туман") || c.contains("fog") -> "🌫️"
            c.contains("ясно") || c.contains("clear") || c.contains("солн") -> "☀️"
            c.contains("малообл") || c.contains("partly") -> "🌤️"
            else -> "⛅"
        }
    }
}

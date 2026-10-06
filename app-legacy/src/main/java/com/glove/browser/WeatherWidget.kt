package com.glove.browser

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONObject
import kotlin.concurrent.thread

class WeatherWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            views.setTextViewText(R.id.weatherTemp, "…°")
            views.setTextViewText(R.id.weatherLabel, context.getString(R.string.widget_weather_loading))
            views.setOnClickPendingIntent(
                R.id.weatherRoot,
                WidgetKit.openApp(context, 100 + id) {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("https://yandex.ru/pogoda/")
                }
            )
            manager.updateAppWidget(id, views)
            refresh(context, manager, id)
        }
    }

    companion object {
        fun refresh(context: Context, manager: AppWidgetManager, id: Int) {
            val coords = WidgetKit.lastCoords(context)
            thread(name = "glove-wx-widget") {
                val json = try {
                    JSONObject(
                        Weather.fetchJson(coords?.first, coords?.second)
                    )
                } catch (_: Exception) {
                    JSONObject()
                }
                val temp = if (json.isNull("temp")) null else json.optDouble("temp")
                val label = json.optString("label", "Яндекс Погода")
                val place = json.optString("place", "Glove")
                val condition = json.optString("condition", label)
                val href = json.optString("href", "https://yandex.ru/pogoda/")
                val tempText = if (temp == null || temp.isNaN()) "—°" else {
                    val n = temp.toInt()
                    (if (n > 0) "+$n" else "$n") + "°"
                }
                val views = RemoteViews(context.packageName, R.layout.widget_weather)
                views.setTextViewText(R.id.weatherIcon, WidgetKit.weatherIcon(condition, label))
                views.setTextViewText(R.id.weatherTemp, tempText)
                views.setTextViewText(R.id.weatherLabel, label)
                views.setTextViewText(R.id.weatherPlace, place)
                views.setOnClickPendingIntent(
                    R.id.weatherRoot,
                    WidgetKit.openApp(context, 100 + id) {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(href)
                    }
                )
                manager.updateAppWidget(id, views)
            }
        }
    }
}

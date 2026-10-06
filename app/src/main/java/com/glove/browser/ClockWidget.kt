package com.glove.browser

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) paint(context, manager, id)
        schedule(context)
    }

    override fun onEnabled(context: Context) {
        schedule(context)
    }

    override fun onDisabled(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(tickPending(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TICK ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            if (ids.isEmpty()) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                am.cancel(tickPending(context))
                return
            }
            for (id in ids) paint(context, manager, id)
            schedule(context)
        }
    }

    companion object {
        const val ACTION_TICK = "com.glove.browser.action.CLOCK_TICK"

        private fun paint(context: Context, manager: AppWidgetManager, id: Int) {
            val now = Date()
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            val date = SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(now)
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setTextViewText(R.id.clockTime, time)
            views.setTextViewText(R.id.clockDate, capitalizeRu(date))
            views.setOnClickPendingIntent(R.id.clockRoot, WidgetKit.openApp(context, 200 + id))
            manager.updateAppWidget(id, views)
        }

        private fun capitalizeRu(value: String): String {
            if (value.isEmpty()) return value
            return value.substring(0, 1).uppercase(Locale("ru")) + value.substring(1)
        }

        private fun tickPending(context: Context): PendingIntent {
            val intent = Intent(context, ClockWidget::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(context, 77, intent, WidgetKit.pendingFlags())
        }

        private fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = tickPending(context)
            val trigger = SystemClock.elapsedRealtime() + 60_000L
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                } else {
                    am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
                }
            } catch (_: Exception) {
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
            }
        }
    }
}

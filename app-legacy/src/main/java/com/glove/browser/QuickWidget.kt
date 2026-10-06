package com.glove.browser

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick)
            views.setOnClickPendingIntent(
                R.id.quickYandex,
                WidgetKit.openApp(context, 400 + id) {
                    action = MainActivity.ACTION_WIDGET_TARGET
                    putExtra(MainActivity.EXTRA_WIDGET_TARGET, MainActivity.TARGET_YANDEX)
                }
            )
            views.setOnClickPendingIntent(
                R.id.quickSearch,
                WidgetKit.openApp(context, 401 + id) {
                    action = MainActivity.ACTION_WIDGET_TARGET
                    putExtra(MainActivity.EXTRA_WIDGET_TARGET, MainActivity.TARGET_SEARCH)
                }
            )
            views.setOnClickPendingIntent(
                R.id.quickBookmarks,
                WidgetKit.openApp(context, 402 + id) {
                    action = MainActivity.ACTION_WIDGET_TARGET
                    putExtra(MainActivity.EXTRA_WIDGET_TARGET, MainActivity.TARGET_BOOKMARKS)
                }
            )
            views.setOnClickPendingIntent(
                R.id.quickDownloads,
                WidgetKit.openApp(context, 403 + id) {
                    action = MainActivity.ACTION_WIDGET_TARGET
                    putExtra(MainActivity.EXTRA_WIDGET_TARGET, MainActivity.TARGET_DOWNLOADS)
                }
            )
            manager.updateAppWidget(id, views)
        }
    }
}

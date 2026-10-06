package com.glove.browser

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

class NewsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_news)
            views.setTextViewText(R.id.newsLine1, context.getString(R.string.widget_news_loading))
            views.setTextViewText(R.id.newsLine2, "")
            views.setOnClickPendingIntent(R.id.newsRoot, WidgetKit.openApp(context, 300 + id))
            manager.updateAppWidget(id, views)
            refresh(context, manager, id)
        }
    }

    companion object {
        fun refresh(context: Context, manager: AppWidgetManager, id: Int) {
            NewsFeed.loadHeadlines(2) { items ->
                val views = RemoteViews(context.packageName, R.layout.widget_news)
                if (items.isEmpty()) {
                    views.setTextViewText(R.id.newsLine1, context.getString(R.string.widget_news_empty))
                    views.setTextViewText(R.id.newsLine2, "")
                    views.setOnClickPendingIntent(R.id.newsRoot, WidgetKit.openApp(context, 300 + id))
                } else {
                    views.setTextViewText(R.id.newsLine1, items[0].title)
                    views.setTextViewText(R.id.newsLine2, if (items.size > 1) items[1].title else items[0].source)
                    views.setOnClickPendingIntent(
                        R.id.newsRoot,
                        WidgetKit.openApp(context, 300 + id) {
                            action = Intent.ACTION_VIEW
                            data = Uri.parse(items[0].url)
                        }
                    )
                    if (items.size > 1) {
                        views.setOnClickPendingIntent(
                            R.id.newsLine2,
                            WidgetKit.openApp(context, 320 + id) {
                                action = Intent.ACTION_VIEW
                                data = Uri.parse(items[1].url)
                            }
                        )
                    }
                }
                manager.updateAppWidget(id, views)
            }
        }
    }
}

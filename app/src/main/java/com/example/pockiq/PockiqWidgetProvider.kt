package com.example.pockiq

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PockiqWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Intent for adding Income
        val incomeIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("action", "add_transaction")
            putExtra("type", "INCOME")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val incomePendingIntent = PendingIntent.getActivity(
            context,
            1,
            incomeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_add_income, incomePendingIntent)

        // Intent for adding Expense
        val expenseIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("action", "add_transaction")
            putExtra("type", "EXPENSE")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val expensePendingIntent = PendingIntent.getActivity(
            context,
            2,
            expenseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_add_expense, expensePendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

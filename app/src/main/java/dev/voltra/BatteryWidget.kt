package dev.voltra

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Thread {
            try { BatteryRepository(context).let { repo -> try { repo.sample() } finally { repo.close() } } }
            finally { pending.finish() }
        }.start()
    }
    companion object {
        /** Temperature and health are omitted when the reading or repository cannot provide them. Not a wear score. */
        fun detail(state: String, temperature: Float?, healthPercent: Int?, timeLabel: String): String {
            val temperatureText = temperature?.let { String.format(Locale.getDefault(), "Температура %.1f °C", it) }
            val healthText = healthPercent?.let { "Здоровье $it%" }
            return listOfNotNull(state, timeLabel, temperatureText, healthText).joinToString(" · ")
        }
        fun update(context: Context, reading: Reading, healthPercent: () -> Int? = { null }) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            views.setTextViewText(R.id.widget_level, if (reading.level >= 0) "${reading.level}%" else "—")
            val state = if (reading.full && reading.plugged) "Заряжено" else if (reading.charging) "Зарядка" else if (reading.plugged) "Подключено" else "Разряд"
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(reading.time))
            views.setTextViewText(R.id.widget_detail, detail(state, reading.temperature, healthPercent(), time))
            val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setOnClickPendingIntent(R.id.widget_level, open)
            views.setOnClickPendingIntent(R.id.widget_detail, open)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}

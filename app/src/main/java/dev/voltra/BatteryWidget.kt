package dev.voltra

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import java.text.DateFormat
import java.util.Date

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        BackgroundExecutor.execute(TAG) {
            try {
                runCatching {
                    BatteryRepository.getInstance(app).sample()
                }.onFailure { error ->
                    Log.w(TAG, "Widget update sample failed", error)
                }
            } finally {
                runCatching { pending.finish() }.onFailure { error ->
                    Log.w(TAG, "Widget PendingResult.finish() failed", error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoltraWidget"

        /**
         * Joins non-null detail segments for the widget summary line.
         * Temperature and health are omitted when unavailable. Not a wear score.
         */
        fun detail(
            state: String,
            timeLabel: String,
            temperatureLabel: String? = null,
            healthLabel: String? = null,
        ): String = listOfNotNull(state, timeLabel, temperatureLabel, healthLabel).joinToString(" · ")

        fun update(context: Context, reading: Reading, healthPercent: () -> Int? = { null }) {
            runCatching {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, BatteryWidget::class.java))
                if (ids.isEmpty()) return
                val views = RemoteViews(app.packageName, R.layout.battery_widget)
                val dash = app.getString(R.string.placeholder_dash)
                views.setTextViewText(R.id.widget_level, if (reading.level >= 0) "${reading.level}%" else dash)
                val stateRes = when {
                    reading.full && reading.plugged -> R.string.state_charged
                    reading.charging -> R.string.state_charging
                    reading.plugged -> R.string.state_plugged
                    else -> R.string.state_discharging
                }
                val state = app.getString(stateRes)
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(reading.time))
                val tempUnit = AppPreferences.temperatureUnit(app)
                val temperatureLabel = reading.temperature?.let {
                    app.getString(R.string.widget_temp_label, tempUnit.format(app, it))
                }
                val healthLabel = healthPercent()?.let {
                    app.getString(R.string.widget_health_label, it)
                }
                views.setTextViewText(R.id.widget_detail, detail(state, time, temperatureLabel, healthLabel))
                val open = PendingIntent.getActivity(
                    app,
                    1,
                    Intent(app, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(R.id.widget_root, open)
                views.setOnClickPendingIntent(R.id.widget_level, open)
                views.setOnClickPendingIntent(R.id.widget_detail, open)
                ids.forEach { manager.updateAppWidget(it, views) }
            }.onFailure { error ->
                Log.w(TAG, "Failed to update widget views", error)
            }
        }
    }
}

package dev.voltra

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Locale

object TemperatureAlert {
    private const val CHANNEL = "temperature_alerts"
    private fun prefs(context: Context) = context.getSharedPreferences("battery", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("temp_alert", false)
    fun threshold(context: Context) = prefs(context).getInt("temp_threshold", 40).coerceIn(35, 50)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("temp_alert", value).remove("temp_alert_sent").apply() }
    fun setThreshold(context: Context, value: Int) { prefs(context).edit().putInt("temp_threshold", value.coerceIn(35, 50)).remove("temp_alert_sent").apply() }
    @Synchronized fun onReading(context: Context, reading: Reading) {
        val temperature = reading.temperature ?: return
        val store = prefs(context)
        if (temperature <= threshold(context) - 2) { if (store.getBoolean("temp_alert_sent", false)) store.edit().putBoolean("temp_alert_sent", false).apply(); return }
        if (!enabled(context) || temperature < threshold(context) || store.getBoolean("temp_alert_sent", false)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Температура батареи", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 4, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        manager.notify(82, builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Температура батареи: ${String.format(Locale.getDefault(), "%.1f", temperature)} °C")
            .setContentText("Датчик достиг установленного порога.").setContentIntent(open).setAutoCancel(true).build())
        store.edit().putBoolean("temp_alert_sent", true).apply()
    }
}

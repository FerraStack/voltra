package dev.voltra

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

object BackgroundSampling {
    private const val JOB_ID = 7421
    fun enabled(context: Context): Boolean = context.getSharedPreferences("battery", Context.MODE_PRIVATE).getBoolean("background", false)
    fun scheduled(context: Context): Boolean = (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).getPendingJob(JOB_ID) != null
    fun ensureScheduled(context: Context): Boolean = !enabled(context) || scheduled(context) || setEnabled(context, true)
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (!enabled) {
            scheduler.cancel(JOB_ID)
            context.getSharedPreferences("battery", Context.MODE_PRIVATE).edit().putBoolean("background", false).apply()
            return true
        }
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, BatterySampleJob::class.java)).setPeriodic(15 * 60_000L).setPersisted(true).build()
        val success = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        context.getSharedPreferences("battery", Context.MODE_PRIVATE).edit().putBoolean("background", success).apply()
        return success
    }
}
class BatterySampleJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try { if (BackgroundSampling.enabled(this)) BatteryRepository(this).let { repo -> try { repo.sample() } finally { repo.close() } } }
            finally { jobFinished(params, false) }
        }.start()
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean = false
}

/** Power transitions make session boundaries visible even between scheduled samples. */
class PowerConnectionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action != android.content.Intent.ACTION_POWER_CONNECTED && intent.action != android.content.Intent.ACTION_POWER_DISCONNECTED) return
        if (!BackgroundSampling.enabled(context)) return
        val pending = goAsync()
        Thread {
            try { BatteryRepository(context.applicationContext).let { repo -> try { repo.sample() } finally { repo.close() } } }
            finally { pending.finish() }
        }.start()
    }
}

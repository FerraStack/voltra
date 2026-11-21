package dev.voltra

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal object BackgroundExecutor {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "voltra-bg-worker").apply { isDaemon = true }
    }

    fun execute(tag: String, block: () -> Unit) {
        executor.execute {
            runCatching(block).onFailure { error ->
                Log.w(tag, "Background task failed", error)
            }
        }
    }
}

object BackgroundSampling {
    private const val TAG = "VoltraBackground"
    const val PERIODIC_JOB_ID = 7421
    const val CHARGING_JOB_ID = 7422

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences("battery", Context.MODE_PRIVATE)
            .getBoolean("background", false)

    fun scheduled(context: Context): Boolean {
        val scheduler = context.applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        return scheduler.getPendingJob(PERIODIC_JOB_ID) != null
    }

    fun ensureScheduled(context: Context): Boolean {
        if (!enabled(context)) return true
        if (!scheduled(context)) return setEnabled(context, true)
        ensureChargingJobScheduled(context)
        return true
    }

    fun ensureChargingJobScheduled(context: Context): Boolean {
        if (!enabled(context)) return false
        val app = context.applicationContext
        val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (scheduler.getPendingJob(CHARGING_JOB_ID) != null) return true
        return runCatching {
            val chargingJob = JobInfo.Builder(CHARGING_JOB_ID, ComponentName(app, BatterySampleJob::class.java))
                .setRequiresCharging(true)
                .setPersisted(true)
                .build()
            scheduler.schedule(chargingJob) == JobScheduler.RESULT_SUCCESS
        }.onFailure { error ->
            Log.w(TAG, "Failed to schedule charging trigger job", error)
        }.getOrDefault(false)
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val app = context.applicationContext
        val scheduler = app.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        if (!enabled) {
            runCatching {
                scheduler.cancel(PERIODIC_JOB_ID)
                scheduler.cancel(CHARGING_JOB_ID)
            }.onFailure { error ->
                Log.w(TAG, "Failed to cancel background jobs", error)
            }
            app.getSharedPreferences("battery", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("background", false)
                .apply()
            return true
        }
        val periodicSuccess = runCatching {
            val periodicJob = JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(app, BatterySampleJob::class.java))
                .setPeriodic(15 * 60_000L)
                .setPersisted(true)
                .build()
            scheduler.schedule(periodicJob) == JobScheduler.RESULT_SUCCESS
        }.onFailure { error ->
            Log.w(TAG, "Failed to schedule periodic background job", error)
        }.getOrDefault(false)

        app.getSharedPreferences("battery", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("background", periodicSuccess)
            .apply()

        if (periodicSuccess) {
            ensureChargingJobScheduled(app)
        }
        return periodicSuccess
    }
}

class BatterySampleJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        BackgroundExecutor.execute(TAG) {
            try {
                if (BackgroundSampling.enabled(app)) {
                    runCatching {
                        BatteryRepository.getInstance(app).sample()
                    }.onFailure { error ->
                        Log.w(TAG, "Background sample job failed (jobId=${params.jobId})", error)
                    }
                }
            } finally {
                runCatching { jobFinished(params, false) }.onFailure { error ->
                    Log.w(TAG, "jobFinished failed", error)
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val TAG = "VoltraSampleJob"
    }
}

/**
 * Legacy/pre-Oreo power connection receiver kept safely guarded alongside the
 * charging-constraint JobInfo for Android 8+ (API 26+).
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_POWER_CONNECTED && action != Intent.ACTION_POWER_DISCONNECTED) return
        val app = context.applicationContext
        if (!BackgroundSampling.enabled(app)) return
        val pending = goAsync()
        BackgroundExecutor.execute(TAG) {
            try {
                runCatching {
                    BatteryRepository.getInstance(app).sample()
                    if (action == Intent.ACTION_POWER_DISCONNECTED) {
                        BackgroundSampling.ensureChargingJobScheduled(app)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Power connection sample failed", error)
                }
            } finally {
                runCatching { pending.finish() }.onFailure { error ->
                    Log.w(TAG, "PendingResult.finish() failed", error)
                }
            }
        }
    }

    companion object {
        private const val TAG = "VoltraPowerReceiver"
    }
}

package com.pichash666.notbteb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.annotation.Keep
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

@Keep
class NoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "notice_monitor"
        private const val NOTIFICATION_ID_RESULT = 1001
        private const val NOTIFICATION_ID_NOTICE = 1002
        private const val NOTIFICATION_ID_SYNC = 1003
        private const val CHANNEL_ID_SYNC = "sync_status"

        fun schedule(context: Context, immediate: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val builder = PeriodicWorkRequestBuilder<NoticeWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )

            if (!immediate) {
                builder.setInitialDelay(15, TimeUnit.MINUTES)
            }

            val workRequest = builder.build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val selectedCategory = prefs.getString("selected_category", "All") ?: "All"
        val savedLastUpdate = prefs.getString("last_update", "") ?: ""
        val savedSpecialUpdate = prefs.getString("special_last_update", "") ?: ""

        try {
            // Run as foreground service to prevent being killed by the system
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Ignore if we can't set foreground (e.g. app in background on some versions)
        }

        return try {
            // Update last sync time for UI
            prefs.edit().putLong("last_background_check_time", System.currentTimeMillis()).apply()

            // 1. Check Special Notice Page (Unconditional)
            val resultResponse = NoticeScraper.fetchResults()
            val specialUpdate = resultResponse.lastUpdate
            if (specialUpdate.isNotEmpty() && specialUpdate != savedSpecialUpdate) {
                val latestResult = resultResponse.notices.firstOrNull()
                showNotification(
                    NOTIFICATION_ID_RESULT,
                    "BTEB Result Update",
                    latestResult?.title ?: "New results or updates detected on the BTEB results page."
                )
                prefs.edit().putString("special_last_update", specialUpdate).apply()
            }

            // 2. Check Selected Category
            val response = NoticeScraper.fetchNotices(selectedCategory)
            if (response.lastUpdate.isNotEmpty() && response.lastUpdate != savedLastUpdate) {
                val latestNotice = response.notices.firstOrNull()
                showNotification(
                    NOTIFICATION_ID_NOTICE,
                    "New Notice: $selectedCategory",
                    latestNotice?.title ?: "The BTEB site has been updated."
                )
                prefs.edit().putString("last_update", response.lastUpdate).apply()
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SYNC,
                "Background Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the app is checking for new notices"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BTEB Monitor Active")
            .setContentText("Checking for new notices...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID_SYNC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID_SYNC, notification)
        }
    }

    private fun showNotification(id: Int, title: String, message: String) {
        val channelId = "notice_updates"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notice Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Notifications for new BTEB notices and results"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            id, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }
}

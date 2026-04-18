package com.pichash666.notbteb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.annotation.Keep
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

@Keep
class NoticeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val selectedCategory = prefs.getString("selected_category", "All") ?: "All"
        val savedLastUpdate = prefs.getString("last_update", "") ?: ""
        val savedSpecialUpdate = prefs.getString("special_last_update", "") ?: ""

        try {
            // Update last sync time for UI
            prefs.edit().putLong("last_background_check_time", System.currentTimeMillis()).apply()

            // 1. Check Special Notice Page (Unconditional)
            val specialUpdate = NoticeScraper.fetchSpecialUpdateDate()
            if (specialUpdate.isNotEmpty() && specialUpdate != savedSpecialUpdate) {
                showNotification(
                    "BTEB Result Update",
                    "New results or updates detected on the BTEB results page."
                )
                prefs.edit().putString("special_last_update", specialUpdate).apply()
            }

            // 2. Check Selected Category
            val response = NoticeScraper.fetchNotices(selectedCategory)
            if (response.lastUpdate.isNotEmpty() && response.lastUpdate != savedLastUpdate) {
                val latestNotice = response.notices.firstOrNull()
                showNotification(
                    "New Notice: $selectedCategory",
                    latestNotice?.title ?: "The BTEB site has been updated."
                )
                prefs.edit().putString("last_update", response.lastUpdate).apply()
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "notice_updates"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Notice Updates", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

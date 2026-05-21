package com.pichash666.notbteb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        
        // Handle Boot, Reboot, and App Updates
        val isTriggerAction = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (isTriggerAction) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("notifications_enabled", true)
            if (enabled) {
                // WorkManager with NetworkType.CONNECTED will automatically 
                // wait for internet if it's not currently available.
                NoticeWorker.schedule(context, immediate = true)
            }
        }
    }
}

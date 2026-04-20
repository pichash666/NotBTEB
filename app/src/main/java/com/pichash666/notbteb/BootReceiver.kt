package com.pichash666.notbteb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("notifications_enabled", true)
            if (enabled) {
                NoticeWorker.schedule(context)
            }
        }
    }
}

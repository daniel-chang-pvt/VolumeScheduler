package com.example.volumescheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && RuleRepository.isGlobalEnabled(context)) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}

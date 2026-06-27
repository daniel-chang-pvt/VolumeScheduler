package com.example.volumescheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 手机重启后，系统中已注册的 AlarmManager 任务会丢失。
        // 因此开机完成时，如果总开关开启，就重新注册所有启用规则。
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && RuleRepository.isGlobalEnabled(context)) {
            AlarmScheduler.scheduleAll(context)
        }
    }
}

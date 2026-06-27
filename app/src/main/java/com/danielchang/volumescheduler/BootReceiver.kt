package com.danielchang.volumescheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 手机重启后，系统中已注册的 AlarmManager 任务会丢失。
        // App 更新后，系统也可能清理旧 PendingIntent。因此这些场景都重新注册启用规则。
        val action = intent.action
        if (action != null && action in restartActions && RuleRepository.isGlobalEnabled(context)) {
            AlarmScheduler.scheduleAll(context)
        }
    }

    private val restartActions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED
    )
}

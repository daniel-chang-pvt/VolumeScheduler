package com.example.volumescheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VolumeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_APPLY_RULE) return
        val ruleId = intent.getLongExtra(AlarmScheduler.EXTRA_RULE_ID, -1L)

        // 双重检查总开关：即使系统里残留了旧 PendingIntent，总开关关闭也不会执行。
        if (!RuleRepository.isGlobalEnabled(context)) return

        val rule = RuleRepository.getRules(context).firstOrNull { it.id == ruleId } ?: return
        if (rule.enabled) {
            VolumeController.applyRule(context, rule)

            // AlarmManager 的一次性闹钟触发后会失效，所以执行完要安排下一天。
            AlarmScheduler.scheduleRule(context, rule)
        }
    }
}

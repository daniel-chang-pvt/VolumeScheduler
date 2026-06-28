package com.danielchang.volumescheduler

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
            val applied = VolumeController.applyRule(context, rule)
            RuleRepository.addTriggerLog(
                context,
                if (applied) rule.triggerDescription() else "${rule.timeText()} 触发失败：无法切换为${rule.profile.label}"
            )

            // AlarmManager 的一次性闹钟触发后会失效，所以执行完要安排下一天。
            AlarmScheduler.scheduleRule(context, rule)
        }
    }

    private fun VolumeRule.triggerDescription(): String = when (profile) {
        RingerProfile.SILENT -> "${timeText()} 触发：关闭声音，关闭震动，开启静音"
        RingerProfile.VIBRATE -> "${timeText()} 触发：关闭声音，开启震动"
        RingerProfile.NORMAL -> "${timeText()} 触发：开启声音，关闭震动"
    }
}

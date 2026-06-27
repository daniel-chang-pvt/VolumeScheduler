package com.example.volumescheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AlarmScheduler {
    const val ACTION_APPLY_RULE = "com.example.volumescheduler.APPLY_RULE"
    const val EXTRA_RULE_ID = "rule_id"

    fun scheduleAll(context: Context) {
        val rules = RuleRepository.getRules(context)
        rules.forEach { cancelRule(context, it.id) }
        if (!RuleRepository.isGlobalEnabled(context)) return
        rules.filter { it.enabled }.forEach { scheduleRule(context, it) }
    }

    fun cancelAll(context: Context) {
        RuleRepository.getRules(context).forEach { cancelRule(context, it.id) }
    }

    fun scheduleRule(context: Context, rule: VolumeRule) {
        if (!RuleRepository.isGlobalEnabled(context) || !rule.enabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextTriggerMillis(rule.hour, rule.minute)
        val pendingIntent = pendingIntent(context, rule.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelRule(context: Context, ruleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, ruleId))
    }

    private fun pendingIntent(context: Context, ruleId: Long): PendingIntent {
        val intent = Intent(context, VolumeAlarmReceiver::class.java).apply {
            action = ACTION_APPLY_RULE
            putExtra(EXTRA_RULE_ID, ruleId)
        }
        return PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis
    }
}

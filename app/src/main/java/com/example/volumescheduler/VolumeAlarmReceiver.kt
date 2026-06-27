package com.example.volumescheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VolumeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_APPLY_RULE) return
        val ruleId = intent.getLongExtra(AlarmScheduler.EXTRA_RULE_ID, -1L)
        if (!RuleRepository.isGlobalEnabled(context)) return

        val rule = RuleRepository.getRules(context).firstOrNull { it.id == ruleId } ?: return
        if (rule.enabled) {
            VolumeController.applyRule(context, rule)
            AlarmScheduler.scheduleRule(context, rule)
        }
    }
}

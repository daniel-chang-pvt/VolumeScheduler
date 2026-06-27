package com.example.volumescheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleRepository {
    private const val PREFS_NAME = "volume_scheduler"
    private const val KEY_GLOBAL_ENABLED = "global_enabled"
    private const val KEY_RULES = "rules"

    fun isGlobalEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLOBAL_ENABLED, false)

    fun setGlobalEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()
        if (enabled) {
            AlarmScheduler.scheduleAll(context)
        } else {
            AlarmScheduler.cancelAll(context)
        }
    }

    fun getRules(context: Context): List<VolumeRule> {
        val raw = prefs(context).getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRule())
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveRules(context: Context, rules: List<VolumeRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_RULES, array.toString()).apply()
        if (isGlobalEnabled(context)) {
            AlarmScheduler.scheduleAll(context)
        } else {
            AlarmScheduler.cancelAll(context)
        }
    }

    fun upsertRule(context: Context, rule: VolumeRule) {
        val current = getRules(context)
        val updated = if (current.any { it.id == rule.id }) {
            current.map { if (it.id == rule.id) rule else it }
        } else {
            current + rule
        }.sortedWith(compareBy<VolumeRule> { it.hour }.thenBy { it.minute })
        saveRules(context, updated)
    }

    fun deleteRule(context: Context, ruleId: Long) {
        AlarmScheduler.cancelRule(context, ruleId)
        saveRules(context, getRules(context).filterNot { it.id == ruleId })
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun VolumeRule.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        val volumeJson = JSONObject()
        volumes.forEach { (kind, setting) ->
            volumeJson.put(kind.name, JSONObject().apply {
                put("enabled", setting.enabled)
                put("percent", setting.percent)
            })
        }
        put("volumes", volumeJson)
    }

    private fun JSONObject.toRule(): VolumeRule {
        val volumeJson = optJSONObject("volumes") ?: JSONObject()
        val volumes = VolumeKind.values().associateWith { kind ->
            val settingJson = volumeJson.optJSONObject(kind.name)
            VolumeSetting(
                enabled = settingJson?.optBoolean("enabled", true) ?: true,
                percent = settingJson?.optInt("percent", 50)?.coerceIn(0, 100) ?: 50
            )
        }
        return VolumeRule(
            id = optLong("id", System.currentTimeMillis()),
            enabled = optBoolean("enabled", true),
            hour = optInt("hour", 8).coerceIn(0, 23),
            minute = optInt("minute", 0).coerceIn(0, 59),
            volumes = volumes
        )
    }
}

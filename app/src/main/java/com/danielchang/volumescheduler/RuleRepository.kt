package com.danielchang.volumescheduler

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RuleRepository {
    // SharedPreferences 文件名和 key。项目数据量很小，用 SharedPreferences 足够简单可靠。
    private const val PREFS_NAME = "volume_scheduler"
    private const val KEY_GLOBAL_ENABLED = "global_enabled"
    private const val KEY_RULES = "rules"
    private const val KEY_TRIGGER_LOGS = "trigger_logs"
    private const val MAX_TRIGGER_LOGS = 200

    fun isGlobalEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GLOBAL_ENABLED, false)

    fun setGlobalEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply()

        // 总开关是最高优先级：关闭时取消全部系统闹钟，避免之后继续自动切换。
        if (enabled) {
            AlarmScheduler.scheduleAll(context)
        } else {
            AlarmScheduler.cancelAll(context)
        }
    }

    fun getRules(context: Context): List<VolumeRule> {
        val raw = prefs(context).getString(KEY_RULES, null) ?: return emptyList()

        // 数据损坏时返回空列表，避免 App 启动崩溃。个人工具优先保证可打开可修正。
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

        // 规则变化后必须重新安排 AlarmManager，否则系统里仍可能保留旧时间。
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

    fun hasTimeConflict(context: Context, rule: VolumeRule): Boolean =
        getRules(context).any { it.id != rule.id && it.hour == rule.hour && it.minute == rule.minute }

    fun deleteRule(context: Context, ruleId: Long) {
        AlarmScheduler.cancelRule(context, ruleId)
        saveRules(context, getRules(context).filterNot { it.id == ruleId })
    }

    fun getTriggerLogs(context: Context): List<TriggerLog> {
        val raw = prefs(context).getString(KEY_TRIGGER_LOGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        TriggerLog(
                            timestampMillis = item.optLong("timestampMillis", 0L),
                            description = item.optString("description")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addTriggerLog(context: Context, description: String) {
        val updated = (listOf(TriggerLog(description = description)) + getTriggerLogs(context))
            .take(MAX_TRIGGER_LOGS)
        val array = JSONArray()
        updated.forEach { log ->
            array.put(JSONObject().apply {
                put("timestampMillis", log.timestampMillis)
                put("description", log.description)
            })
        }
        prefs(context).edit().putString(KEY_TRIGGER_LOGS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun VolumeRule.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("enabled", enabled)
        put("hour", hour)
        put("minute", minute)
        put("profile", profile.name)
    }

    private fun JSONObject.toRule(): VolumeRule = VolumeRule(
        id = optLong("id", System.currentTimeMillis()),
        enabled = optBoolean("enabled", true),
        hour = optInt("hour", 8).coerceIn(0, 23),
        minute = optInt("minute", 0).coerceIn(0, 59),
        profile = parseProfile()
    )

    private fun JSONObject.parseProfile(): RingerProfile {
        val profileName = optString("profile", "")
        RingerProfile.values().firstOrNull { it.name == profileName }?.let { return it }

        // 兼容早期版本保存的“音量百分比规则”：旧规则里铃声音量为 0 时迁移为静音，
        // 其他情况迁移为声音。迁移只发生在读取时，不会再保留旧音量控制逻辑。
        val oldRingSetting = optJSONObject("volumes")?.optJSONObject("RING")
        val oldRingPercent = oldRingSetting?.optInt("percent", 100) ?: 100
        return if (oldRingPercent == 0) RingerProfile.SILENT else RingerProfile.NORMAL
    }
}

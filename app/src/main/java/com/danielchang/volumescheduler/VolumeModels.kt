package com.danielchang.volumescheduler

/**
 * 用户在每条规则里选择的目标铃声模式。
 *
 * 这里故意只操作 Android 的 ringerMode：静音、震动、声音。
 * 不保存也不修改任何具体音量百分比，避免影响闹钟、媒体、通知等原有音量设定。
 */
enum class RingerProfile(val label: String) {
    SILENT("静音"),
    VIBRATE("震动"),
    NORMAL("声音")
}

/**
 * 一条每天重复执行的规则。
 *
 * id：用于区分不同规则，也用于生成 AlarmManager 的 PendingIntent requestCode。
 * enabled：单条规则开关。总开关关闭时，即使规则 enabled=true 也不会执行。
 * hour/minute：每天触发的时间。
 * profile：到时间后切换到的铃声模式。
 */
data class VolumeRule(
    val id: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
    val profile: RingerProfile = RingerProfile.NORMAL
) {
    fun timeText(): String = "%02d:%02d".format(hour, minute)
}

/**
 * 一条执行记录。
 *
 * 最新记录显示在界面最上方，用于确认 App 是否真的在后台触发过规则。
 */
data class TriggerLog(
    val timestampMillis: Long = System.currentTimeMillis(),
    val description: String
)

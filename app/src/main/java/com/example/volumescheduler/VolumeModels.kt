package com.example.volumescheduler

enum class RingerProfile(val label: String) {
    SILENT("静音"),
    VIBRATE("震动"),
    NORMAL("声音")
}

data class VolumeRule(
    val id: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
    val profile: RingerProfile = RingerProfile.NORMAL
) {
    fun timeText(): String = "%02d:%02d".format(hour, minute)
}

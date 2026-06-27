package com.example.volumescheduler

import android.media.AudioManager

enum class VolumeKind(
    val label: String,
    val streamType: Int
) {
    RING("铃声音量", AudioManager.STREAM_RING),
    NOTIFICATION("通知/短信音量", AudioManager.STREAM_NOTIFICATION),
    MUSIC("媒体音量", AudioManager.STREAM_MUSIC),
    ALARM("闹钟音量", AudioManager.STREAM_ALARM),
    SYSTEM("系统音量", AudioManager.STREAM_SYSTEM),
    VOICE_CALL("通话音量", AudioManager.STREAM_VOICE_CALL)
}

data class VolumeSetting(
    val enabled: Boolean = true,
    val percent: Int = 50
)

data class VolumeRule(
    val id: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
    val volumes: Map<VolumeKind, VolumeSetting> = VolumeKind.values().associateWith { VolumeSetting() }
) {
    fun timeText(): String = "%02d:%02d".format(hour, minute)
}

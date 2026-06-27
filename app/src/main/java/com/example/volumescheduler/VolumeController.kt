package com.example.volumescheduler

import android.content.Context
import android.media.AudioManager

object VolumeController {
    fun applyRule(context: Context, rule: VolumeRule) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            audioManager.ringerMode = when (rule.profile) {
                RingerProfile.SILENT -> AudioManager.RINGER_MODE_SILENT
                RingerProfile.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerProfile.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            }
        }
    }
}

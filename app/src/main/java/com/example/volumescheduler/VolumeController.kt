package com.example.volumescheduler

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

object VolumeController {
    fun applyRule(context: Context, rule: VolumeRule) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        rule.volumes.forEach { (kind, setting) ->
            if (setting.enabled) {
                setVolume(audioManager, kind, setting.percent)
            }
        }
    }

    private fun setVolume(audioManager: AudioManager, kind: VolumeKind, percent: Int) {
        if (kind == VolumeKind.RING) {
            setRingerModeForRingVolume(audioManager, percent)
        }

        val max = audioManager.getStreamMaxVolume(kind.streamType)
        val volume = (max * percent.coerceIn(0, 100) / 100f).roundToInt().coerceIn(0, max)
        runCatching {
            audioManager.setStreamVolume(kind.streamType, volume, 0)
        }
    }

    private fun setRingerModeForRingVolume(audioManager: AudioManager, percent: Int) {
        runCatching {
            audioManager.ringerMode = if (percent.coerceIn(0, 100) == 0) {
                AudioManager.RINGER_MODE_SILENT
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
        }
    }
}

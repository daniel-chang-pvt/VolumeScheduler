package com.danielchang.volumescheduler

import android.content.Context
import android.media.AudioManager

object VolumeController {
    /**
     * 真正执行规则的地方。
     *
     * 重要设计约束：这里只切换手机响铃模式，不调用 setStreamVolume，
     * 因此不会改变闹钟、媒体、通知、系统等任何音量档位。
     */
    fun applyRule(context: Context, rule: VolumeRule) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            // 某些系统在没有“勿扰/通知策略”权限时可能拒绝修改 ringerMode，
            // runCatching 可以避免后台触发时因系统限制导致进程崩溃。
            audioManager.ringerMode = when (rule.profile) {
                RingerProfile.SILENT -> AudioManager.RINGER_MODE_SILENT
                RingerProfile.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerProfile.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            }
        }
    }
}

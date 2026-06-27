package com.example.volumescheduler

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) render()
    }

    private fun render() {
        val scrollView = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        scrollView.addView(root)
        setContentView(scrollView)

        val title = TextView(this).apply {
            text = "定时铃声模式"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)
        root.addSpace(12)

        addGlobalSwitch()
        root.addSpace(12)

        if (RuleRepository.isGlobalEnabled(this) && !canScheduleExactAlarms()) {
            addExactAlarmNotice()
            root.addSpace(12)
        }

        if (RuleRepository.isGlobalEnabled(this) && !hasNotificationPolicyAccess()) {
            addNotificationPolicyNotice()
            root.addSpace(12)
        }

        root.addView(Button(this).apply {
            text = "添加规则"
            setOnClickListener { showRuleDialog(VolumeRule(id = System.currentTimeMillis())) }
        })
        root.addSpace(12)

        val rules = RuleRepository.getRules(this)
        if (rules.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "还没有规则。添加一个时间点，并选择静音、震动或声音。"
                textSize = 16f
            })
        } else {
            rules.forEach { rule -> addRuleCard(rule) }
        }
    }

    private fun addGlobalSwitch() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFE8EEFF.toInt())
        }
        val text = TextView(this).apply {
            this.text = if (RuleRepository.isGlobalEnabled(this@MainActivity)) {
                "自动音量控制\n已开启：到时间会自动执行启用的规则"
            } else {
                "自动音量控制\n已关闭：不会自动修改手机音量"
            }
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        row.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = RuleRepository.isGlobalEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                RuleRepository.setGlobalEnabled(this@MainActivity, checked)
                render()
            }
        })
        root.addView(row)
    }

    private fun addExactAlarmNotice() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFFFF1CC.toInt())
        }
        box.addView(TextView(this).apply {
            text = "建议允许“闹钟和提醒”权限"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "未允许时，省电模式下触发时间可能延迟。"
        })
        box.addView(Button(this).apply {
            text = "去设置"
            setOnClickListener { openExactAlarmSettings() }
        })
        root.addView(box)
    }

    private fun addNotificationPolicyNotice() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFFFE5E5.toInt())
        }
        box.addView(TextView(this).apply {
            text = "建议允许“勿扰/通知策略”权限"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "部分手机需要此权限，应用才能切换静音、震动、声音模式。"
        })
        box.addView(Button(this).apply {
            text = "去设置"
            setOnClickListener { openNotificationPolicySettings() }
        })
        root.addView(box)
    }

    private fun addRuleCard(rule: VolumeRule) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFF4F4F4.toInt())
            setOnClickListener { showRuleDialog(rule) }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = rule.timeText()
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(Switch(this).apply {
            isChecked = rule.enabled
            setOnClickListener { it.parent.requestDisallowInterceptTouchEvent(true) }
            setOnCheckedChangeListener { _, checked ->
                RuleRepository.upsertRule(this@MainActivity, rule.copy(enabled = checked))
                render()
            }
        })
        card.addView(top)
        card.addView(TextView(this).apply {
            text = rule.summary()
            textSize = 15f
        })
        card.addView(TextView(this).apply {
            text = "点击卡片编辑"
            textSize = 12f
        })
        card.addView(Button(this).apply {
            text = "立即应用此规则"
            setOnClickListener {
                VolumeController.applyRule(this@MainActivity, rule)
            }
        })
        root.addView(card)
        root.addSpace(10)
    }

    private fun showRuleDialog(initialRule: VolumeRule) {
        var enabled = initialRule.enabled
        var profile = initialRule.profile

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }

        val enabledSwitch = Switch(this).apply {
            text = "规则启用"
            isChecked = enabled
            setOnCheckedChangeListener { _, checked -> enabled = checked }
        }
        container.addView(enabledSwitch)

        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hourInput = numberInput(initialRule.hour.toString(), "小时 0-23")
        val minuteInput = numberInput(initialRule.minute.toString(), "分钟 0-59")
        timeRow.addView(hourInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        timeRow.addSpace(8)
        timeRow.addView(minuteInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        container.addView(timeRow)
        container.addSpace(10)

        container.addView(TextView(this).apply {
            text = "到时间切换为"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        val profileGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        RingerProfile.values().forEachIndexed { index, item ->
            profileGroup.addView(RadioButton(this).apply {
                id = index + 1
                text = item.label
                isChecked = item == profile
            })
        }
        profileGroup.setOnCheckedChangeListener { _, checkedId ->
            profile = RingerProfile.values().getOrElse(checkedId - 1) { RingerProfile.NORMAL }
        }
        container.addView(profileGroup)

        val scrollView = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("编辑规则")
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val hour = hourInput.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 8
                val minute = minuteInput.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
                RuleRepository.upsertRule(
                    this,
                    initialRule.copy(
                        enabled = enabled,
                        hour = hour,
                        minute = minute,
                        profile = profile
                    )
                )
                render()
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("删除") { _, _ ->
                RuleRepository.deleteRule(this, initialRule.id)
                render()
            }
            .show()
    }

    private fun numberInput(value: String, hintText: String): EditText = EditText(this).apply {
        setText(value)
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        setSelectAllOnFocus(true)
    }

    private fun VolumeRule.summary(): String {
        return "到点切换为：${profile.label}"
    }

    private fun LinearLayout.addSpace(dp: Int) {
        addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(dp)))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun backgroundDrawable(color: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
        }

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    private fun hasNotificationPolicyAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun openNotificationPolicySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }
}

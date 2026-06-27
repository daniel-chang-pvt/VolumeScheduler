package com.example.volumescheduler

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
        if (RuleRepository.isGlobalEnabled(this)) {
            // 从系统设置页返回、App 更新后首次打开等场景，都主动刷新一次系统闹钟注册。
            AlarmScheduler.scheduleAll(this)
        }
        if (::root.isInitialized) render()
    }

    private fun render() {
        // 本项目没有使用 XML 布局或 Compose，界面全部用原生 View 动态创建。
        // 好处是依赖少、构建快；缺点是界面代码会集中在这个 Activity 中。
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

        if (RuleRepository.isGlobalEnabled(this) && !hasNotificationPolicyAccess()) {
            // 切换静音/震动模式在部分系统上属于“通知策略”相关能力。
            addNotificationPolicyNotice()
            root.addSpace(12)
        }

        if (RuleRepository.isGlobalEnabled(this) && !isIgnoringBatteryOptimizations()) {
            // 某些厂商系统会在锁屏后限制后台广播，加入电池优化白名单可提高触发成功率。
            addBatteryOptimizationNotice()
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
        // 总开关改变时会立即注册或取消系统闹钟，逻辑在 RuleRepository.setGlobalEnabled 中。
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFE8EEFF.toInt())
        }
        val text = TextView(this).apply {
            this.text = if (RuleRepository.isGlobalEnabled(this@MainActivity)) {
                "自动铃声模式\n已开启：到时间会自动执行启用的规则"
            } else {
                "自动铃声模式\n已关闭：不会自动切换手机模式"
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

    private fun addBatteryOptimizationNotice() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = backgroundDrawable(0xFFFFF1CC.toInt())
        }
        box.addView(TextView(this).apply {
            text = "建议允许“忽略电池优化”"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "部分手机锁屏后会限制后台触发，允许后更容易准时执行。"
        })
        box.addView(Button(this).apply {
            text = "去设置"
            setOnClickListener { openBatteryOptimizationSettings() }
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
        // 首页每张卡片代表一条每日重复规则。点击卡片编辑，右侧开关只控制该规则。
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
                // 测试入口：不用等定时时间到，直接执行同一套规则应用逻辑。
                VolumeController.applyRule(this@MainActivity, rule)
            }
        })
        root.addView(card)
        root.addSpace(10)
    }

    private fun showRuleDialog(initialRule: VolumeRule) {
        // 对话框中先在局部变量里暂存编辑结果，只有点击“保存”才写入 SharedPreferences。
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
                // 输入非法时回退到默认 08:00；正常情况下数字输入框已限制为数字。
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

    private fun hasNotificationPolicyAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(requestIntent) }
                .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    private fun openNotificationPolicySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }
}

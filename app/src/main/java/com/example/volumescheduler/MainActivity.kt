package com.example.volumescheduler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VolumeSchedulerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSchedulerApp() {
    val context = LocalContext.current
    var globalEnabled by remember { mutableStateOf(RuleRepository.isGlobalEnabled(context)) }
    var rules by remember { mutableStateOf(RuleRepository.getRules(context)) }
    var editingRule by remember { mutableStateOf<VolumeRule?>(null) }

    fun refresh() {
        globalEnabled = RuleRepository.isGlobalEnabled(context)
        rules = RuleRepository.getRules(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("定时音量") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlobalSwitchCard(
                    enabled = globalEnabled,
                    onChange = {
                        RuleRepository.setGlobalEnabled(context, it)
                        refresh()
                    }
                )
            }

            if (globalEnabled && !canScheduleExactAlarms(context)) {
                item { ExactAlarmPermissionCard(context) }
            }

            item {
                Button(
                    onClick = { editingRule = VolumeRule(id = System.currentTimeMillis()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("添加规则")
                }
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        text = "还没有规则。添加一个时间点，并设置各类音量百分比。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { enabled ->
                            RuleRepository.upsertRule(context, rule.copy(enabled = enabled))
                            refresh()
                        },
                        onEdit = { editingRule = rule }
                    )
                }
            }
        }
    }

    editingRule?.let { rule ->
        RuleEditorDialog(
            initialRule = rule,
            onDismiss = { editingRule = null },
            onSave = { saved ->
                RuleRepository.upsertRule(context, saved)
                editingRule = null
                refresh()
            },
            onDelete = {
                RuleRepository.deleteRule(context, rule.id)
                editingRule = null
                refresh()
            }
        )
    }
}

@Composable
private fun GlobalSwitchCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动音量控制", fontWeight = FontWeight.Bold)
                Text(
                    text = if (enabled) "已开启：到时间会自动执行启用的规则" else "已关闭：不会自动修改手机音量",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ExactAlarmPermissionCard(context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("建议允许“闹钟和提醒”权限", fontWeight = FontWeight.Bold)
            Text("未允许时，省电模式下触发时间可能延迟。")
            OutlinedButton(onClick = { openExactAlarmSettings(context) }) {
                Text("去设置")
            }
        }
    }
}

@Composable
private fun RuleCard(rule: VolumeRule, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Card(onClick = onEdit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rule.timeText(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            Text(rule.summary(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("点击卡片编辑", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RuleEditorDialog(
    initialRule: VolumeRule,
    onDismiss: () -> Unit,
    onSave: (VolumeRule) -> Unit,
    onDelete: () -> Unit
) {
    var enabled by remember { mutableStateOf(initialRule.enabled) }
    var hourText by remember { mutableStateOf(initialRule.hour.toString()) }
    var minuteText by remember { mutableStateOf(initialRule.minute.toString()) }
    var volumes by remember { mutableStateOf(initialRule.volumes.withDefaults()) }
    val hour = hourText.toIntOrNull()?.coerceIn(0, 23)
    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59)
    val canSave = hour != null && minute != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialRule.id == 0L) "添加规则" else "编辑规则") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("规则启用", modifier = Modifier.weight(1f))
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.filter(Char::isDigit).take(2) },
                            label = { Text("小时 0-23") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.filter(Char::isDigit).take(2) },
                            label = { Text("分钟 0-59") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                VolumeKind.values().forEach { kind ->
                    item {
                        val setting = volumes[kind] ?: VolumeSetting()
                        VolumeSettingRow(
                            kind = kind,
                            setting = setting,
                            onChange = { changed -> volumes = volumes + (kind to changed) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onSave(
                        initialRule.copy(
                            enabled = enabled,
                            hour = hour ?: 8,
                            minute = minute ?: 0,
                            volumes = volumes
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun VolumeSettingRow(
    kind: VolumeKind,
    setting: VolumeSetting,
    onChange: (VolumeSetting) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(kind.label, fontWeight = FontWeight.Bold)
                Text("${setting.percent}%", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = setting.enabled,
                onCheckedChange = { onChange(setting.copy(enabled = it)) }
            )
        }
        Slider(
            enabled = setting.enabled,
            value = setting.percent.toFloat(),
            onValueChange = { onChange(setting.copy(percent = it.toInt().coerceIn(0, 100))) },
            valueRange = 0f..100f,
            steps = 19
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun VolumeRule.summary(): String {
    val enabledVolumes = VolumeKind.values().mapNotNull { kind ->
        val setting = volumes[kind]
        if (setting?.enabled == true) "${kind.label} ${setting.percent}%" else null
    }
    return if (enabledVolumes.isEmpty()) "不修改任何音量" else enabledVolumes.joinToString(" / ")
}

private fun Map<VolumeKind, VolumeSetting>.withDefaults(): Map<VolumeKind, VolumeSetting> =
    VolumeKind.values().associateWith { this[it] ?: VolumeSetting() }

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

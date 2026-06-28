# 项目说明

本项目是一个 Android 定时铃声模式工具。它允许用户创建多条每日规则，在指定时间把手机切换为 `静音`、`震动` 或 `声音`。

当前应用包名：

```text
com.danielchang.volumescheduler
```

## 当前功能

- 总开关：关闭后取消所有自动触发。
- 多条每日规则：每条规则有独立时间、启用状态和目标模式。
- 同一个时分只能存在一条规则，避免同一时间多条规则互相覆盖。
- 目标模式：静音、震动、声音。
- 到点触发：使用 `AlarmManager.setAlarmClock`，不使用常驻后台服务。
- 开机恢复：手机重启后重新注册启用规则。
- 触发记录：每次定时触发后写入一条记录，最新记录显示在最上方。
- 不修改任何音量档位，尤其不影响闹钟音量和闹钟原有设定。

## 设计原则

- 低资源占用：不常驻后台，不轮询时间。
- 最小权限：只申请当前功能需要的权限。
- 不影响闹钟：只切换 `AudioManager.ringerMode`，不调用 `setStreamVolume`。
- 方便云端构建：GitHub Actions 直接输出 release APK。

## 主要文件

- `app/src/main/AndroidManifest.xml`
  - 声明 Activity、BroadcastReceiver 和权限。

- `MainActivity.kt`
  - 主界面。
  - 动态创建原生 Android View，不依赖 XML 布局或 Jetpack Compose。
  - 提供总开关、规则列表、添加/编辑规则、权限提示和触发记录。

- `VolumeModels.kt`
  - 数据模型。
  - `RingerProfile` 表示静音、震动、声音。
  - `VolumeRule` 表示一条每日规则。

- `RuleRepository.kt`
  - 本地数据读写。
  - 使用 `SharedPreferences` 保存总开关、规则 JSON 和触发记录 JSON。
  - 规则变化后负责触发重新注册定时任务。

- `AlarmScheduler.kt`
  - 定时任务注册和取消。
  - 使用 `AlarmManager.setAlarmClock` 创建每条规则的系统闹钟级触发。
  - 触发一次后由 Receiver 再注册下一天。

- `VolumeAlarmReceiver.kt`
  - 定时触发入口。
  - 到时间后读取规则并调用 `VolumeController.applyRule`。
  - 执行后安排下一天同一时间。

- `VolumeController.kt`
  - 真正切换手机铃声模式的地方。
  - 只设置 `AudioManager.ringerMode`。

- `BootReceiver.kt`
  - 处理手机重启后的定时任务恢复。

## 执行流程

用户保存规则后：

```text
MainActivity
  -> RuleRepository.upsertRule
  -> RuleRepository.saveRules
  -> AlarmScheduler.scheduleAll
  -> AlarmManager.setAlarmClock 注册系统闹钟级触发
```

到设定时间后：

```text
Android 系统唤醒 App
  -> VolumeAlarmReceiver.onReceive
  -> 检查总开关和规则开关
  -> VolumeController.applyRule
  -> AlarmScheduler.scheduleRule 注册下一天
```

手机重启后：

```text
Android 发送 BOOT_COMPLETED
  -> BootReceiver.onReceive
  -> 如果总开关开启，则 AlarmScheduler.scheduleAll
```

## 数据结构

规则保存为 JSON 数组，格式类似：

```json
[
  {
    "id": 1710000000000,
    "enabled": true,
    "hour": 22,
    "minute": 30,
    "profile": "SILENT"
  }
]
```

`profile` 可选值：

- `SILENT`：静音
- `VIBRATE`：震动
- `NORMAL`：声音

触发记录保存为 JSON 数组，最多保留最近 200 条，格式类似：

```json
[
  {
    "timestampMillis": 1710000000000,
    "description": "22:30 触发：关闭声音，关闭震动，开启静音"
  }
]
```

界面显示时按数组顺序展示，最新记录在最上方。

## 权限说明

- `MODIFY_AUDIO_SETTINGS`
  - 允许应用修改铃声模式。

- `ACCESS_NOTIFICATION_POLICY`
  - 部分 Android 版本或厂商系统需要用户手动允许后，应用才能切换静音/震动相关模式。

- `RECEIVE_BOOT_COMPLETED`
  - 手机重启后重新注册定时任务。

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  - 请求用户把应用加入电池优化白名单，提高锁屏和后台触发成功率。

## 为什么不使用后台服务

这个应用每天只需要在少量固定时间点执行一次。如果使用常驻后台服务，会更耗电，也更容易被 Android 后台限制杀掉。

当前方案使用 `AlarmManager.setAlarmClock`：平时 App 不运行，到点后系统按闹钟级别唤醒一次，执行完成后结束。这是兼顾省资源和锁屏可靠性的方式。

`setAlarmClock` 的代价是部分手机会显示一个即将到来的闹钟图标。这是系统认为该触发需要可靠执行的表现。

## 为什么不修改音量

早期版本曾支持设置各类音量百分比，但实际测试发现部分手机即使铃声音量为 0，来电仍可能响铃。同时用户明确要求不能影响闹钟原有设定。

因此当前版本只切换系统铃声模式，不调用任何音量流设置 API。代码中不应再出现：

- `setStreamVolume`
- `STREAM_ALARM`
- `STREAM_MUSIC`
- `STREAM_NOTIFICATION`
- `STREAM_RING`

如果后续要重新加入音量功能，必须单独评估是否会影响闹钟和厂商系统行为。

## 二次开发入口

- 修改界面文案或布局：看 `MainActivity.kt`。
- 增加规则字段：先改 `VolumeRule`，再改 `RuleRepository.toJson/toRule`，最后改编辑界面。
- 修改触发记录格式：看 `TriggerLog`、`RuleRepository.addTriggerLog` 和 `VolumeAlarmReceiver.triggerDescription`。
- 修改触发策略：看 `AlarmScheduler.kt`。
- 修改到点后执行的系统行为：看 `VolumeController.kt`。
- 修改云端构建：看 `.github/workflows/build-apk.yml`。

## 常见问题

### 熄屏能否触发

代码使用 `AlarmManager.setAlarmClock`，比普通 `setExactAndAllowWhileIdle` 更适合锁屏和深度待机场景。仍建议允许“忽略电池优化”，部分国产系统还需要允许自启动、后台运行。

如果用户手动“强行停止”应用，Android 会取消该应用的后台触发能力，直到下次手动打开 App。

### 为什么需要勿扰/通知策略权限

部分手机把切换静音/震动模式视为通知策略能力。如果没有授权，系统可能拒绝修改模式。App 首页会在需要时显示入口。

### 关闭总开关后会恢复原来的模式吗

不会。总开关只控制是否继续自动触发，不会恢复或改变当前手机模式。

### 会影响闹钟吗

代码不修改闹钟音量。闹钟是否响铃由系统闹钟 App 和用户原本设置决定。

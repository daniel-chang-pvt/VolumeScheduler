# 定时音量

一个低资源占用的 Android 定时音量控制应用。

## 功能

- 总开关：关闭后取消所有自动触发，不再修改手机音量。
- 多条规则：可以添加多个每天触发的时间点。
- 每条规则有独立启用开关。
- 每条规则可以分别设置这些音量：铃声、通知/短信、媒体、闹钟、系统、通话。
- 每种音量可以单独启用或关闭，并设置 0% 到 100%。
- 到点后使用 `AlarmManager` 唤醒一次，设置音量后结束，不常驻后台。
- 手机重启后，如果总开关开启，会自动重新注册启用的规则。

## 构建 APK

本项目已配置 GitHub Actions，不需要在本地安装 Android Studio。

1. 把本目录内容推送到 GitHub 仓库。
2. 打开 GitHub 仓库页面。
3. 进入 `Actions`。
4. 选择 `Build APK`。
5. 如果没有自动运行，点击 `Run workflow`。
6. 构建完成后，在页面底部 `Artifacts` 下载 `VolumeScheduler-debug-apk`。
7. 解压后得到 `app-debug.apk`，安装到 Android 手机。

## 本地推送示例

如果你的仓库地址是 `https://github.com/你的用户名/VolumeScheduler.git`，可以在本目录运行：

```bash
git add .
git commit -m "Initial Android project"
git branch -M main
git remote add origin https://github.com/你的用户名/VolumeScheduler.git
git push -u origin main
```

如果已经设置过 `origin`，不要重复执行 `git remote add origin`，直接 `git push` 即可。

## 手机权限说明

安装后建议检查这些设置：

- 允许安装未知来源应用。
- Android 12 及以上建议允许“闹钟和提醒”权限，否则省电模式下触发可能延迟。
- 部分国产系统可能需要允许“自启动”“后台运行”或加入电池优化白名单。

## 注意事项

- 关闭总开关只会停止自动控制，不会恢复或改变当前音量。
- 不同 Android 版本和手机品牌对铃声、通知、系统音量的联动规则不同，某些音量可能会被系统合并控制。
- 通话音量通常只有通话场景下最明显，部分系统可能限制应用修改。
- debug APK 适合自己安装测试，不适合直接上架应用商店。

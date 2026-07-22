# 无线 ADB 工具箱 v1.0

这是一个直接运行在 Android 设备上的 ADB Host 客户端，用于管理同一局域网内、由你拥有或已获授权管理的另一台 Android 设备。

## 完整功能

- 在“传统 ADB”标签输入 IP 与端口（默认 5555）连接传统 TCP ADB。
- Android 11+ 配对码配对：输入配对端口和 6 位配对码。
- 使用 Android 系统文件选择器选择本机 APK，通过 ADB Sync push 到远程路径。
- 可执行 `pm install -r` 安装推送后的 APK。
- 执行任意 ADB shell 命令；支持 `shell_v2` 时实时显示 stdout、stderr 与退出码。
- 快速查看制造商、型号、设备代号、Android 版本、SDK、屏幕分辨率、密度和电池状态。
- 终端风格日志，支持自动滚动、暂停、复制、保存、清空和长度保护。
- ADB 主机密钥保存在 App 私有目录，重启后复用同一授权身份。

## Android 11+ 使用步骤

1. 在远程设备打开“开发者选项 → 无线调试”。
2. 点击“使用配对码配对设备”。
3. 在 App 的“Android 11+”页输入 IP、配对页面显示的配对端口和 6 位配对码，点击“第一步：配对”。
4. 返回远程设备的无线调试主页面，查看“IP 地址和端口”。
5. 将主页面显示的端口填入 App 的“连接端口”，点击“连接”。

配对端口和连接端口通常不同；关闭或重启无线调试后，端口也可能变化。

## 传统无线 ADB 使用步骤（Android 10 及以下）

1. 使用 USB 调试在目标设备执行 `adb tcpip 5555`，或确认目标设备已开启传统 TCP ADB。
2. 在 App 的“传统 ADB”页输入目标设备局域网 IP，端口保留为 `5555`（除非设备使用其他端口）。
3. 点击“连接传统 ADB”。

## 源代码位置

主要文件：

- `app/src/main/java/com/yongwei/adbtoolbox/MainActivity.java`：标签页界面、WindowInsets、APK、Shell、设备信息和日志逻辑。
- `app/src/main/java/com/yongwei/adbtoolbox/KadbBridge.kt`：KADB 的同步桥接层。
- `app/src/main/java/com/yongwei/adbtoolbox/AdbToolboxApp.kt`：ADB 主机密钥存储初始化。

界面由 `MainActivity.java` 以原生 Android View 动态构建；旧的 `activity_main.xml` 仅保留作参考，不参与当前页面渲染。

## Android Studio 构建

要求：

- Android Studio
- JDK 17 或更新版本
- Android SDK Platform 36
- Android Build Tools 36.0.0
- 可访问 Google Maven、Maven Central 和 JitPack

```bash
./gradlew assembleDebug
```

输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

项目配置：

- 包名：`com.yongwei.adbtoolbox`
- versionCode：`2`
- versionName：`1.1.0`
- minSdk：`23`
- targetSdk：`35`
- compileSdk：`36`

## 安全提醒

ADB shell 可以安装应用、修改设置，并访问调试权限允许读取的数据。仅连接你拥有或明确获授权管理的设备。不要在公共网络开放传统 5555 端口；使用结束后建议关闭无线调试。

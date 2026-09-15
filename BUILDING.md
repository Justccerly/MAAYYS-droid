# Android 构建说明

## Windows 环境（2026-09-15 配置）

工程使用 AGP 8.7.3、Kotlin 2.0.21、compileSdk/targetSdk 35 和 Gradle Wrapper 8.9。

本机已安装：

- Zulu OpenJDK 17.0.20.1：`C:\Users\su\AppData\Local\Programs\Java\zulu17.68.203-ca-jdk17.0.20.1-win_x64`；
- Android SDK：`C:\Users\su\AppData\Local\Android\Sdk`；
- Android SDK Platform 35、Build Tools 34.0.0（AGP 默认版本）及 35.0.0；
- Platform Tools（包含 ADB）、Command-line Tools 16.0（兼容 JDK 17）。

已设置用户级 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT` 和 `Path`，并更新本机 `local.properties`。新开终端可使用这些设置；从仍持有旧环境的应用打开终端时，先运行下面的 `env.ps1`。原用户环境变量备份保存在 `%LOCALAPPDATA%\android-agent-setup\user-env-before.json`。

## 完整构建前置条件

需要安装：

- JDK 17；
- Gradle 8.9（或通过 Wrapper）；
- Android SDK Platform 35；
- Android Build Tools 34.0.0（AGP 8.7.3 默认使用）；
- 可访问 Google Maven 与 Maven Central。

## 构建命令

PowerShell 中执行：

```powershell
cd C:\Users\su\Desktop\android-agent
. .\env.ps1
.\gradlew.bat :app:assembleDebug
```

建议随后执行：

```powershell
.\gradlew.bat :testDebugUnitTest :app:testDebugUnitTest :lintDebug :app:lintDebug
```

产物预期位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

核心库的测试位于根模块，所以必须包含 `:testDebugUnitTest`；仅执行 `:app:testDebugUnitTest` 不会运行这批测试。正常开发优先使用上述 Gradle 命令，两个 Python 验证脚本需要另外准备编译器 JAR，不是完整 Android 构建的前置条件。

## 安卓移植版构建输入

当前版本通过 JNA 接入官方 MaaFramework，完整 Go Agent 从 MaaYYs 上游源码交叉编译。需要额外安装 Go 1.24+ 和 NDK 27.2.12479018。首次准备或重新生成被忽略的二进制输入：

```powershell
python scripts/prepare-framework.py
python scripts/prepare-resources.py
.\scripts\build-agent.ps1
. .\env.ps1
.\gradlew.bat :app:assembleDebug :testDebugUnitTest :lintDebug :app:lintDebug
```

`prepare-resources.py --source <目录>` 可使用已核对提交的 MaaYYs 源码目录。固定上游提交、依赖版本及修改见 `UPSTREAM_VERSIONS.json` 和 `THIRD_PARTY_NOTICES.md`。包内包含 32 项被 interface.json 导入的任务、8 个区服配置及完整 Agent；这些数量表示已接入的配置，不代表所有任务已完成设备回归。

NDK 的 llvm-strip 会破坏本次官方 OCR 库重定位段的文件对齐，因此打包保留官方 `.so` 原始字节。Go Agent 为 Android PIE 可执行文件，以 `libmaayys_agent.so` 打包并从只读安装目录启动，兼容 Android 禁止执行应用可写目录文件的限制。

连接测试设备后运行原生集成测试：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

设备测试分别验证真实阴阳师资源加载/完整 Agent 注册，以及固定图像回调、原生 pipeline 和上游 RandomWait 动作的执行与释放。固定图像测试不能代替实际游戏画面识别、Root 输入或真机任务回归。当前 Root 主链路仍使用原有 Shell 控制器，MAA-Meow 特权截图/输入桥、Shizuku 和 ADB 接入尚未完成。

## 本机验证结果（2026-09-15）

- `:app:assembleDebug` 成功，生成 `app/build/outputs/apk/debug/app-debug.apk`。
- `:testDebugUnitTest`：10 个测试类、41 项测试，失败、错误和跳过均为 0。报告：`build/reports/tests/testDebugUnitTest/index.html`。
- `:app:testDebugUnitTest`：`NO-SOURCE`，应用模块没有独立单元测试。
- Build Tools 35.0.0 的 `apksigner verify --verbose` 校验成功，APK 使用 v2 调试签名。
- `:lintDebug :app:lintDebug --rerun-tasks` 成功，无错误；库模块有 5 条警告，应用模块有 14 条警告，涉及界面字符串、图标、数据提取规则和冗余 SDK 版本判断。报告分别位于 `build/reports/lint-results-debug.html`、`app/build/reports/lint-results-debug.html`。
- Windows SDK 路径已按 properties 格式转义盘符冒号：`sdk.dir=C\:/Users/su/AppData/Local/Android/Sdk`。


## 更新源

首页资源卡片提供 Mirror酱和 GitHub 两个来源，二选一。Mirror酱使用官方 `GET https://mirrorchyan.com/api/resources/{res_id}/latest` 接口；CDK 只在当前页面内存中使用，不写入配置。GitHub 使用 `https://api.github.com/repos/TanyaShue/MaaYYs/releases/latest` 的 `win-x86_64` 资源包。

MaaFramework Core 使用同一个来源选择：GitHub 从 `MaaXYZ/MaaFramework` 最新发布按 ABI 选择 `MAA-android-aarch64-*` 或 `MAA-android-x86_64-*`；Mirror酱使用资源 ID `MaaFramework` 和 Android ABI。Core 下载后按 ABI 解压到独立目录，下一次启动加载；正在运行的 Core 不会被覆盖。

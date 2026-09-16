# MAAYYS-droid

MAAYYS-droid 是 [MaaYYs](https://github.com/TanyaShue/MaaYYs) 的 Android 移植版，用于在 Android 手机或模拟器上运行《阴阳师》自动化任务。

项目沿用 MaaYYs 的任务、Pipeline、图片模板、OCR 模型和自定义 Agent；Android 权限、后台运行、虚拟显示器和触控能力参考并复用 [MAA-Meow](https://github.com/Aliothmoon/MAA-Meow) 的实现。

> 本项目仍处于开发阶段。自动化行为可能受游戏版本、客户端渠道、设备 ROM 和权限状态影响，请自行承担使用风险。

## 功能

- **MaaYYs 任务**：加载原项目的日常、副本、活动和其他任务。
- **多客户端兼容**：支持官服、TapTap、Bilibili、华为、应用宝、OPPO、VIVO 等各大渠道服客户端。后台空间启动时会自动探测识别已安装的游戏客户端。
- **任务配置**：任务启用开关（Jetpack Compose 实时响应动效）、搜索筛选、任务选项和参数保存。
- **后台运行**：通过独立虚拟显示器运行阴阳师，手机前台可以继续正常使用聊天或看视频；支持实时预览、全屏手动操作、返回前台和关闭后台。
- **定时运行**：任务页的启用状态和参数会直接继承到定时任务；支持周一至周日和多个自定义 `HH:mm` 时间点。
- **控制方式**：支持 Root 和 Shizuku。控制器必须由用户明确选择，不会静默切换权限。
- **更新**：MaaYYs 资源和 MaaFramework Core 分开更新，提供 Mirror 酱 / GitHub 二选一；Core 按设备 ABI 暂存并在下次启动切换。
- **日志**：运行状态、错误和原生运行日志可在应用内查看。

## 使用要求

- Android 8.0（API 26）或更高版本；后台独立显示器建议 Android 12 或更高版本。
- 设备支持：主要适配主流手机 `arm64-v8a` 架构（已剔除 x86_64 二进制冗余，安装包体积由 360MB+ 缩减至约 190MB）。
- Root 设备，或已启动并授权的 Shizuku。
- 手机安装与所选资源一致的阴阳师客户端。
- 后台模式需要设备和 ROM 允许创建虚拟显示器及移动应用任务。

## 使用方法

1. 安装 `MAAYYS-droid-debug.apk`。
2. 打开“设置”，选择 Root 或 Shizuku，并完成对应授权。
3. 在“任务”页选择游戏区服，打开需要运行的任务开关，进入任务详情填写参数。
4. **前台运行**：直接点击任务详情中的“开始任务”。
5. **后台运行**：
   - 打开“后台”页直接点击“启动后台游戏”唤起虚拟游戏空间（无需强行预先保存单项任务）。
   - 游戏加载完成后，点击“开始任务”即可开始挂机；自动化会优选已保存或首个启用的任务运行。
   - 手动操作时点击预览右上角按钮即可进入全屏，点击悬浮叉号返回后台预览。
6. **定时运行**：打开“定时”页，选择周几和任意多个时间点并保存。任务范围始终继承“任务”页的开关状态。

## 更新资源和 Core

首页的资源卡片提供 Mirror 酱和 GitHub 两个更新来源，二选一。Mirror 酱检查版本时可不填写 CDK；下载资源需要有效 CDK。CDK 仅保存在当前页面内存中，不写入配置文件。

MaaYYs 资源更新只替换任务和识别资源，不需要重新编译 Agent。MaaFramework Core 更新按设备 ABI 下载，停止运行后暂存，应用下次启动时才切换；如果新 Core 启动失败会回退到原版本。
Mirror 酱使用官方接口：
```text
GET https://mirrorchyan.com/api/resources/{res_id}/latest
```
GitHub 资源来自 `TanyaShue/MaaYYs`，Core 来自 `MaaXYZ/MaaFramework`。

## CI / CD 与开发构建

本项目已配置 **GitHub Actions CI/CD 流水线**，自动拉取最新 MaaFramework 原生库与 MaaYYs 规则资源，编译 Go Agent 原生二进制并输出单架构轻量化 APK。

### 本地构建命令
环境要求：JDK 17、Android SDK Platform 35、Build Tools 34.0.0、NDK 27.2.12479018、Go 1.24+。
```powershell
cd C:\Users\su\Desktop\android-agent
. .\env.ps1
python scripts/prepare-framework.py
python scripts/prepare-resources.py --source C:\path\to\MaaYYs
.\scripts\build-agent.ps1
.\gradlew.bat :app:assembleDebug
```
APK 输出路径：
```text
app/build/outputs/apk/debug/MAAYYS-droid-debug.apk
```

## 项目结构
```text
app/                 Android 应用、Compose UI、任务服务
src/main/kotlin/     MaaFramework、资源管理和任务运行时
meow-platform/       从 MAA-Meow 复用的虚拟显示器、截图、输入和特权服务层
hidden-api/          Android 隐藏 API 编译接口
upstream/maayys/     固定版本的 MaaYYs Go Agent 源码
scripts/             上游资源、Core 和 Agent 构建脚本
.github/workflows/   GitHub Actions CI 自动化构建流程
```

## 当前限制
- 物理手机上的 Root 管理器、Shizuku 和不同 ROM 还需要逐设备验证。
- 完整一键日常任务的长时间运行、断连恢复和应用重启恢复仍在验证中。
- Android 应用自身 APK 的在线更新暂未接入；当前更新流程针对 MaaYYs 资源和 MaaFramework Core。
- Android 端不支持 MaaYYs 原项目中的 Windows/MXU 桌面客户端功能。

## 上游和许可证
上游版本、源码修改和许可证见：
- [PROJECT_PLAN.md](PROJECT_PLAN.md)
- [UPSTREAM_VERSIONS.json](UPSTREAM_VERSIONS.json)
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- [BUILDING.md](BUILDING.md)

MaaYYs 使用 MIT 许可证；MaaFramework 和 Go 绑定使用 LGPL-3.0；MAA-Meow 主项目使用 AGPL-3.0，复用的第三方代码保留其原许可证。分发修改后的版本时请同时提供对应源码和许可证文本。

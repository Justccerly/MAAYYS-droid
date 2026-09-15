# MaaYYs Android 实施规划

## 目标与验收

目标是在 Android 设备上运行 MaaYYs 的真实阴阳师任务。Root 授权、APK 打包、模拟控制器测试都不能代替这个目标。

用户在 2026-09-15 明确要求优先参考和复用 MAA-Meow，避免重复实现已有的 Android 自动化基础设施。

## 已核对的上游

- MAA-Meow：https://github.com/Aliothmoon/MAA-Meow
- 本次参考提交：`ef4ba0e7750ffae4561657ca33e25b342880f6d5`。
- 本机完整参考源码：`C:/Users/su/Desktop/MAA-Meow-reference`。通过提交固定的源码 ZIP 获取；不是已成功建立的 Git checkout。
- MaaYYs：https://github.com/TanyaShue/MaaYYs；本机现有资源位于 `C:/Users/su/Desktop/新建文件夹/MAyys`，其 interface.json 声明版本为 `v3.16.0-beta.1`。
- MaaFramework：https://github.com/MaaXYZ/MaaFramework；本次核对到官方 `v5.13.0` 发布包含 Android aarch64 和 x86_64 预编译包。

## 复用决策

MAA-Meow 的 Android 宿主能力应优先复用或适配；MaaYYs 的流程资源和自定义 agent 保持其原有语义。

| 能力 | 上游证据 | 本项目实施方向 |
| --- | --- | --- |
| Root 权限与服务启动 | `manager/RootManager.kt`、`SuSpawner.kt`、`RootRemoteServiceConnector.kt` | 采用 libsu 和独立服务模式，替换主页面散落的 su 调用 |
| Shizuku 服务 | `ShizukuManager.kt`、`ShizukuProcessServiceConnector.kt`、`ProcessServiceConnectorBackend.kt` | 复用授权、服务连接、断连与进程清理机制 |
| 截图和输入桥 | `native/bridge_capture.*`、`bridge_frame_buffer.*`、`bridge_input.*`、`bridge.h` | 优先适配现有桥接；核对 ABI、显示器、旋转、坐标和生命周期 |
| 原生库加载 | `remote/MaaCoreManager.kt`、`maa/MaaCoreLibrary.java` | 采用 JNA 调用官方原生 API 的方案，替换本项目无实现的 JNI 声明 |
| Android 界面与服务 | `overlay/`、`domain/service/`、`remote/` | 复用任务状态、日志、悬浮控制与生命周期设计，任务配置改由 MaaYYs 资源驱动 |
| 游戏流程 | 本地 `interface.json`、`tasks/`、`resource_pack/` | 复用 MaaYYs 现有流程、模板、OCR 模型和任务配置 |

### 必须处理的接口差异

MAA-Meow 的 `MaaCoreLibrary.java` 调用 `AsstCreateEx`、`AsstAppendTask`、`AsstStart`，对应明日方舟 MAA Core。
MaaYYs 使用 MaaFramework 的 pipeline 和自定义 Action/Recognition。不能直接将 MaaYYs 资源复制进 MAA-Meow 就宣称可以运行，也不能把两个项目的同名 MaaCore 库混用。

MAA-Meow `bridge.h` 已明确对齐 MaaFramework AndroidExternalLib 的触控参数，这为复用底层桥提供了依据；实际 ABI 与运行效果仍需验证。

复用源文件时保留许可证和版权声明。MAA-Meow 主项目为 AGPL-3.0，部分 scrcpy 派生代码保留 Apache-2.0；具体来源以其 `THIRD_PARTY_NOTICES.md` 为准。目前这里只完成源码参考，尚未把上游实现集成到 APK。

## 实施与完成证据

- [x] 配置 Windows 构建环境，生成并校验开发 APK。
- [x] 通过当前 41 项单元测试和 Android lint（仍有警告）。
- [x] 用户确认当前 APK 在目标设备获得 Root 权限。
- [x] 核对 MAA-Meow 的源码、许可证、原生接口及可复用模块。
- [ ] 接入官方 Android MaaFramework 原生包和实际 API 绑定；证明设备端加载、版本查询、资源加载、任务执行及退出成功。
- [ ] 接入 MAA-Meow 的 Root/Shizuku 服务与截图触控方案；证明实际截图可解码，点击/滑动坐标正确，授权撤销和停止能清理服务。
- [ ] 完成 MaaYYs 资源选择、安装和校验，支持 interface.json 声明的多层资源路径。
- [ ] 从 MaaYYs 上游复用或移植全部被所支持任务引用的自定义 Action/Recognition；列出缺失项并显式阻止不支持的任务，不静默成功。
- [ ] 完成任务列表、参数配置、执行、停止、日志和错误展示；启动失败必须能在界面看到具体原因。
- [ ] 接通并验证 ADB 模式的连接、授权、截图和输入；不以占位适配器视为完成。
- [ ] 在目标设备运行一个真实阴阳师任务，保留任务、资源版本、设备信息及执行日志作为首个完整流程的验收证据。
- [ ] 对计划支持的任务和控制模式完成回归、取消、断连、权限撤销及应用生命周期验证，产出可安装 APK、构建说明及源码许可材料。

后续以本清单及下方设备证据判断完成情况，不再使用未经需求权重核算的完成百分比。

## 2026-09-15 安卓执行链路进展

- 已在 AGENTS.md 固定项目定位：MaaYYs 是移植主体，MAA-Meow 是 Android 宿主参考。
- 完整保存 MaaYYs 上游 Agent 源码，所有 Action/Recognition 源文件保持原内容；只调整入口库路径和 Go 绑定的 Android 库名称识别。
- 已生成 ARM64 / x86_64 Android PIE Agent，注册 25 个动作、6 个识别器，不再以三个 Kotlin 基础动作代替上游 Agent。
- 已接入官方 MaaFramework 5.13.0 和 JNA 实际 API，删除没有实现的 JNI 占位类。
- 已打包原始资源、32 个导入任务和 8 个区服，新增从上游定义生成的任务与参数界面，支持 input/select/switch/checkbox、嵌套选项、数值类型及配置保存。
- Android 15 / x86_64 MuMu：原生集成测试已验证真实官服资源加载、25+6 注册项、固定图像截图回调、原始 Go RandomWait 执行以及停止释放。设备上直接运行了 2 项测试，均通过。
- JVM 测试增加到 44 项并通过；上游 Go 测试通过；lint 无错误。
- 设备测试发现并修复两项问题：NDK strip 破坏官方 OCR 库段对齐；异步停止任务尚未完成就释放 Tasker 导致 native crash。

上述证据尚不证明真实手机的阴阳师任务已完成。Root 点击/滑动仍由现有 Shell 控制器提供；MAA-Meow 的特权服务、截图输入桥、Shizuku/ADB、悬浮控制和一键日常队列仍须继续实施与验证。真机实际任务验收保持待完成。


## UI 与后台功能进展（2026-09-15）

- 已参考 MaaMeow 重做 Compose Material 3 UI：首页、任务、后台、设置四页，统一卡片、导航、主题、任务搜索和参数面板；已在竖屏和横屏模拟器检查界面。
- 已移植 MaaMeow 的虚拟显示器和特权服务平台层（39 个上游文件、Root token Binder 启动、Shizuku 服务入口、NativeBridge 截图/输入、独立游戏前台服务）。Root 平台测试已验证虚拟屏截图、点击、主屏保留 MaaYYs、阴阳师在独立 display 上运行以及返回主屏。
- 当前 UI 后台页已实测显示阴阳师画面，提供手动触控、任务选择、返回前台和关闭后台操作；相关证据在 `build/ui-review/` 和 `build/background-validation/`。
- 最新 APK：`app/build/outputs/apk/debug/app-debug.apk`，`assembleDebug`、Android 测试 APK 和 lint 均通过。

真实手机上的 Root 管理器授权、Shizuku 绑定、ROM 隐藏 API 和长时间完整一键日常仍需继续验证。

- CDK 只保存在当前页面的内存状态，不写入 SharedPreferences；错误消息和运行日志不会输出 CDK。

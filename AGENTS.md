# 项目定位与实施约束

本项目是 **TanyaShue/MaaYYs 的 Android 移植**。用户已明确确认：

- 阴阳师任务、资源、配置与自定义 Agent 来自 MaaYYs；优先沿用上游源码与行为，不能重新设计一套脚本替代。
- Android 宿主权限、截图、触控、后台服务与界面优先参考和复用 Aliothmoon/MAA-Meow，避免重复造轮子。
- MaaYYs 使用 MaaFramework，MAA-Meow 的明日方舟任务 API 不能直接替代 MaaFramework。
- 完成标准是 MaaYYs 的真实功能在 Android 上运行。Root 授权测试、构建成功、模拟测试通过都只是阶段证据。
- 保留上游来源、提交版本、许可证与必要的修改说明。

保持完整安卓化目标，不能将演示、权限测试或少量动作支持重新定义为项目完成。

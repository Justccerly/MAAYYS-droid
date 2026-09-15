# 上游来源与修改

本工程移植 MaaYYs 的阴阳师功能。版本和提交固定于 `UPSTREAM_VERSIONS.json`。

| 组件 | 来源与许可 | 使用方式 / 修改 |
| --- | --- | --- |
| MaaYYs | https://github.com/TanyaShue/MaaYYs ，MIT | 原任务、资源和完整 Go Agent。`upstream/maayys/agent/main.go` 增加 Android 原生库目录环境变量；`go.mod` 指向本地兼容绑定。所有 Action/Recognition Go 源码保持上游内容。许可证在 `upstream/maayys/LICENSE`。 |
| maa-framework-go | https://github.com/MaaXYZ/maa-framework-go ，LGPL-3.0 | `upstream/maa-framework-go` 保存完整绑定源码。四处动态库名称选择增加 Android；修改日期 2026-09-15。原许可在其 `LICENSE.md`。 |
| MaaFramework | https://github.com/MaaXYZ/MaaFramework ，LGPL-3.0 | 官方 v5.13.0 Android 原生包，保持库文件原始字节，包含其 OpenCV、ONNX Runtime、FastDeploy 等随包依赖。对应框架源码：https://github.com/MaaXYZ/MaaFramework/tree/v5.13.0 。构建与依赖来源见该版本源码及官方发布包。 |
| JNA | https://github.com/java-native-access/jna ，LGPL-2.1-or-later / Apache-2.0 双许可 | Maven AAR 5.18.1，通过官方 C API 连接 MaaFramework；保留依赖自带的许可材料。 |
| Gson | https://github.com/google/gson ，Apache-2.0 | ProjectInterface 和参数 JSON 解析。 |
| MAA-Meow | https://github.com/Aliothmoon/MAA-Meow ，AGPL-3.0；部分 scrcpy 派生文件为 Apache-2.0 | 当前参考其 JNA 原生加载与宿主分层方案。尚未复制其特权进程、截图输入桥或界面源文件；这些模块的复用仍在实施清单中，不能宣称已集成。 |

APK 中的 `assets/licenses/` 包含 MaaYYs、MaaFramework 和 Go 绑定许可文本。上游自带资源中的许可与声明随资源包一并保留。分发原生依赖及修改后的绑定时，应同时提供相应源码和构建材料；本目录的源码快照、版本记录和脚本属于移植交付物。

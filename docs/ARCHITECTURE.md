# Android 架构

## 技术选型

- Kotlin：应用主语言
- Jetpack Compose：界面
- CameraX：预览、图像分析、录像
- Room：试飞、飞机档案和事件数据
- MediaStore / app-specific storage：视频文件
- LiteRT：模型选定并完成许可证审查后接入本地推理

CameraX 官方概览：https://developer.android.com/media/camera/camerax
LiteRT 官方文档：https://developers.google.com/edge/litert

## 分层

- `ui/`：Compose 页面与 UI state
- `camera/`：CameraX 生命周期、录像与帧采集
- `detection/`：模型接口、预处理、检测结果
- `tracking/`：跨帧目标关联与置信度
- `timing/`：时间基准、事件候选、人工修正
- `analysis/`：二维轨迹与报告计算
- `data/`：Room entities、DAO、repositories
- `storage/`：视频 URI、文件生命周期

## 数据流

CameraX ImageAnalysis → frame timestamp + image → detector → tracker → flight event candidates → timing review UI → Room session + video URI.

录像时间线和图像分析帧时间戳需要明确关联。不可用 Compose 重绘时间、按钮响应时间或播放器播放位置替代原始采集时间。

## 线程与性能

- 图像分析必须使用有界队列并及时关闭 ImageProxy。
- 不在主线程执行模型推理、视频 IO 或数据库批量操作。
- 记录丢帧数、帧时间戳间隔、相机配置和设备信息。
- 对不同机型测量真实吞吐量，不能假设所有设备支持相同分辨率/FPS。

## 隐私

首版优先本地存储。录像可能包含旁观者、场地和位置信息；提供清晰的保存/删除行为，不默认上传。

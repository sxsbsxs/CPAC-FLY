# Android 架构

## 技术选型

- Kotlin：应用主语言
- Jetpack Compose：界面
- CameraX：预览、图像分析、录像
- Google ML Kit Object Detection 17.0.2：Android 端目标检测 API
- ML Kit bundled generic object detector：使用 STREAM_MODE 连续视频追踪，SDK 在连续帧间分配 tracking ID；模型随应用打包，离线可用
- ARCore Depth API（可选能力）：支持设备上的逐帧深度图；运行时检查能力，不作为所有设备的硬依赖
- Room：试飞、飞机档案和事件数据
- MediaStore / app-specific storage：视频文件
- OpenCV：按需用于相机标定、图像几何和视觉追踪；引入前审查版本与许可证

CameraX：https://developer.android.com/media/camera/camerax
Google ML Kit Object Detector：https://developers.google.com/edge/mediapipe/solutions/vision/object_detector
ARCore Depth：https://developers.google.com/ar/develop/depth

## 分层

- `ui/`：Compose 页面与 UI state
- `camera/`：CameraX 生命周期、录像与帧采集
- `calibration/`：相机内参、畸变、地面参考面和场地尺度标定
- `depth/`：ARCore 能力探测、深度图采集、深度置信/有效性检查
- `detection/`：Google ML Kit Object Detector 封装、预处理、检测结果
- `tracking/`：跨帧目标关联与置信度；人工点选目标作为通用模型失效时的兜底
- `altitude/`：飞机检测区域深度采样、相机坐标到地面相对高度转换、质量评估
- `flightmetrics/`：高度滤波、下降率和剩余时间预测
- `timing/`：固定机位/手持跟拍模式、时间戳事件候选、相机运动补偿接口、人工逐帧修正
- `analysis/`：二维轨迹、高度/下降率曲线与报告
- `data/`：Room entities、DAO、repositories
- `storage/`：视频 URI、文件生命周期

## 第一阶段预训练模型边界

EfficientDet-Lite0 是 COCO 80 类通用检测模型，不是纸飞机专用模型。它可以作为 Android 端推理和检测框管线的现成基线，但 COCO 中的 airplane 类不等于纸飞机类别，快速移动、很小或形状特殊的纸飞机可能完全漏检或被错分。因此：
- 不把通用模型的 airplane 框直接宣称为可靠的纸飞机识别。
- 先用它验证模型加载、图像预处理、检测结果和设备端延迟。
- 飞机目标无法被模型稳定识别时，下一步接入用户点选 ROI + 视觉追踪；再根据真实误检样本评估是否需要少量微调。
- 只有经过纸飞机专门测试集验证后，才开放“自动纸飞机识别”标识。

## 数据流

CameraX frame + capture timestamp → detector → tracker/manual ROI fallback → optional ARCore depth sample → calibrated ground-relative altitude → robust temporal filter → descent-rate estimator → remaining-time predictor → live UI + Room samples.

录像时间线和图像分析帧时间戳需要明确关联。不可用 Compose 重绘时间、按钮响应时间或处理完成时间替代原始采集时间。ARCore 深度帧与相机帧必须按时间戳和视场参数对齐。

## 高度估计策略

1. 在试飞前引导用户固定手机、选择后置摄像头并标定场景；在画面中识别/指定地面参考面。
2. 检查设备是否支持 ARCore Depth，并在运行时启用；不支持时使用可用的相机标定与已知尺度方案。
3. 在飞机检测框/关键点周围取稳健深度样本，剔除背景混入、无效深度和低置信像素。
4. 利用相机内参、设备姿态和地面平面，将相机坐标中的三维点转换为相对地面的垂直高度。
5. 记录原始深度、转换结果、地面平面、质量分数和算法版本；不满足条件时输出 unavailable/relative-only。
6. 不把相机距离（斜距）直接称为离地高度。

## 实时性能

- 目标 UI 更新至少 1 Hz；设备允许时分析目标 10–30 Hz，实际 FPS 自适应。
- 使用有界队列和明确的丢帧策略，优先处理最新帧，防止积压导致数据显示滞后。
- 分析必须异步执行并及时关闭 ImageProxy；不在主线程执行推理、深度处理、视频 IO 或数据库批量操作。
- 记录采集时间戳、处理延迟、帧间隔、丢帧数、深度有效像素比例和设备信息。
- 对不同机型测量吞吐量、温升和电量；性能不足时降低分析分辨率/频率，但不改变原始录像质量。

## 隐私

首版优先本地存储。录像可能包含旁观者、场地和位置信息；提供清晰的保存/删除行为，不默认上传。


## 视频复核与手持相机运动估计

- 试飞记录保存人工复核的出手/落地视频时间戳；人工修正时长按两个时间戳之差重新计算。
- 录像复核界面使用 `MediaExtractor` 枚举视频 sample PTS，提供按实际 sample 时间戳前后移动和时间线拖动；预览帧由 `MediaMetadataRetriever` 按最近时间戳解码。容器/编码可能存在 sample 与显示帧差异，仍需真实设备和可变帧率视频验证。
- `GlobalMotionEstimator` 是纯 Kotlin 灰度帧块匹配平移估计基线，已接入 CameraX 分析帧并显示实时诊断，输出归一化平移、置信度和可靠性标志。它只能近似处理平移型镜头运动，不处理旋转、缩放和显著视差；目前尚未作为 `FlightTimingAnalyzer` 的可信相机运动补偿输入，不可把输出当作已完成的手持跟拍计时补偿。

- 试飞记录可关联用户建立的纸飞机档案；拍摄前可选择机型，手动结束和离镜自动停止均保存机型 ID/名称。


## ML Kit 模型与离线运行

- 使用 `com.google.mlkit:object-detection:17.0.2` 随应用打包的检测模型，无需首次启动下载。
- 模型文件随应用安装，不需要模型下载器或网络权限。
- 目标检测与追踪在 Android 设备本地执行，不使用推理 API、不上传图像或视频；安装后可离线运行。
- 模型随应用安装，不存在首次模型下载失败状态。
- 当前模型仍是 COCO 通用模型，不能代表折纸飞机专用识别。

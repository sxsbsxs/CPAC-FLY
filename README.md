# CPAC FLY

**CPAC FLY** is an Android-first smart flight timing and training analysis app for paper-airplane enthusiasts.

CPAC FLY aims to help users record flights, review video, understand flight paths, estimate ground-relative altitude and descent trends, and improve hang-time through repeatable training.

> **Project status:** early development. CameraX preview/frame analysis, optional CameraX video capture with MediaStore output, generic frame-to-frame tracking, manual flight timing, and local flight-session history are implemented. The latest code still requires CI and physical-device verification. The current pretrained detector is a generic COCO baseline, not a paper-airplane-specific model; real-device behavior and paper-airplane recognition are not yet validated.

## First release scope

- Phone's built-in camera only (external cameras are a future extension).
- CameraX rear-camera preview, live frame analysis, and local video recording to Movies/CPAC FLY when the device supports the combined use cases.
- Flight session and paper-aircraft records stored locally.
- A manual start/landing flight timer with local session persistence (automatic event detection is still pending).
- Live estimated ground-relative altitude, descent rate, remaining-flight-time range, and confidence/quality status.
- Video review, slow-motion playback, 2D image-plane trajectory, altitude and descent-rate plots.
- On-device generic object-detection baseline using MediaPipe EfficientDet-Lite0, plus IoU-based generic object tracking; paper-airplane-specific recognition remains unvalidated.
- Training history and session reports.
- No leaderboard integration in the initial release.
- No account or cloud backend required for the first local-testing milestone.

## 尚未完成的工作（当前真实状态）

目前仍未完成的工作包括：补齐并固定 Gradle Wrapper 与 Android Studio 构建环境、完成 Debug APK 安装和多机型真机验证；评估通用 COCO 模型在真实纸飞机视频中的召回率与误检率，并开发/接入合法可用的纸飞机专用检测模型及模型评测数据；实现手动 ROI 辅助追踪与目标丢失恢复；把固定机位/手持跟拍双模式算法接入真实视频帧，完成手持相机全局运动估计与补偿、固定机位地面区域标定、触地证据识别、可变帧率与视频 PTS 时间戳校准；开发视频时间线、逐帧慢放、出手/落地候选确认与人工修正；完善飞出镜头连续未检测自动停止（5 秒至 2 分钟可选）的纸飞机专用识别基础、遮挡/漏检处理和真机验证；使用 Room 完成试飞记录结构化存储、数据库迁移、飞机档案增删改查、记录编辑/删除、录像文件清理以及高度样本和预测结果持久化；接入 ARCore Depth（设备支持时）、相机内参与地面标定、深度图和飞机区域对齐、相对地面垂直高度估算及质量置信度；完成高度/下降率/剩余时间实时曲线、二维轨迹、慢动作回放、预测与实测误差报告和训练历史对比；最后完成不同手机、拍摄方式、光照、背景、距离及纸飞机类型下的计时/测高误差测试、无障碍与隐私检查、签名配置和内测 APK 发布。上述项目均未完成或尚未通过真实设备验证，不应视为现有功能；其中通用 COCO 检测器、纯 Kotlin 计时/高度算法和飞出镜头倒计时目前仅构成开发基础，不能保证真实纸飞机识别、自动计时或测高准确性。

## Technology direction

- Kotlin and Jetpack Compose
- CameraX for camera preview, image analysis, and video capture
- Optional ARCore Depth API on supported devices, with runtime capability checks
- Room for local structured data
- Android media storage for recorded video
- MediaPipe Tasks Vision for the initial on-device detection baseline; model limitations and license evidence are documented

CameraX: https://developer.android.com/media/camera/camerax
ARCore Depth: https://developers.google.com/ar/develop/depth
MediaPipe Object Detector: https://developers.google.com/edge/mediapipe/solutions/vision/object_detector

## Repository layout

```text
app/                 Android application
docs/                Product, architecture, timing, and release documentation
ml/                  Dataset, training, evaluation, and model metadata (no large datasets committed)
.github/workflows/   CI build workflow
```

## Build

Open this repository in a current stable Android Studio installation, allow Gradle sync, then run the `app` configuration on an Android device or emulator.

The source includes CameraX preview/frame analysis/video capture, a MediaPipe detector wrapper, a first-launch checksum-verified EfficientDet-Lite0 model installer, generic object tracking, a manual timer, and pure-Kotlin descent/remaining-time estimators with unit tests. GitHub Actions has successfully run unit tests and assembled a Debug APK. A recent CI run previously succeeded, but the latest changes must pass CI and the APK must be installed and verified on physical devices before being treated as a test release. A Gradle wrapper and physical-device installation/verification are still pending.

## Important measurement limitations

A normal phone camera produces a 2D video view. Absolute ground-relative height requires reliable scale and scene geometry; camera-to-aircraft range is not the same as vertical height above ground. ARCore Depth can provide per-pixel depth estimates on supported devices, but quality depends on device capability, motion, range, and scene texture. When calibration or depth quality is insufficient, CPAC FLY must show relative trend or unavailable status rather than invent a precise metre value.

Descent rate must be derived from timestamped height samples with smoothing. Remaining flight time is a conditional estimate: it should be shown as a range with confidence only when descent is stable and the landing/termination model is applicable. Otherwise the app should explain why it cannot estimate reliably.

The app must distinguish:
1. **Automatic estimate** — computer-vision result requiring review.
2. **Video-reviewed result** — result checked and corrected by the user.
3. **Official result** — a result confirmed under the applicable competition rules by an authorized judge or organizer.

## Open-source and third-party components

This repository is public, but public visibility does **not** itself grant permission to reuse its code. No repository-wide license is declared yet. Before adding a third-party library, model, dataset, or copied source file, record its exact version, origin, license, notices, and redistribution obligations in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A project license decision is still required from the maintainer.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Until a project license is selected, contributors should not assume that the repository's source is available for unrestricted reuse.

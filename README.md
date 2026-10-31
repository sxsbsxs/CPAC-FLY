# CPAC FLY

**CPAC FLY** is an Android-first smart flight timing and training analysis app for paper-airplane enthusiasts.

CPAC FLY aims to help users record flights, review video, understand flight paths, estimate ground-relative altitude and descent trends, and improve hang-time through repeatable training.

> **Project status:** early development. The first milestone is an installable Android test build. Automatic paper-airplane recognition, timing, and altitude estimation are not yet validated features. The current pretrained detector is a generic COCO baseline, not a paper-airplane-specific model.

## First release scope

- Phone's built-in camera only (external cameras are a future extension).
- Camera preview and video recording.
- Flight session and paper-aircraft records stored locally.
- A timing workflow with manual correction.
- Live estimated ground-relative altitude, descent rate, remaining-flight-time range, and confidence/quality status.
- Video review, slow-motion playback, 2D image-plane trajectory, altitude and descent-rate plots.
- On-device generic object-detection baseline using MediaPipe EfficientDet-Lite0; paper-airplane-specific recognition remains unvalidated.
- Training history and session reports.
- No leaderboard integration in the initial release.
- No account or cloud backend required for the first local-testing milestone.

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

The source now includes a MediaPipe detector wrapper and a checksum-pinned EfficientDet-Lite0 model download task. A Gradle wrapper and CI build verification are still pending. Do not treat an unverified source snapshot as a tested APK.

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

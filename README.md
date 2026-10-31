# CPAC FLY

**CPAC FLY** is an Android-first smart flight timing and training analysis app for paper-airplane enthusiasts.

CPAC FLY aims to help users record flights, review video, understand flight paths, and improve hang-time through repeatable training.

> **Project status:** early development. The first milestone is an installable Android test build. Automatic aircraft detection and timing are planned capabilities, not yet validated features.

## First release scope

- Phone's built-in camera only (external cameras are a future extension).
- Camera preview and video recording.
- Flight session and paper-aircraft records stored locally.
- A timing workflow with manual correction.
- Video review, slow-motion playback, and 2D image-plane trajectory visualization.
- On-device aircraft detection and tracking after a suitable, license-reviewed model has been evaluated.
- Training history and session reports.
- No leaderboard integration in the initial release.
- No account or cloud backend required for the first local-testing milestone.

## Technology direction

- Kotlin and Jetpack Compose
- CameraX for camera preview, image analysis, and video capture
- Room for local structured data
- Android media storage for recorded video
- LiteRT for on-device inference once the model and its license are approved

CameraX is the recommended starting point for new Android camera applications. See the [CameraX overview](https://developer.android.com/media/camera/camerax). LiteRT provides on-device inference; see [LiteRT documentation](https://developers.google.com/edge/litert).

## Repository layout

```text
app/                 Android application
docs/                Product, architecture, timing, and release documentation
ml/                  Dataset, training, evaluation, and model metadata (no large datasets committed)
.github/workflows/   CI build workflow
```

## Build

Open this repository in a current stable Android Studio installation, allow Gradle sync, then run the `app` configuration on an Android device or emulator.

The first source scaffold is intentionally small. A Gradle wrapper and CI workflow will be added once the Android build toolchain is pinned and verified. Do not treat an unverified source snapshot as a tested APK.

## Important measurement limitations

A normal phone camera produces a 2D video view. A single camera view cannot, by itself, establish a paper airplane's true 3D trajectory, physical speed, lift, or drag. Timing must use capture timestamps and clearly defined event rules; frame drops, variable frame rate, occlusion, and off-screen flight can affect accuracy. The app must expose uncertainty and allow user correction.

The app must distinguish:
1. **Automatic estimate** — computer-vision result requiring review.
2. **Video-reviewed result** — result checked and corrected by the user.
3. **Official result** — a result confirmed under the applicable competition rules by an authorized judge or organizer.

## Open-source and third-party components

This repository is public, but public visibility does **not** itself grant permission to reuse its code. No repository-wide license is declared yet. Before adding a third-party library, model, dataset, or copied source file, record its exact version, origin, license, notices, and redistribution obligations in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). A project license decision is still required from the maintainer.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Until a project license is selected, contributors should not assume that the repository's source is available for unrestricted reuse.

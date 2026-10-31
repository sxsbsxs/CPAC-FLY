# Contributing to CPAC FLY

Thank you for helping improve CPAC FLY.

## Before contributing

- Discuss substantial features or architecture changes in an issue first.
- Keep pull requests focused and explain how changes were tested.
- Do not commit personal flight videos, faces, location data, credentials, signing keys, or large training datasets.
- Do not add a dependency, model, dataset, or copied source without recording its provenance and license in `THIRD_PARTY_NOTICES.md`.
- Do not assume that public repository visibility grants a license to reuse project code. The project-wide license has not yet been selected.

## Engineering principles

- Prefer Kotlin, Jetpack Compose, CameraX, and Room for the Android first release.
- Keep camera capture, frame analysis, tracking, timing, persistence, and UI as separate responsibilities.
- Use monotonic/capture timestamps for timing; never derive flight time from UI animation or screen refresh.
- Make uncertain detections visible and support manual correction.
- Do not claim 3D measurements from a single uncalibrated camera view.
- Add unit tests for timing and data transformations; add device tests for camera behavior.

## Pull request checklist

- [ ] Scope and user impact are explained.
- [ ] Build/test results are reported, including device/API level when relevant.
- [ ] New dependencies and model assets have license/provenance records.
- [ ] No secrets, personal data, or large raw datasets are included.
- [ ] Accessibility and error states are considered.

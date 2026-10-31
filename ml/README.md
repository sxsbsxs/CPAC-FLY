# On-device object tracking

## Selected implementation

CPAC FLY uses Google ML Kit Object Detection 17.0.2 with `STREAM_MODE`.

- API: https://developers.google.com/ml-kit/vision/object-detection/android
- Model: ML Kit's built-in generic object detector, shipped through the SDK's supported model installation path.
- Runtime: Android device; no inference server, account, paid API, user model training, or video upload.
- Tracking: ML Kit assigns a tracking ID to an object across successive video frames.
- Camera mode: stream mode tracks the prominent object; multiple-object detection is intentionally disabled to preserve real-time performance.
- Offline: model/runtime is provided through the app dependency's supported packaging path; the app itself does not download an external TFLite file at startup.

## Known limitations

This is generic object detection and tracking, not paper-airplane classification. A small, fast, blurred, folded paper airplane may not be detected reliably. Coarse object labels do not establish that an object is a paper airplane.

The app locks onto one tracked target when the user starts the flight timer. The out-of-view timer monitors that tracking ID rather than switching to another background object. Use a clean, uncluttered background and verify results in the saved video.

## Validation checklist (no model training)

- Confirm the detector initializes on supported Android devices.
- Confirm tracking IDs persist while a visible object moves between frames.
- Measure analysis FPS, latency, memory, and thermal behavior on real phones.
- Test paper planes of different sizes, folds, colors, distances, backgrounds, and lighting.
- Record false detections, missed detections, occlusions, and target-ID switches.
- Compare the app's out-of-view stop against the saved video timeline.
- Keep uncertain flights manually reviewable; do not report unverified automatic timing as official results.

No model training, dataset labeling, fine-tuning, or custom model hosting is part of this project.

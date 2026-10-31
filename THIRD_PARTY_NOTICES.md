# Third-party notices and license register

Before release, verify the exact license for each dependency, model weight, dataset, and asset independently.

| Component / asset | Exact version | Source URL | License evidence | Review status |
|---|---|---|---|---|
| AndroidX / Jetpack Compose | Pinned in app Gradle | https://developer.android.com/jetpack | Review exact artifact metadata | Pending |
| CameraX | 1.6.2 (camera-camera2, camera-lifecycle, camera-view) | https://developer.android.com/jetpack/androidx/releases/camera | AndroidX CameraX release; verify transitive notices for release | Integrated; preserve notices |
| Room | Not yet integrated | https://developer.android.com/training/data-storage/room | Review exact artifact metadata when added | Pending |
| MediaPipe Tasks Vision | 1.1.0 | https://developers.google.com/edge/mediapipe/solutions/vision/object_detector | Apache-2.0 in published Maven metadata | Integrated; preserve notices |
| EfficientDet-Lite0 model | Official MediaPipe float32 v1; SHA-256 pinned in Gradle | https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite | Checksum-matched model mirror reports Apache-2.0; confirm upstream model terms before release | Integrated as build-time download; final license verification pending |
| ARCore Depth | Not yet integrated | https://developers.google.com/ar/develop/depth | Review exact SDK artifact terms when added | Planned |

## Model and dataset obligations

1. Model weights may have terms different from the inference library.
2. Dataset image/annotation terms do not automatically match model terms.
3. Record artifact URL, immutable version, checksum, attribution, and license evidence.
4. Do not add personal videos or datasets without consent and provenance.
5. Do not use a pretrained model's label name as evidence that it can recognize a visually different object class.

This file is a review register, not legal advice.

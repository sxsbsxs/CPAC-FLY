# Third-party notices and license register

Before release, verify the exact license for each dependency, model weight, dataset, and asset independently.

| Component / asset | Exact version | Source URL | License evidence | Review status |
|---|---|---|---|---|
| AndroidX / Jetpack Compose | Pinned in app Gradle | https://developer.android.com/jetpack | Review exact artifact metadata | Pending |
| Google ML Kit Object Detection | 17.0.2 (`com.google.mlkit:object-detection`) | https://developers.google.com/ml-kit/vision/object-detection/android | Verify exact published artifact and bundled model notices before redistribution | Integrated; preserve notices |
| CameraX | 1.6.2 (camera-camera2, camera-lifecycle, camera-view) | https://developer.android.com/jetpack/androidx/releases/camera | AndroidX CameraX release; verify transitive notices for release | Integrated; preserve notices |
| Room | Not yet integrated | https://developer.android.com/training/data-storage/room | Review exact artifact metadata when added | Pending |
| ARCore Depth | Not yet integrated | https://developers.google.com/ar/develop/depth | Review exact SDK artifact terms when added | Planned |

## Model and dataset obligations

1. Model weights may have terms different from the inference library.
2. Dataset image/annotation terms do not automatically match model terms.
3. Record artifact URL, immutable version, checksum, attribution, and license evidence.
4. Do not add personal videos or datasets without consent and provenance.
5. Do not use a pretrained model's label name as evidence that it can recognize a visually different object class.

This file is a review register, not legal advice.

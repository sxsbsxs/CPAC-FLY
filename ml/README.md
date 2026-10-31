# On-device model workspace

## Initial pretrained baseline

The Android app uses Google's MediaPipe Tasks Vision Object Detector with the official EfficientDet-Lite0 float32 model (version 1), trained on COCO.

- Model URL: https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite
- Input: 320 × 320
- Labels: COCO 80 classes
- SHA-256: `40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58`
- Model file is downloaded during Gradle build into generated assets; it is not committed as a binary.
- The MediaPipe model guide reports example Pixel 6 latency of about 61 ms CPU / 28 ms GPU for this float32 model; real devices must be benchmarked.
- License evidence: the checksum-matched official MediaPipe model is reported as Apache-2.0 by the model mirror; re-check upstream asset terms before shipping a release.

## Important limitation

This is a generic COCO object detector, not a paper-airplane detector. The COCO `airplane` label describes conventional aircraft and does not guarantee detection of small paper airplanes. Treat detections as experimental. Do not claim paper-airplane recognition accuracy until evaluated on a separate paper-airplane test set.

## Model evaluation checklist

- exact artifact URL, version, SHA-256, and license evidence
- model input size, preprocessing, output schema
- device benchmark, latency, memory, and accuracy metrics
- known failure cases and intended-use limitations
- false-positive/false-negative rates for paper airplanes across size, fold, color, background, lighting, and motion blur

## Future model adaptation

Only if baseline + user-selected tracking prove insufficient:
- collect consented paper-airplane frames and label bounding boxes
- split train/validation/test by flight session, not adjacent frames
- evaluate a small fine-tune or a dedicated lightweight detector
- document dataset provenance and license before training

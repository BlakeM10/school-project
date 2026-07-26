# ml-tools

Off-device machine learning tooling for Court Vision (Python 3.11 + OpenCV per
the proposal's resource plan).

## fetch_models.sh

Downloads the two on-device models into `app/src/main/assets/models/`
(gitignored binaries — run once per development machine, then rebuild the app):

- `pose_landmarker_lite.task` — MediaPipe BlazePose, 33 landmarks
- `ball_detector.tflite` — starter ball detector (COCO EfficientDet-Lite0,
  detects the "sports ball" class)

```bash
./ml-tools/fetch_models.sh
./gradlew installDebug
```

## validate/validate_shots.py

Implements the proposal's accuracy validation: shot detection measured against
manually labelled ground-truth clips, targeting **precision and recall ≥ 85%**.

Protocol (from the proposal):
1. Record ~50 short clips at the NextGen gymnasium covering makes, misses,
   passes, and dribbling (negatives).
2. Label each clip's true shot events into `ground_truth.csv`
   (`clip_id,timestamp_ms,made`).
3. Run each clip through the app and export detected events to `detected.csv`
   (same format).
4. `python3 validate_shots.py ground_truth.csv detected.csv`

The script exits non-zero when the NFR target is missed, so it can gate a CI
job once clip data exists.

## Fine-tuning the ball detector (later sprint)

The starter COCO model works for a generic ball. To fine-tune on NextGen's ball
and gym lighting per the proposal: collect frames, label with any COCO-format
tool, train a MobileNet-SSD/EfficientDet-Lite model with the TFLite Model Maker
or MediaPipe Model Maker, and drop the exported `.tflite` over
`app/src/main/assets/models/ball_detector.tflite` — the app code needs no
change (the detector is encapsulated behind the same asset path and labels).

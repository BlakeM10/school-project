#!/usr/bin/env bash
# Downloads the on-device ML models into the app's assets. Run once per
# development machine (binaries are deliberately not committed to git).
#
#   ./ml-tools/fetch_models.sh
#
# Models:
#  - MediaPipe BlazePose (pose_landmarker_lite): 33-landmark pose estimation
#  - EfficientDet-Lite0 (COCO, includes the "sports ball" class): starter ball
#    detector. Replace the same file with the fine-tuned basketball model from
#    ml-tools/train when gym footage is available — no code change needed.
set -euo pipefail

ASSETS_DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/models"
mkdir -p "$ASSETS_DIR"

POSE_URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task"
BALL_URL="https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/latest/efficientdet_lite0.tflite"

echo "Downloading BlazePose model..."
curl -fL --retry 3 -o "$ASSETS_DIR/pose_landmarker_lite.task" "$POSE_URL"

echo "Downloading starter ball detection model (COCO EfficientDet-Lite0)..."
curl -fL --retry 3 -o "$ASSETS_DIR/ball_detector.tflite" "$BALL_URL"

echo
echo "Done. Models installed:"
ls -lh "$ASSETS_DIR" | grep -v gitignore
echo
echo "Rebuild the app (./gradlew installDebug) to bundle them."

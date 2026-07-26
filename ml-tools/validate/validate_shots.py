#!/usr/bin/env python3
"""Shot-detection accuracy validation (NFR: precision and recall >= 85%).

Per the proposal, the pipeline is validated against manually labelled
ground-truth clips (target: 50). For each clip, a human labels the true shot
events; the app (or the desktop prototype) exports the detected events. This
script matches them and reports precision, recall, and made/missed agreement.

CSV formats (one row per shot event):
  ground_truth.csv:  clip_id,timestamp_ms,made        (made: 1/0)
  detected.csv:      clip_id,timestamp_ms,made

A detection matches a ground-truth event when it is in the same clip and
within +/- TOLERANCE_MS. Each event matches at most once (greedy by time).

Usage:
  python3 validate_shots.py ground_truth.csv detected.csv [--tolerance-ms 1000]
"""
import argparse
import csv
import sys
from collections import defaultdict


def load(path):
    events = defaultdict(list)
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            events[row["clip_id"]].append(
                (int(row["timestamp_ms"]), row["made"].strip() in ("1", "true", "True"))
            )
    for clip in events.values():
        clip.sort()
    return events


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ground_truth")
    parser.add_argument("detected")
    parser.add_argument("--tolerance-ms", type=int, default=1000)
    args = parser.parse_args()

    truth = load(args.ground_truth)
    detected = load(args.detected)

    true_positives = 0
    made_agreement = 0
    total_truth = sum(len(v) for v in truth.values())
    total_detected = sum(len(v) for v in detected.values())

    for clip_id, truth_events in truth.items():
        remaining = list(detected.get(clip_id, []))
        for t_ts, t_made in truth_events:
            match = None
            for d in remaining:
                if abs(d[0] - t_ts) <= args.tolerance_ms:
                    match = d
                    break
            if match is not None:
                remaining.remove(match)
                true_positives += 1
                if match[1] == t_made:
                    made_agreement += 1

    precision = true_positives / total_detected if total_detected else 0.0
    recall = true_positives / total_truth if total_truth else 0.0
    f1 = (
        2 * precision * recall / (precision + recall)
        if precision + recall > 0
        else 0.0
    )
    made_acc = made_agreement / true_positives if true_positives else 0.0

    print(f"Ground-truth shots: {total_truth}")
    print(f"Detected shots:     {total_detected}")
    print(f"True positives:     {true_positives}")
    print(f"Precision:          {precision:.1%}")
    print(f"Recall:             {recall:.1%}")
    print(f"F1:                 {f1:.1%}")
    print(f"Made/missed agreement (of matched shots): {made_acc:.1%}")

    target = 0.85
    ok = precision >= target and recall >= target
    print(f"\nNFR target (precision & recall >= {target:.0%}): {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())

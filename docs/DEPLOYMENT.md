# Court Vision — Deployment Guide (Phase 9)

## 1. Build the release APK

One-time: create a signing keystore (keep it OUT of git, back it up — losing it
means you can never update the installed app):

```bash
keytool -genkeypair -v -keystore courtvision-release.keystore \
  -alias courtvision -keyalg RSA -keysize 2048 -validity 10000
```

Then build and sign:

```bash
./gradlew assembleRelease
# sign + align (Android SDK build-tools)
zipalign -v -p 4 app/build/outputs/apk/release/app-release-unsigned.apk app-release-aligned.apk
apksigner sign --ks courtvision-release.keystore --out CourtVision.apk app-release-aligned.apk
```

Pre-flight checklist before building:
- [ ] `app/google-services.json` present (production Firebase project)
- [ ] Firestore rules + indexes deployed (`cd firebase && firebase deploy --only firestore`)
- [ ] Drill catalogue seeded (`firebase/seed/seed_drills.js`)
- [ ] ML models fetched (`./ml-tools/fetch_models.sh`) — the APK must bundle them
- [ ] Release SHA-1 fingerprint added in Firebase console (Google Sign-In)
- [ ] Version bumped in `app/build.gradle.kts` (`versionCode` / `versionName`)

## 2. Install at NextGen Academy

Every CI run also uploads a **debug APK artifact** (GitHub → Actions → run →
Artifacts) usable for quick trials; use the signed release APK for the pilot.

Install on the club device(s): copy `CourtVision.apk` to the phone and open it
(enable "Install unknown apps" for the file manager), or:

```bash
adb install CourtVision.apk
```

## 3. Parallel running (the proposal's implementation approach)

Run the existing manual coaching process and Court Vision **simultaneously for
approximately four weeks**:

- Coaches continue normal verbal observation, feedback, and their informal
  made/missed tallies.
- Players run drills with Court Vision recording at the same time.
- After each session, a coach compares their manual tally against the app's
  session document and records both numbers plus any discrepancy in the
  comparison log below.

**Comparison log:**

| Date | Player | Drill | Manual made/attempts | App made/attempts | Discrepancy notes |
|------|--------|-------|----------------------|-------------------|-------------------|

Why parallel running (from the proposal): direct comparison between the two
systems, coach confidence in the automated metrics before relying on them, and
a low-risk environment for finding errors before the manual system is retired.

## 4. Cut-over review (end of week 4)

Hold a review with the coaching staff and decide, using:
- the comparison log (agreement rate between manual and automated counts),
- the UAT questionnaire results (docs/TEST_PLAN.md §4),
- open defects and their severities,
- the N-03 accuracy validation result.

Outcomes: **adopt fully** (retire manual tallies), **extend parallel running**
(specific gaps to fix, new review date), or **roll back** (retire the app,
document why in the dissertation's evaluation chapter).

## 5. Operations notes

- **Cost:** everything runs on the Firebase Spark (free) tier; monitor usage in
  the Firebase console (Firestore reads are the metric to watch).
- **Data protection:** no video is ever stored; players can delete their own
  sessions in-app; account deletion requests are honoured via the Firebase
  console (delete the Auth user + their `users` doc; their sessions can be
  removed with a console query on `playerId`).
- **Updating:** bump `versionCode`, rebuild, re-sign with the SAME keystore,
  reinstall. Firestore schema changes must remain backward-compatible with
  already-synced offline sessions.
- **Model updates:** replacing `app/src/main/assets/models/ball_detector.tflite`
  with a fine-tuned model requires only a rebuild (see ml-tools/README.md).

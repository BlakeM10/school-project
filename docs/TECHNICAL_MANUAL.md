# Court Vision — Technical Manual

*For future developers and maintainers (per the proposal's documentation
objective). Companion documents: `DEVELOPMENT_BLUEPRINT.md` (requirements +
architecture), `DATABASE_SCHEMA.md`, `TEST_PLAN.md`, `DEPLOYMENT.md`,
`FIREBASE_SETUP.md`.*

## 1. Architecture overview (MVVM)

Single-activity Android app, Kotlin, four layers:

```
View (Fragments + Navigation)  →  ViewModel (StateFlow state)  →
Domain (pure Kotlin logic)     →  Data (Firebase repositories)
```

- **View** (`ui/`): one package per screen. Fragments only render state and
  forward clicks; global routing (role-based home, sign-out) lives in
  `MainActivity`, which observes `AuthViewModel`.
- **ViewModel** (`viewmodel/`): one per screen, exposing a single immutable
  UI-state data class via `StateFlow`. Constructed with repository interfaces
  through small `Factory` classes; dependencies come from `di/AppContainer`
  (manual DI — no framework, by design).
- **Domain** (`domain/`): pure Kotlin, no Android/Firebase imports —
  models (`User` sealed hierarchy, `Drill`, `Session`, `Team`), `SessionRecorder`,
  `JoinCodeGenerator`, and the CV algorithms (`domain/cv/`). Everything here is
  JVM-unit-testable.
- **Data** (`data/`): `AuthRepository`/`FirestoreRepository` interfaces with
  Firebase implementations, plus pure mappers (Firestore document maps ↔
  domain models).
- **CV integration** (`cv/`): Android-bound wrappers — `PoseEstimator`
  (MediaPipe), `BallDetectorEngine` (TFLite), `CameraCVPipeline` (orchestrator),
  `FeedbackEngine` (tones).

## 2. Class responsibilities (proposal class model → code)

| Proposal class | Code | Responsibility |
|---|---|---|
| User / Player / Coach | `domain/model/User.kt` | Sealed identity hierarchy; `Role` carries the Firestore wire name |
| Session | `domain/model/Session.kt` | Immutable session record + derived-metric computation |
| Drill | `domain/model/Drill.kt` | Catalogue entry; `measures` drives which detectors activate |
| SessionRecorder | `domain/session/SessionRecorder.kt` | Aggregates CV events → Session; injectable clock |
| CVPipeline | `cv/CameraCVPipeline.kt` + `domain/cv/*` | Frame analysis → events via `CVPipelineListener`; internals fully encapsulated |
| FeedbackEngine | `cv/FeedbackEngine.kt` | Made/missed/cue tones |
| FirestoreRepository | `data/repository/FirebaseFirestoreRepository.kt` | Drills, sessions, teams; snapshot-listener Flows |

## 3. The CV pipeline in detail

Frame flow: CameraX `ImageAnalysis` (KEEP_ONLY_LATEST, single-thread executor)
→ bitmap + rotation → `BallDetectorEngine.detect` (sync) and
`PoseEstimator.detectAsync` (MediaPipe live-stream) → on each pose result,
`CameraCVPipeline` feeds `(pose, latestBall, ts)` into the pure detectors and
posts events to the main thread.

- **ShotDetector** (`domain/cv/ShotDetector.kt`): IDLE → HOLDING (ball near a
  wrist) → RISING (wrists above shoulders; `motionStartTs`) → FLIGHT (ball
  separates upward; `releaseTs`). Flight ends on descent past release height /
  ball lost 600 ms / 3 s timeout. Made/missed = maximum perpendicular deviation
  of descent samples from the descent chord (< 0.035 → made) after requiring a
  minimum arc height. **All thresholds are named constants at the bottom of the
  file — tune them there** against `ml-tools/validate/validate_shots.py`.
- **DribbleDetector**: down→up reversals of ball centre while below the hip
  line; amplitude ≥ 0.03, inter-bounce interval clamped to 150–2000 ms.
- **ReactionTimer**: armed at cue time; baseline = first frame after cue;
  fires when mean displacement of shoulders/hips/wrists exceeds 0.035; 3 s
  no-response window discards the rep.

Coordinates everywhere are normalized to the image, y downward.

## 4. Firestore

Schema + rules rationale: `docs/DATABASE_SCHEMA.md`. Sources of truth in the
repo: `firebase/firestore.rules`, `firebase/firestore.indexes.json`,
`firebase/seed/drills.json` (+ `seed_drills.js` Admin-SDK seeder).
Key invariants the code relies on:
- `sessions` docs are immutable; owner may delete (right to erasure).
- Team joins may only append the caller's own uid to `playerIds`.
- `drills` is client-read-only; seeding is Admin-SDK only.
- One document shape for all sessions (unmeasured metrics = 0/[]).

## 5. Development environment

1. JDK 17+, Android SDK (API 34). Any editor: the project builds with
   `./gradlew assembleDebug` / `testDebugUnitTest`.
2. Firebase per-developer setup: `docs/FIREBASE_SETUP.md`.
3. ML models: `./ml-tools/fetch_models.sh` (binaries are gitignored; the app
   degrades gracefully and shows a warning when they're absent).
4. CI (`.github/workflows/ci.yml`): builds the APK + runs all unit tests on
   every push; uploads the debug APK and (on failure) test reports as
   artifacts.

## 6. Replacing / retraining the ball detection model

The detector loads `app/src/main/assets/models/ball_detector.tflite` — any
TFLite object-detection model **with metadata** whose labels include
`sports ball`, `basketball`, or `ball` works with zero code changes
(`BallDetectorEngine.BALL_LABELS`). Recommended path: MediaPipe Model Maker or
TFLite Model Maker fine-tune on labelled gym frames (see ml-tools/README.md),
then swap the asset and rebuild. Validate with the ml-tools harness before
shipping (N-03 gate).

## 7. Decision changelog

| Decision | Rationale |
|---|---|
| Firestore over self-hosted SQL | Proposal: no server maintenance in scope; free tier; offline sync |
| MVVM + manual DI (no Hilt) | Proposal mandates MVVM/testability; manual container keeps the student codebase transparent |
| Flat collections, `teamId` denormalized on sessions | Rules can't join; enables one-query coach dashboard with per-doc access checks |
| No video retention anywhere | Approved decision C1 + legal commitments; enforced structurally (no video field/storage) |
| Join-by-code teams | Approved account-provisioning decision; rules restrict the update to appending own uid |
| Sessions immutable + owner-deletable | Analytical integrity + right to erasure |
| ML binaries out of git, fetched per-machine | Repo hygiene; models swappable without code changes |
| Guava dependency | CameraX exposes ListenableFuture but ships only a stub |
| Made/missed via descent-deviation heuristic | No hoop detection in v1; isolated + threshold-tunable, measured by the validation harness |
| MPAndroidChart (JitPack) for line charts | Proposal: "a charting library" for the dashboard; radial chart is custom per spec |

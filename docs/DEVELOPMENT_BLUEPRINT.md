# Court Vision — Development Blueprint

**Project:** Court Vision analytics system for NextGen Basketball Foundation (Academy)
**Author of source documents:** Martin Blake (BSc Hons Computing Information Systems, NACIT)
**Blueprint status:** APPROVED (2026-07-26) with the following contradiction resolutions:
- **C1 resolved:** No video retention. Frames are processed on-device and discarded; only metrics are stored. The replay feature (F-18) is out of scope.
- **C2 resolved:** Push notifications (F-19) deferred — not in core scope; may be added later via FCM.
- **C3 resolved:** Reaction-time stimulus is audio-only.
- **Account provisioning:** self sign-up as player or coach; a coach creates a team which generates a join code; players enter the code to join the team.
- **Dev environment:** standard Kotlin/Gradle Android project, buildable from the CLI and usable from VS Code; Android SDK required on the user's machine for device/emulator runs.

This blueprint is derived exclusively from four project documents:

| # | Document | Role in analysis |
|---|----------|------------------|
| D1 | Concept Paper | Earliest vision: background, problems, 7 system objectives |
| D2 | Proposal (correction draft, with tutor comments) | Intermediate version; contains tutor's required changes |
| D3 | Project Proposal (Corrected — final) | **Most authoritative**: detailed objectives, architecture, tech stack, testing, legal/ethical constraints |
| D4 | Literature Review | Similar-systems analysis (Hudl, HomeCourt, Catapult, CoachNow) and technology justifications |

Where documents conflict, D3 (the final corrected proposal) is treated as most authoritative because it is the latest supervisor-corrected version. All conflicts are listed in §11 and require user sign-off before code is written.

---

## 1. Project Summary

**Goal.** Replace subjective, memory-based coach observation at NextGen Academy (a grassroots basketball club in Malawi) with objective, automated, longitudinal performance analytics — using nothing but a mid-range Android phone.

**What the system does:**

1. Uses the phone camera during training drills to run two on-device ML models simultaneously:
   - **MediaPipe BlazePose** — 33 body landmarks per frame (pose estimation)
   - **TensorFlow Lite MobileNet SSD** (fine-tuned on basketball imagery) — ball bounding box per frame
2. From these two signals it automatically computes: **shot attempts, made/missed classification, shooting accuracy %, shot release time, dribble count, dribble speed, and reaction time** (elapsed time from an audio cue to first significant pose change).
3. Gives the player **real-time feedback** during the drill: a tone after each detected shot and a live metric HUD + skeleton/ball overlay on screen.
4. Offers a **structured drill library** (predefined exercises with instructions, target metrics, durations) so players can train independently.
5. Persists per-session metrics to **Firebase Firestore**, powering **longitudinal progress reports** for players and a **coach dashboard** with team roster and trend charts.
6. Works **fully offline during a session** — all CV runs on-device; cloud sync happens when connectivity is available.

**Why (evidence from documents):** immediate augmented feedback accelerates motor learning (Salmoni et al.; Schmidt & Wrisberg); on-device inference is feasible on mid-range phones (MobileNets, MediaPipe); no existing system (Hudl, HomeCourt, Catapult, CoachNow) combines Android support + automated real-time feedback + team-level coach reporting + zero cost.

**User roles (only two are documented):**
- **Player** — runs drills, receives live feedback, views own session summaries and progress.
- **Coach** — manages a team of players, views team roster, per-player longitudinal trend reports.
- Both inherit from a `User` superclass (shared identity + authentication). *No admin role is documented; none will be invented.*

---

## 2. Requirements Catalogue (derived)

> ⚠️ The proposal references a formal requirements specification listing **FR01–FR15 and NFR01–NFR10**, and a test plan with **25 test cases** — but those documents were not provided. The catalogue below is **derived** from the four available documents and numbered independently. If the real FR/NFR spec exists, supply it and this section will be reconciled against it.

### 2.1 Functional requirements (derived)

| ID | Requirement | Source |
|----|-------------|--------|
| F-01 | Secure login for players and coaches via Firebase Auth: email/password **and** Google Sign-In | D3 §3.3 |
| F-02 | Account creation/management for player and coach accounts | D3 (user manual scope) |
| F-03 | Record a training drill using the phone camera (CameraX pipeline) | D1, D3 |
| F-04 | Run BlazePose pose estimation ≥15 fps, extracting 33 landmarks per frame | D3 §3.3 |
| F-05 | Detect the basketball per frame via fine-tuned TFLite MobileNet SSD (bounding box output) | D3 §3.3 |
| F-06 | Detect shot attempts from wrist/elbow trajectory vectors relative to ball bounding box | D3 §3.3 |
| F-07 | Classify each shot attempt as made or missed; compute session shooting accuracy % | D1, D3 |
| F-08 | Measure shot release time (start of shooting motion → ball leaves hand) | D1, D3 |
| F-09 | Count dribbles via repeated ball-to-hand proximity events; compute dribble speed from inter-bounce intervals | D1, D3 |
| F-10 | Measure reaction time: audio start cue → first significant pose-landmark change | D3 (D1 said "visual or audio" — see conflict C3) |
| F-11 | Real-time feedback during drill: audio tone per detected shot + live on-screen metric overlay (HUD), skeleton overlay (green), ball bounding box (green) | D1, D3 |
| F-12 | Drill library: predefined, read-only catalogue with instructions, target metrics, durations; selectable by players and coaches; available offline | D1, D3, D4 |
| F-13 | Session summary after each drill: radial accuracy chart + shot distribution bar chart | D3 §3.3 |
| F-14 | Persist all session metrics to Firestore; sync when online | D1, D3 |
| F-15 | Player progress reports: metric trends across sessions over time | D1, D3 |
| F-16 | Coach dashboard: player roster + longitudinal line-chart trends per player | D3 |
| F-17 | Team linkage: coaches see only players whose `teamId` matches theirs | D3 (security rules) |
| F-18 | *(CONFLICT C1 — pending)* Store session video for replay with overlaid annotations | D1 only; contradicted by D3 |
| F-19 | *(CONFLICT C2 — pending)* Push notifications: training schedules, new drills, performance milestones | D1 + D2; absent from D3 |

### 2.2 Non-functional requirements (derived)

| ID | Requirement | Source |
|----|-------------|--------|
| N-01 | All CV processing on-device; no cloud dependency during a session (offline-capable) | D3 |
| N-02 | Pose pipeline ≥15 fps minimum on the target device (BlazePose targets 30 fps) | D3 |
| N-03 | Shot detection precision and recall ≥85%, validated against 50 manually labelled ground-truth clips | D3 |
| N-04 | Privacy/legal: raw video is **not retained** after on-device processing; only computed metrics stored (Malawi Electronic Transactions and Cyber Security Act; informed consent; guardian consent for minors; right to withdraw/delete) | D3 |
| N-05 | Firestore security rules: a player's sessions readable only by that player and coaches with matching `teamId`; `drills` publicly readable, write-protected | D3 |
| N-06 | UI meets Android Material Design accessibility guidelines, incl. minimum 48×48 dp touch targets | D3 |
| N-07 | Target/reference device: mid-range Android — Android 10+ (API 29), ≥12 MP rear camera, ≥3 GB RAM (e.g. Redmi Note 10, tested on Android 12) | D3 |
| N-08 | Zero cost: Firebase free tier; all libraries open-source (Apache 2.0 / Firebase ToS) | D3, D4 |
| N-09 | Simplicity: interface usable by non-specialists without coach mediation (lesson from Hudl/HomeCourt review) | D4 |
| N-10 | Unit tests (JUnit 4 + Mockito) run in CI (GitHub Actions) on every commit | D3 |

---

## 3. Application Architecture

**Pattern:** MVVM (documented and mandated in D3), native Android, Kotlin.

```
┌────────────────────────────── PRESENTATION (View) ──────────────────────────────┐
│  Activities / Fragments (5 screens)                                             │
│  Login · DrillLibrary · LiveSession (viewfinder + overlays + HUD)               │
│  SessionSummary · CoachDashboard                                                │
└───────────────▲─────────────────────────────────────────────────────────────────┘
                │ observes LiveData / StateFlow
┌───────────────┴────────────────── VIEWMODEL ────────────────────────────────────┐
│  LoginViewModel · DrillLibraryViewModel · LiveSessionViewModel                  │
│  SessionSummaryViewModel · CoachDashboardViewModel                              │
└───────────────▲─────────────────────────────────────────────────────────────────┘
                │ calls / observes
┌───────────────┴───────────────── DOMAIN (business logic) ───────────────────────┐
│  CVPipeline ──(events)──► SessionRecorder ──► FeedbackEngine                    │
│   ├─ BlazePose (33 landmarks/frame, ≥15fps)      ├─ tone per shot               │
│   ├─ TFLite MobileNet SSD (ball bbox)            └─ HUD metric updates          │
│   ├─ ShotDetector (wrist/elbow vectors vs bbox)                                 │
│   ├─ DribbleCounter (ball–hand proximity events)                                │
│   └─ ReactionTimer (audio cue → pose change)                                    │
│  Domain models: User (abstract) ◄─ Player, Coach · Session · Drill              │
└───────────────▲─────────────────────────────────────────────────────────────────┘
                │
┌───────────────┴───────────────────── DATA ──────────────────────────────────────┐
│  FirestoreRepository (sessions, drills, users, teams) · AuthRepository          │
│  Firebase Auth (email/password + Google) · Firestore (offline persistence on)   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Key design points (all from D3):**
- The CV internals (pose model, ball model, frame buffer) are **encapsulated behind an event-based interface** so detection algorithms can be swapped without touching other classes.
- **Inheritance:** `User` superclass holds shared identity/auth; `Player`/`Coach` specialize. Polymorphic handling in auth flow, type-specific dispatch afterwards.
- Documented class model: `User, Player, Coach, Session, Drill, CVPipeline, SessionRecorder, FirestoreRepository, FeedbackEngine` (D3 says "eight classes" but lists nine names — treated as nine).
- Frame flow: CameraX `ImageAnalysis` → CVPipeline → domain events (`ShotDetected(made)`, `DribbleDetected`, `ReactionMeasured`, `PoseFrame`) → SessionRecorder mutates the active `Session` → FeedbackEngine renders audio/HUD feedback; ViewModel observes recorder state for the UI.

---

## 4. Folder Structure

Standard Android/Gradle project (Kotlin DSL), plus a Python tooling folder for model work:

```
court-vision/
├── app/
│   ├── src/main/
│   │   ├── java/com/nextgen/courtvision/
│   │   │   ├── ui/                        # View layer — one package per screen
│   │   │   │   ├── login/                 #   LoginFragment
│   │   │   │   ├── drilllibrary/          #   DrillLibraryFragment + adapter
│   │   │   │   ├── livesession/           #   LiveSessionFragment, OverlayView (skeleton,
│   │   │   │   │                          #   bbox, HUD via Android Canvas)
│   │   │   │   ├── summary/               #   SessionSummaryFragment, RadialChartView,
│   │   │   │   │                          #   shot-distribution bar chart
│   │   │   │   └── dashboard/             #   CoachDashboardFragment, roster, trend line chart
│   │   │   ├── viewmodel/                 # One ViewModel per screen (LiveData/StateFlow)
│   │   │   ├── domain/
│   │   │   │   ├── model/                 # User, Player, Coach, Session, Drill, metric types
│   │   │   │   ├── cv/                    # CVPipeline, PoseEstimator, BallDetector,
│   │   │   │   │                          # ShotDetector, DribbleCounter, ReactionTimer
│   │   │   │   ├── session/               # SessionRecorder
│   │   │   │   └── feedback/              # FeedbackEngine (tone player + HUD state)
│   │   │   ├── data/
│   │   │   │   ├── repository/            # FirestoreRepository, AuthRepository (interfaces
│   │   │   │   │                          # + impls, mockable for JUnit/Mockito)
│   │   │   │   └── mapper/                # Firestore document ↔ domain model mapping
│   │   │   └── di/                        # Dependency wiring
│   │   ├── assets/                        # ball_detector.tflite (fine-tuned MobileNet SSD)
│   │   └── res/                           # layouts, Material theme, strings, audio tones
│   ├── src/test/                          # JUnit 4 + Mockito unit tests
│   └── src/androidTest/                   # instrumented/integration tests (pre-recorded clips)
├── firebase/
│   ├── firestore.rules                    # security rules (N-05)
│   └── seed/drills.json                   # predefined drill catalogue seed data
├── ml-tools/                              # Python 3.11 + OpenCV 4.9 (D3 resource table)
│   ├── label/                             # training-data labelling helpers
│   ├── train/                             # MobileNet SSD fine-tuning → TFLite export
│   └── validate/                          # precision/recall vs 50 ground-truth clips (N-03)
├── docs/                                  # this blueprint, schema, manuals
└── .github/workflows/ci.yml               # GitHub Actions: unit tests on every commit (N-10)
```

---

## 5. Database Schema (Firebase Firestore — Native mode)

Four collections, exactly as specified in D3:

### `users/{uid}` — document per Player or Coach (uid = Firebase Auth UID)
```
role:        "player" | "coach"
displayName: string
email:       string
teamId:      string (ref → teams)
createdAt:   timestamp
```

### `teams/{teamId}` — links coaches to their managed players
```
name:      string
coachIds:  array<string>   (user uids)
playerIds: array<string>   (user uids)
```

### `drills/{drillId}` — predefined catalogue, read-only to clients
```
name:          string
category:      string          (e.g. shooting, dribbling, reaction)
instructions:  string
targetMetrics: map             (e.g. { accuracyPct: 70, shots: 20 })
durationSec:   number
```

### `sessions/{sessionId}` — one document per completed drill session
```
playerId:        string (uid)
teamId:          string        (denormalized for coach-access rule)
drillId:         string
startedAt:       timestamp
durationSec:     number
shotsAttempted:  number
shotsMade:       number
accuracyPct:     number
avgReleaseTimeMs:number
releaseTimesMs:  array<number>
dribbleCount:    number
dribbleSpeedHz:  number        (bounces/sec from inter-bounce intervals)
reactionTimesMs: array<number>
```

**Security rules (N-05, verbatim intent from D3):**
- `sessions`: read allowed iff `request.auth.uid == resource.data.playerId` OR requester is a coach whose `teamId` matches `resource.data.teamId`; write only by the owning player.
- `drills`: read by any authenticated user; client writes denied.
- `users`: own document readable/writable; coaches can read documents of players on their team.
- No raw video stored anywhere (N-04).

**Offline:** Firestore's built-in offline persistence covers the "record at the gym, sync later" requirement; the drill catalogue is cached locally for offline drill selection.

---

## 6. API Design

There is **no custom backend server** — D3/D4 explicitly chose Firebase (serverless) over a self-hosted DB + REST API. The "API" therefore has three layers:

**(a) External service APIs used**
- Firebase Auth: email/password sign-in, Google Sign-In, session tokens.
- Firestore SDK: CRUD on the four collections, offline cache, security-rule enforcement.

**(b) Repository interfaces (the app's internal data API — mockable per D3 test plan)**
```kotlin
interface AuthRepository {
    suspend fun signInEmail(email: String, password: String): Result<User>
    suspend fun signInGoogle(idToken: String): Result<User>
    suspend fun register(profile: NewUser): Result<User>
    fun currentUser(): User?
    fun signOut()
}

interface FirestoreRepository {
    suspend fun getDrills(): List<Drill>                          // cached offline
    suspend fun saveSession(session: Session): Result<Unit>       // queued offline
    fun observeSessions(playerId: String): Flow<List<Session>>
    fun observeTeamPlayers(teamId: String): Flow<List<Player>>
    suspend fun getUser(uid: String): User?
}
```

**(c) CV pipeline event interface (module boundary mandated by D3's encapsulation requirement)**
```kotlin
interface CVPipelineListener {
    fun onPoseFrame(landmarks: List<Landmark33>, fps: Float)
    fun onBallDetected(box: BoundingBox)
    fun onShotDetected(result: ShotResult)        // made | missed, releaseTimeMs
    fun onDribbleDetected(intervalMs: Long)
    fun onReactionMeasured(reactionMs: Long)
}
```

---

## 7. Screen Flow

Five screens (fixed set, per D3 — Login, Drill Library, Live Session, Session Summary, Coach Dashboard):

```
                        ┌─────────┐
                        │  Login   │  Firebase Auth (email/password · Google)
                        └────┬────┘
                 role=player │ role=coach
              ┌──────────────┴───────────────┐
              ▼                              ▼
      ┌──────────────┐               ┌────────────────┐
      │ Drill Library │               │ Coach Dashboard │
      │ (select drill)│               │ roster · trends │
      └──────┬───────┘               └───────┬────────┘
             ▼                               │ select player
      ┌──────────────┐                       ▼
      │ Live Session  │               (player session
      │ camera + pose │                summaries /
      │ overlay + HUD │                trend charts)
      └──────┬───────┘
             ▼ drill ends
      ┌──────────────┐
      │Session Summary│ → back to Drill Library, or view own progress history
      │ radial + bars │
      └──────────────┘
```

Live Session screen contents (D3): camera viewfinder, green pose skeleton overlay, green ball bounding box, live metric HUD; audio tone per detected shot; reaction drills start with an audio cue.

---

## 8. Feature Breakdown

| Feature | Sub-features | Depends on |
|---|---|---|
| **1. Authentication** | Email/password; Google Sign-In; role-aware routing (player vs coach); account creation | Firebase Auth |
| **2. Drill Library** | Read-only catalogue; instructions, target metrics, duration; offline cache; drill selection starts a session | Firestore, seed data |
| **3. CV Pipeline** | CameraX capture ≥15fps; BlazePose 33 landmarks; TFLite ball detection; shot detection; made/missed classification; release-time; dribble count/speed; reaction time | ML models, device camera |
| **4. Real-time Feedback** | Audio tone per shot; live HUD; skeleton + bbox overlays (Canvas) | CV Pipeline |
| **5. Session Recording** | SessionRecorder aggregates CV events into Session; save to Firestore (offline-queued) | CV Pipeline, Firestore |
| **6. Session Summary** | Radial accuracy chart (custom view); shot-distribution bar chart; per-metric readout | Session Recording |
| **7. Progress Reports** | Player metric trends across sessions; coach dashboard roster; per-player line charts (charting library) | Firestore history |
| **8. Team Management** | Coach↔player linkage via `teams`; access control via rules | Auth, Firestore |
| **9. Push Notifications** *(pending C2)* | Schedules, new drills, milestones | FCM |
| **10. Video Replay** *(pending C1)* | Session replay with overlaid annotations | Conflicts with N-04 |

---

## 9. Development Roadmap (mapped to your phases)

| Your phase | Work | Exit criteria |
|---|---|---|
| **3 — Database** | Firestore schema, security rules, drill seed catalogue, emulator config | Rules pass emulator tests; schema doc approved |
| **4 — Authentication** | Firebase Auth (email + Google), User/Player/Coach models, role routing, login screen | Login works both ways; role routing correct |
| **5 — Backend APIs** | AuthRepository + FirestoreRepository, mappers, offline persistence, unit tests + CI | Repos fully unit-tested (Mockito), CI green |
| **6 — Frontend** | All 5 screens, MVVM ViewModels, charts, Material Design (48dp), navigation | All screens navigable with stub CV data |
| **7 — AI features** | CVPipeline: BlazePose + TFLite integration, shot/dribble/reaction algorithms, FeedbackEngine, overlays; Python fine-tune + validation vs 50 clips | ≥15fps on target-class device; 85% P/R validation report |
| **8 — Testing** | Unit (JUnit4/Mockito) · integration (pre-recorded clips) · system (25 test cases — doc missing, will draft) · UAT script for NextGen | All high-priority cases pass |
| **9 — Deployment** | Signed release APK; parallel-running rollout plan (4 weeks alongside manual coaching, then cut-over review); user + technical manuals | APK installable; docs delivered |

Note: D3 develops CV early (it's the highest-risk module). Your phase order defers it to Phase 7. Mitigation: during Phases 3–6 I'll define the CV event interfaces and build against a **stub CVPipeline**, so the risky CV work can also be prototyped in Python (per D3's methodology) in parallel without blocking. Flagged for your awareness rather than deviating from your order.

## 10. Priority Order (MoSCoW)

- **Must:** Auth · Firestore schema/rules · drill library · CV shot detection + accuracy % · live HUD + tone · session summary · session persistence · player progress view · coach dashboard basics
- **Should:** dribble count/speed · release time · reaction time · Google Sign-In · trend charts polish · CI pipeline
- **Could:** push notifications (if approved) · milestone detection · UAT tooling
- **Won't (this version) unless you say otherwise:** video replay with annotations (conflicts with the no-video-retention legal position) · iOS/cross-platform · multi-player simultaneous tracking (documents scope sessions to a single player per camera)

---

## 11. Risks, Contradictions and Missing Information

### Contradictions requiring your decision (per your rule: ask, don't assume)

- **C1 — Video retention.** Concept Paper (D1): "record all training sessions as video and allow users to replay sessions with overlaid performance annotations." Final Proposal (D3, Legal): "all session video will be processed on-device and **not retained** after the session ends; only computed metric values will be stored." Direct conflict. D3 is later and legally motivated → recommend **no video retention** (drop replay).
- **C2 — Push notifications.** In D1 objectives and D2 (task 3.4.3), absent from D3's detailed objectives/screens. Recommend: keep out of core scope or add as low-priority "Could".
- **C3 — Reaction-time stimulus.** D1: "visual **or** audio stimulus"; D3: audio cue only. Resolved to **audio-only** (D3 later + technically cleaner) — say if you disagree.
- **C4 — Class count.** D3 says "eight classes" but lists nine. Treated as nine. (Cosmetic; no decision needed.)

### Missing information

- **The FR01–FR15 / NFR01–NFR10 requirements specification** — referenced but not provided. §2 is my derivation; supply the real one if it exists.
- **The 25-test-case test plan** — referenced, not provided; will be drafted in Phase 8.
- **Drill catalogue content** — no actual drills are enumerated anywhere; need a starter list (even 5–10) or I'll draft one for approval.
- **Ball-detection training data** — fine-tuning MobileNet SSD needs labelled basketball images/footage; none provided. Options: public basketball datasets for a first pass, then gym footage later.
- **Account provisioning flow** — who creates accounts and how players join a team is not specified (see question asked at approval).

### Key risks

| Risk | Impact | Mitigation |
|---|---|---|
| Shot-detection accuracy below 85% target | Core value proposition fails | Validation harness (50 labelled clips per D3); iterate algorithm; Agile sprints are designed for this |
| <15 fps with two models on mid-range device | Real-time feedback degrades | GPU delegate for TFLite; BlazePose lite variant; frame-skip strategy for ball detector |
| No training dataset for ball model | Phase 7 blocked | Start with public datasets + pre-trained COCO ball class ("sports ball"); fine-tune later with gym footage |
| Cannot run/test on a real device from this environment | CV behavior unverifiable until user-side testing | Unit/integration tests with pre-recorded clips + emulator; user runs on-device validation |
| Development in VS Code vs documented Android Studio | Tooling friction | Gradle CLI builds work from any editor; see approval question |

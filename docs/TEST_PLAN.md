# Court Vision — Test Plan (Phase 8)

Testing follows the four levels from the proposal: **unit**, **integration**,
**system**, and **user acceptance**. Requirement IDs (F-xx / N-xx) refer to the
requirements catalogue in `docs/DEVELOPMENT_BLUEPRINT.md` §2.

## 1. Unit testing (automated, CI-gated)

JUnit 4 + Mockito, run on every push by `.github/workflows/ci.yml`
(proposal NFR: CI on every commit). Current suite (86 tests):

| Area | Test classes |
|---|---|
| Validation & mapping | `CredentialsValidatorTest`, `UserMapperTest`, `DrillMapperTest`, `SessionMapperTest`, `TeamMapperTest` |
| Domain logic | `JoinCodeGeneratorTest`, `SessionRecorderTest` |
| CV algorithms | `ShotDetectorTest`, `DribbleDetectorTest`, `ReactionTimerTest` |
| ViewModels | `AuthViewModelTest`, `DrillLibraryViewModelTest`, `LiveSessionViewModelTest`, `CoachDashboardViewModelTest` |
| Integration (JVM) | `CvSessionIntegrationTest` |

## 2. Integration testing

Two forms, per the proposal ("pre-recorded video clips as reproducible inputs"):

1. **JVM-level (automated):** `CvSessionIntegrationTest` scripts a synthetic
   clip (pose + ball frames) through ShotDetector + DribbleDetector into
   SessionRecorder and asserts the final Session document — runs in CI.
2. **Device-level (manual, on target hardware):** play the labelled validation
   clips (ml-tools/README.md protocol) at the phone camera or feed them via
   `adb` screen capture, and compare detected events against labels with
   `ml-tools/validate/validate_shots.py`. Pass gate: **precision & recall ≥ 85%
   for shot detection (N-03)**.

## 3. System test cases (TC01–TC25)

Executed manually on the target device class (Android 10+, e.g. Redmi Note 10,
per N-07) with Firebase configured and models installed. Record outcomes in the
test log (§5).

| ID | Title | Req | Steps (condensed) | Expected result | Priority |
|----|-------|-----|-------------------|-----------------|----------|
| TC01 | Register as player (email) | F-01,F-02 | Create account with valid email/password, role Player | Lands on Drill Library; `users` doc created with role=player | High |
| TC02 | Register as coach (email) | F-01,F-02 | Same with role Coach | Lands on Coach Dashboard; role=coach stored | High |
| TC03 | Reject invalid registration input | F-01 | Try bad email, short password, no role | Clear validation message per case; no account created | High |
| TC04 | Email sign-in and session restore | F-01 | Sign in; kill app; reopen | Sign-in works; reopen skips login and restores the correct home screen | High |
| TC05 | Google Sign-In (first time) | F-01 | Continue with Google on a fresh account | Prompted for name + role, then routed by role | Medium |
| TC06 | Coach creates team | F-17 | Coach Dashboard → enter name → Create | Join code displayed; `teams` doc created; coach's teamId set | High |
| TC07 | Player joins by code | F-17 | Player → Join a team → enter code | Banner disappears; player appears in coach roster (live) | High |
| TC08 | Wrong join code rejected | F-17 | Enter a non-existent code | Friendly error; player remains teamless | Medium |
| TC09 | Drill catalogue displays | F-12 | Open Drill Library online | All seeded drills listed in sortOrder with details | High |
| TC10 | Drill catalogue offline | F-12,N-01 | Airplane mode after first launch → open library | Cached drills still listed and selectable | High |
| TC11 | Camera permission flow | F-03 | Open Live Session first time; deny then allow | Denial shows explanation; allowing shows viewfinder | High |
| TC12 | Pose skeleton overlay | F-04 | Stand in frame during Live Session | Green 33-landmark skeleton tracks the player | High |
| TC13 | Ball bounding box | F-05 | Hold/move a basketball in frame | Green box tracks the ball | High |
| TC14 | Shot detection + tone | F-06,F-07,F-11 | Take 10 shots in a shooting drill | Each attempt counted; tone plays per shot; HUD updates live | High |
| TC15 | Made/missed classification | F-07 | 10 shots with known outcomes | Session accuracy within agreed tolerance of true accuracy (see N-03 gate) | High |
| TC16 | Release time measured | F-08 | Shots with deliberate slow/fast release | releaseTimesMs populated; slower release → larger value | Medium |
| TC17 | Dribble count + speed | F-09 | 30 s stationary dribble drill | Count within ±10% of actual; speed plausible (bounces/s) | High |
| TC18 | Reaction drill cues | F-10,F-11 | Run reaction drill; react to each beep | Cue tones play at random intervals; reactionTimesMs recorded per rep | High |
| TC19 | Session summary correctness | F-13 | Finish a drill → summary | Radial accuracy, made/missed bars, and metric rows match HUD totals; unmeasured metrics say "Not measured" | High |
| TC20 | Session persisted to cloud | F-14 | Finish drill online → check Firestore console | One `sessions` doc with all metric fields and correct playerId/teamId | High |
| TC21 | Offline session syncs later | F-14,N-01 | Record drill in airplane mode; re-enable network | Session appears in Firestore after reconnect; visible in progress screen throughout | High |
| TC22 | Player progress report | F-15 | Record ≥3 sessions → My Progress | Trend chart chronological; history list matches; delete removes a session after confirmation | High |
| TC23 | Coach dashboard trends | F-16,F-17 | As coach, select players | Roster live-updates; per-player accuracy line chart matches that player's sessions | High |
| TC24 | Security rules enforced | N-05 | Player A tries reading player B's session (script/console); non-coach tries writing `drills` | Both denied by Firestore rules | High |
| TC25 | Frame rate on target device | N-02 | Live Session with both models for 2 min | Pose pipeline sustains ≥15 fps (log/profiler); no ANR; device temperature acceptable | High |

## 4. User Acceptance Testing (UAT)

Per the proposal: **two coaches and four players, two one-hour sessions** at the
NextGen Academy gymnasium. The facilitator observes without intervening and
records completion time + notes per task.

**Session A — players (4 participants):**
1. Install the app and create a player account.
2. Join the team using the code the coach provides.
3. Pick the Free Throw Series drill, set the phone on a stand, run the drill.
4. Read your session summary aloud (accuracy, shots, release time).
5. Find your progress screen and identify your best session.
6. Record a session with WiFi off; verify it appears later.

**Session B — coaches (2 participants):**
1. Create a coach account and the team; share the join code.
2. Watch the roster populate as players join.
3. Select each player and interpret their accuracy trend.
4. Compare the app's shot counts for one drill against your own manual tally
   (parallel running data point).
5. Attempt to find another team's data (expected: impossible).

Each participant then completes a short questionnaire (5-point Likert):
ease of setup, clarity of live feedback, trust in the metrics, usefulness of
reports, willingness to keep using the system — plus free-text comments.
All results are anonymised in the dissertation per the ethics commitments.

## 5. Test log template

| TC | Date | Tester | Device | Result (Pass/Fail) | Defect ID | Notes |
|----|------|--------|--------|--------------------|-----------|-------|
|    |      |        |        |                    |           |       |

**Defect log:**

| ID | TC | Severity (Blocker/Major/Minor) | Description | Status | Fixed in commit |
|----|----|-------------------------------|-------------|--------|-----------------|
|    |    |                               |             |        |                 |

**Exit criteria (per proposal):** all High-priority test cases pass; no open
Blocker/Major defects; N-03 accuracy gate met or a documented, supervisor-agreed
deviation recorded.

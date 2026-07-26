# Court Vision — Database Design (Phase 3)

**Backend:** Firebase Firestore (Native mode) + Firebase Authentication
**Status:** DRAFT — awaiting approval (Phase 3 checkpoint)

Firestore was mandated by the project documents (final proposal §3.3; literature review §3.4),
which rejected self-hosted MySQL/PostgreSQL (server maintenance out of scope) and DynamoDB.
The proposal specifies exactly four collections: `users`, `sessions`, `drills`, `teams`.

---

## 1. Entity relationships

```
User (Firebase Auth uid)
 └── users/{uid}                    role: player | coach
        │ teamId
        ▼
     teams/{teamId}                 1 team ── N players, N coaches (pilot: 1 team)
        ▲                           joinCode links players to a coach's team
        │ teamId (denormalized)
     sessions/{sessionId}           1 player ── N sessions; each references 1 drill
        │ drillId
        ▼
     drills/{drillId}               read-only catalogue, seeded by developer
```

Design notes:
- **Flat top-level collections** (no subcollections). The coach dashboard needs team-wide
  session queries; a flat `sessions` collection with a denormalized `teamId` allows one
  indexed query instead of N per-player reads.
- **`teamId` denormalized onto sessions** so the security rule for coach access can be
  evaluated per-document without joins (Firestore rules cannot join at query time).
- **No raw video anywhere** — approved decision C1 and legal requirement N-04.

---

## 2. Collections

### 2.1 `users/{uid}` — document ID = Firebase Auth UID

| Field | Type | Req | Description |
|---|---|---|---|
| `role` | string | ✔ | `"player"` or `"coach"`. Immutable after creation. |
| `displayName` | string | ✔ | Shown in rosters and dashboards. |
| `email` | string | ✔ | Mirrors the Auth record for display purposes. |
| `teamId` | string \| null | ✔ | Team membership. Null until the player joins / coach creates a team. |
| `createdAt` | timestamp | ✔ | Server timestamp at registration. |

### 2.2 `teams/{teamId}` — auto-generated ID

| Field | Type | Req | Description |
|---|---|---|---|
| `name` | string | ✔ | e.g. "NextGen U18". |
| `joinCode` | string | ✔ | Short human-typeable code (6 uppercase alphanumerics, e.g. `K7PQ2M`), generated at team creation. Players query on this to join. |
| `coachIds` | array\<string\> | ✔ | UIDs of coaches (creator initially). |
| `playerIds` | array\<string\> | ✔ | UIDs of joined players. |
| `createdAt` | timestamp | ✔ | Server timestamp. |

### 2.3 `drills/{drillId}` — fixed IDs (e.g. `free_throws`), seeded, read-only to clients

| Field | Type | Req | Description |
|---|---|---|---|
| `name` | string | ✔ | e.g. "Free Throw Series". |
| `category` | string | ✔ | `shooting` \| `dribbling` \| `reaction` \| `combined`. |
| `instructions` | string | ✔ | Step-by-step player instructions. |
| `targetMetrics` | map | ✔ | Goal values, e.g. `{ "shots": 20, "accuracyPct": 60 }`. |
| `durationSec` | number | ✔ | Recommended drill duration. |
| `measures` | array\<string\> | ✔ | Which metrics the CV pipeline records for this drill: subset of `shots`, `releaseTime`, `dribbles`, `reactionTime`. Drives which detectors the Live Session screen activates. |
| `sortOrder` | number | ✔ | Display ordering in the Drill Library. |

### 2.4 `sessions/{sessionId}` — auto-generated ID; one per completed drill run

| Field | Type | Req | Description |
|---|---|---|---|
| `playerId` | string | ✔ | Owning player's UID. Immutable. |
| `teamId` | string \| null | ✔ | Player's team at session time (denormalized for coach access rule). |
| `drillId` | string | ✔ | Drill performed. |
| `startedAt` | timestamp | ✔ | Session start. |
| `durationSec` | number | ✔ | Actual elapsed drill time. |
| `shotsAttempted` | number | ✔* | Total detected shot attempts. |
| `shotsMade` | number | ✔* | Detected made shots. |
| `accuracyPct` | number | ✔* | `shotsMade / shotsAttempted × 100` (0 when no attempts). |
| `releaseTimesMs` | array\<number\> | ✔* | Per-shot release times. |
| `avgReleaseTimeMs` | number | ✔* | Mean of the above. |
| `dribbleCount` | number | ✔* | Detected dribble events. |
| `dribbleSpeedHz` | number | ✔* | Bounces per second (mean inter-bounce interval inverted). |
| `reactionTimesMs` | array\<number\> | ✔* | Per-cue reaction times. |
| `avgReactionTimeMs` | number | ✔* | Mean of the above. |
| `appVersion` | string | ✔ | For debugging metric anomalies across releases. |

\* Metric fields are written as `0` / `[]` when the drill's `measures` doesn't include them —
keeping a single document shape keeps trend queries and chart code simple.

**Immutability & deletion:** sessions are never updated after creation (they are records of
fact). The owning player may delete their own sessions — this implements the documented legal
right to request deletion of personal data.

---

## 3. Access control matrix

| Collection | Player | Coach | Unauthenticated |
|---|---|---|---|
| `users` | create/read/update own doc (role immutable) | same + read docs of players on own team | none |
| `teams` | read (needed for join-by-code query); update only to add own UID to `playerIds` | create; read; update own team | none |
| `drills` | read | read | none |
| `sessions` | create own (playerId must equal own UID); read own; delete own | read sessions where `teamId` matches own team | none |

Enforced by `firebase/firestore.rules` (see file). Notes:

- **Join-by-code trade-off:** players must query `teams` where `joinCode == <typed code>`,
  which requires signed-in read access to team documents. Within a single-club pilot this is
  acceptable; documented as a known limitation. The update rule still guarantees a player can
  *only* add their own UID to `playerIds` and touch nothing else.
- **Coach access to player sessions** is checked per-document via a `get()` of the coach's own
  user profile (1 extra read per rule evaluation, cached per request) comparing `teamId`.
- **`drills` writes are denied to all clients** — seeding happens via the Firebase console /
  Admin SDK script, matching "publicly readable but write-protected" in the proposal.

---

## 4. Indexes

Composite indexes required (declared in `firebase/firestore.indexes.json`):

| Collection | Fields | Serves |
|---|---|---|
| `sessions` | `playerId ASC, startedAt DESC` | Player history & progress trends |
| `sessions` | `teamId ASC, startedAt DESC` | Coach dashboard team feed |
| `sessions` | `playerId ASC, drillId ASC, startedAt DESC` | Per-drill trend chart |

Single-field queries (`teams.joinCode`, `drills.sortOrder`) use automatic indexes.

---

## 5. Offline behaviour

- Firestore SDK offline persistence is **enabled** — sessions recorded at the gym without
  connectivity are queued locally and sync automatically when online (requirement N-01).
- The drill catalogue is fetched with cache-first reads so the Drill Library works offline
  after first launch.

## 6. Data protection mapping (from the proposal's legal section)

| Legal commitment | Schema mechanism |
|---|---|
| No raw video retained | No video field exists anywhere; storage bucket not used |
| Data minimisation | Only computed metrics + minimal identity fields stored |
| Access restricted to player + own coach | Security rules (§3) |
| Right to deletion | Player may delete own sessions; account deletion removes user doc |

## 7. Starter drill catalogue

The documents specify the drill library must exist but list no actual drills.
`firebase/seed/drills.json` contains an 8-drill starter catalogue (2 per category) covering
every metric the CV pipeline measures — **review the drill content and adjust freely**;
it seeds the read-only `drills` collection.

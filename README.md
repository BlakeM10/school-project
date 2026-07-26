# Court Vision

Mobile basketball performance analytics for NextGen Basketball Foundation (Academy).
An Android app that uses on-device computer vision (MediaPipe BlazePose + TensorFlow Lite)
to automatically measure shooting accuracy, shot release time, dribble count/speed, and
reaction time during training drills — with real-time feedback, a drill library, and
longitudinal progress reports backed by Firebase Firestore.

## Project documents

| Document | Purpose |
|---|---|
| `docs/DEVELOPMENT_BLUEPRINT.md` | Approved system blueprint (architecture, requirements, roadmap) |
| `docs/DATABASE_SCHEMA.md` | Approved Firestore database design |
| `firebase/` | Security rules, indexes, drill seed data |

## Building

Standard Android Gradle project — works from Android Studio or any editor (VS Code)
with the Android SDK installed.

```bash
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run unit tests (JUnit 4 + Mockito)
```

Requirements: JDK 17+, Android SDK (API 34). In VS Code, install the "Android"
and "Kotlin" extensions, and set `ANDROID_HOME` or create `local.properties`
with `sdk.dir=/path/to/Android/sdk`.

## Firebase setup (one-time, per developer)

1. Create a Firebase project at https://console.firebase.google.com (Spark/free tier).
2. Add an Android app with package name `com.nextgen.courtvision`.
3. Download `google-services.json` into `app/` (it is gitignored on purpose).
4. Enable **Authentication → Email/Password and Google** sign-in providers.
5. Create a **Firestore database** (Native mode), then deploy rules and indexes:
   ```bash
   cd firebase
   firebase deploy --only firestore
   ```
6. Seed the drill catalogue:
   ```bash
   cd firebase/seed
   npm install firebase-admin
   GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> node seed_drills.js <project-id>
   ```

For Google Sign-In on a debug build, add your debug keystore's SHA-1 to the
Firebase project settings (`./gradlew signingReport` prints it).

## CI

GitHub Actions builds the app and runs the unit test suite on every push
(`.github/workflows/ci.yml`), per the proposal's continuous integration requirement.

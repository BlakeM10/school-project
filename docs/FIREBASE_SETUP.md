# Firebase Setup — Click-by-Click Guide

One-time setup (~10 minutes). Needed only before running the app on a real phone;
it does not block development. No prior Firebase experience assumed.

> **Shortcut:** if you have Claude Code installed locally (VS Code extension or CLI),
> open this project folder and say: *"Do the Firebase setup in docs/FIREBASE_SETUP.md"* —
> it will run steps 4–7 for you and walk you through 1–3.

## 1. Create the Firebase project (browser)

1. Go to https://console.firebase.google.com and sign in with any Google account.
2. Click **Create a Firebase project** (or "Add project").
3. Project name: `court-vision` → **Continue**.
4. When asked about Google Analytics, toggle it **off** (not needed) → **Create project** → wait → **Continue**.

## 2. Register the Android app and download the config file

1. On the project home page, click the **Android icon** (robot) under "Get started by adding Firebase to your app".
2. **Android package name:** `com.nextgen.courtvision` (must be exactly this).
3. Leave nickname and SHA-1 empty for now → **Register app**.
4. Click **Download google-services.json**.
5. Move that file into the `app/` folder of this project (next to `app/build.gradle.kts`).
   It is deliberately gitignored — never commit it.
6. Click **Next → Next → Continue to console** (skip the SDK instructions; already done).

## 3. Turn on the two sign-in methods

1. Left sidebar → **Build → Authentication** → **Get started**.
2. Click **Email/Password** → toggle **Enable** (top switch only) → **Save**.
3. Click **Add new provider** → **Google** → toggle **Enable** → pick your support email → **Save**.

## 4. Create the Firestore database

1. Left sidebar → **Build → Firestore Database** → **Create database**.
2. Location: pick `europe-west1` (or any region; cannot be changed later) → **Next**.
3. Choose **Start in production mode** → **Create**. (Our own rules are deployed next.)

## 5. Deploy the security rules and indexes (terminal)

In VS Code, open a terminal in the project folder and run:

```bash
npm install -g firebase-tools     # once
firebase login                    # opens a browser; sign in with the same Google account
cd firebase
firebase use --add               # pick the court-vision project, alias: default
firebase deploy --only firestore
cd ..
```

## 6. Seed the drill catalogue (terminal)

1. In the Firebase console: **Project settings (gear icon) → Service accounts →
   Generate new private key** → save the file as `serviceAccount.json` in the
   `firebase/seed/` folder (also never committed — add it nowhere else).
2. Run:
   ```bash
   cd firebase/seed
   npm install firebase-admin
   GOOGLE_APPLICATION_CREDENTIALS=serviceAccount.json node seed_drills.js court-vision
   cd ../..
   ```
   (If your project got a suffixed ID like `court-vision-4f2a1`, use that ID —
   it is shown in Project settings → General → Project ID.)

## 7. Enable Google Sign-In for your debug builds (terminal)

```bash
./gradlew signingReport
```

Copy the **SHA1** line under `Variant: debug`, then in the Firebase console:
**Project settings → General → Your apps → Add fingerprint** → paste → **Save**.
Re-download `google-services.json` and replace the one in `app/` (it now contains
the OAuth client Google Sign-In needs).

## Done — verify

Connect your Android phone (USB debugging on) and run:

```bash
./gradlew installDebug
```

Open Court Vision on the phone: create an account with email/password, and try
"Continue with Google". Both should land you on the placeholder home screen.

## Troubleshooting

| Symptom | Fix |
|---|---|
| App crashes at launch | `google-services.json` missing from `app/` — redo step 2.4–2.5 |
| "Google Sign-In is not configured" | Same as above — config file missing at build time |
| Google sign-in fails/cancels instantly | SHA-1 fingerprint missing — redo step 7 |
| "PERMISSION_DENIED" errors in-app | Rules not deployed — redo step 5 |
| Empty drill library later on | Seeding not run — redo step 6 |

# Court Vision — User Manual

*For NextGen Basketball Foundation coaches and players.*
*(Screenshot placeholders marked 📷 — capture during UAT sessions.)*

## 1. Installing the app

1. Ask your coach (or the project developer) for the `CourtVision.apk` file.
2. Copy it to your Android phone (Android 10 or newer) and tap it to install.
   If asked, allow your file manager to "install unknown apps".
3. Open **Court Vision**. 📷

## 2. Creating your account

1. Tap **Create an account**.
2. Enter your full name, email, and a password (at least 6 characters).
3. Choose **Player** or **Coach** — this decides which screens you see.
4. Tap **Create account**. You can also use **Continue with Google**; first-time
   Google users are asked for their name and role afterwards. 📷

## 3. For coaches: create your team

1. After signing in you land on the **Coach Dashboard**.
2. Enter your team name and tap **Create team**.
3. A **6-character join code** appears — share it with your players. 📷

## 4. For players: join your team

1. On the **Drill Library** screen, tap **Join a team** in the banner.
2. Type the code from your coach. The banner disappears once you're in.

## 5. Recording a training session (players)

1. Put the phone on a stand or stable surface, side-on to the play area, so
   **both you and the ball stay in frame** (for shooting drills, include the
   basket direction of travel).
2. Pick a drill from the **Drill Library** — its card shows instructions and
   duration.
3. Allow camera access when asked (first time only).
4. Check the green **skeleton** appears over your body and a green **box** over
   the ball. If you instead see a warning about missing detection models, the
   app was built without them — tell the developer. 📷
5. Tap **Start drill**. The top bar shows the timer and your live counts.
6. During the drill: a **high tone** = made shot detected, a **low tone** =
   missed shot. In reaction drills, move as fast as you can on each **beep**.
7. Tap **Finish drill** when done.

## 6. Reading your session summary

- The **ring** shows your shooting accuracy for the session.
- The bars show **made vs missed** shots.
- Below: release time, dribbles (and per-second speed), reaction time, and
  duration. "Not measured in this drill" is normal for metrics that drill
  doesn't track. 📷

## 7. Tracking your progress (players)

Tap **My progress** in the Drill Library. The chart shows your shooting
accuracy across sessions; the list below has every session. Deleting a session
(bin icon → confirm) permanently removes it — this is your right, but it cannot
be undone.

## 8. The Coach Dashboard

- **Players** lists everyone who joined with your code — it updates live.
- Tap a player to see their **accuracy trend** chart.
- No internet at the gym? Players' sessions appear automatically the next time
  their phone is online.

## 9. Troubleshooting

| Problem | What to do |
|---|---|
| App crashes at launch | The build is missing its Firebase configuration — contact the developer |
| "Google Sign-In is not configured" | Use email/password, or contact the developer |
| Camera screen is black | Check camera permission: Settings → Apps → Court Vision → Permissions |
| No skeleton/ball box | Poor lighting or player too far away; move closer / brighten the gym. If a "models not installed" warning shows, the build is incomplete |
| Shots not being counted | Keep your whole body and the ball in frame; avoid other people walking through the frame |
| Session not on coach's dashboard | The recording phone hasn't been online since the session — connect it to the internet once |
| Wrong team joined | Contact the developer to move you (there is no in-app leave-team yet) |

## 10. Your data and privacy

- The camera image is processed **on the phone only** — no video is ever saved
  or uploaded. Only numbers (shots, times, counts) are stored.
- Only you and your team's coach can see your sessions.
- You may delete any of your sessions at any time, and you may ask for your
  account and all your data to be removed.

# Wake Challenge Alarm

An Android alarm clock that only stops ringing once you've completed a
"wake-up challenge" — with a roommate-friendly twist: the ring volume
automatically ducks down the moment it detects you've picked the phone up
and are moving, and comes back up if you set it back down. Completing your
chosen challenge is what actually dismisses the alarm.

## How it works

- **Volume ducking (always on):** the accelerometer detects sustained
  motion vs. stillness. Moving → volume drops. Still (e.g. phone back on
  the bed) → volume returns to full. This runs independently of whichever
  challenge you've picked, so a shared apartment doesn't get blasted while
  you're doing the challenge.
- **Challenge library:** build a reusable list of challenges (menu → Manage
  Challenges). Each alarm picks one **primary** and, optionally, one
  **secondary/backup** challenge.
- **Challenge types:** Photo (with blur + reference-similarity checks),
  Steps, Jumping Jacks, Recite (speech-to-text match), Math.
- **Customizable sound, per alarm:**
  - Pick any system/device ringtone via the standard Android ringtone
    picker, **or**
  - Play a random track from your own **Music Pool** each time the alarm
    fires (menu → Music Pool, or from within an alarm's editor). Add songs
    from your device's storage; the app keeps a persisted link to them so
    they survive reboots. Tap the play icon next to a song to preview it.
  - A per-alarm **ring volume** slider (this is the "full" volume level —
    the motion-ducking feature always drops proportionally from whatever
    you set here).

## Getting an installable APK — no Android Studio required

This project includes a GitHub Actions workflow
(`.github/workflows/build-apk.yml`) that builds a ready-to-install debug
APK for you automatically, for free, using only a web browser:

1. Create a free [GitHub](https://github.com) account if you don't have one.
2. Create a new **empty repository** (Add file → nothing needed yet).
3. Upload this whole project folder into it. Easiest way: on the repo
   page, click **Add file → Upload files**, then drag in everything from
   this `WakeChallengeAlarm` folder (including the hidden `.github` folder
   — if your OS hides it, use `git` instead: `git init`, `git add .`,
   `git commit -m "init"`, `git remote add origin <your-repo-url>`,
   `git push -u origin main`).
4. Once pushed, go to the **Actions** tab of your repo. A workflow run
   called "Build APK" should start automatically (or click **Run
   workflow** if it doesn't).
5. Wait for it to finish (a few minutes), then open the completed run and
   download the **wake-alarm-debug-apk** artifact — it's a zip containing
   `app-debug.apk`.
6. Transfer that `.apk` to your phone (email it to yourself, Google Drive,
   USB, etc.), open it, and allow "install unknown apps" for whichever app
   you used to open it. It's a signed debug build, so it installs directly
   — no separate signing step needed.

This gives you a real installable APK without ever installing Android
Studio locally.

## Opening in Android Studio instead (optional)

If you do get access to Android Studio later: `File → Open`, select this
folder, let it generate the Gradle wrapper when prompted, sync, and run.

## First run checklist

On first launch you may be prompted for:
- **Notifications** permission (Android 13+) — needed for the alarm's
  full-screen notification.
- **"Alarms & reminders" (exact alarm) permission** (Android 12+) — the
  app will send you to system settings if it's not already granted.
- **Camera** and **microphone** permissions — only requested the first
  time you actually use a Photo or Recite challenge.

Also worth doing manually on most phones: exempt the app from battery
optimization (Settings → Apps → [app name] → Battery → Unrestricted), so
the OS doesn't kill the ringing service overnight.

## Project structure

```
app/src/main/kotlin/com/wakechallenge/alarm/
  data/    Room entities, DAOs, database, per-goal-type config helpers, music pool
  alarm/   AlarmScheduler, AlarmReceiver, BootReceiver, AlarmRingingService
  ring/    Full-screen ring UI + one fragment per challenge type
  ui/      Alarm list, alarm editor, challenge library, challenge editor, music pool
  util/    Image hashing/blur detection, text similarity, motion/rep counting
```

## Notes / things you may want to tune

- Volume-ducking sensitivity: `MOVING_VARIANCE_THRESHOLD` and `debounceMs`
  in `util/Motion.kt` (`MotionStateMonitor`).
- Ducked volume is always 30% of your chosen ring volume — change the
  `0.3f` multiplier in `AlarmRingingService.startRinging()` to adjust.
- Photo match strictness: adjustable per-challenge via the slider in the
  challenge editor (stored as `similarityThreshold`, 0–40; lower = stricter).
- Blur cutoff: `sharpnessMinScore` in `ring/PhotoChallengeFragment.kt`.
- Step/jumping-jack sensitivity: `MotionRepCounter.forSteps` /
  `.forJumpingJacks` in `util/Motion.kt`.

## Renaming the app

To rename it, change `app_name` in
`app/src/main/res/values/strings.xml` — no code changes needed. The
Android package/applicationId (`com.wakechallenge.alarm`) can stay as-is;
it doesn't need to match the display name.


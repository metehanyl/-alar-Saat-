# Çalar Saat — iOS

SwiftUI/SwiftData port of the Android Çalar Saat app: alarms, alarm groups,
per-alarm PIN dismiss, synthesized melodies, and Sabah Namazı (imsak-time
auto-alarms).

This source was written without a Mac — there is no Swift/Xcode toolchain in
the environment that generated it, so **none of it has been compiled**. Build
and fix any errors on your own Mac before relying on it.

## Build steps

1. Install XcodeGen (one-time): `brew install xcodegen`
2. From this `ios/` directory: `xcodegen generate`
3. Open the generated project: `open CalarSaat.xcodeproj`
4. In Xcode, select the `CalarSaat` target → *Signing & Capabilities* → set
   your Team (a free Apple ID works for running on your own device for up to
   7 days at a time; a paid $99/year Developer account is needed for
   TestFlight or longer-lived installs).
5. Plug in an iPhone, select it as the run destination, and Build & Run.

If Xcode reports build errors, they're expected on the first pass since this
was never compiled — fix them directly in Xcode and re-run.

## Known platform limitations vs. the Android app

iOS does not give third-party apps the same low-level control Android's
`AlarmManager` does, so a few things behave differently here even once it
builds cleanly:

- **No guaranteed wake/alert.** Alarms are local notifications
  (`UNUserNotificationCenter`), not an OS-level alarm clock. iOS, not the
  app, decides exact delivery timing and can delay or coalesce them under
  some conditions.
- **No silent-mode/Focus bypass.** Apple's "critical alerts" entitlement,
  which can override Do Not Disturb/silent mode, requires a special grant
  from Apple that this project doesn't have. A muted phone may not play the
  alarm sound.
- **No true lock-screen takeover.** Android shows a full-screen ringing
  activity over the lock screen automatically. On iOS, the notification
  shows a banner + sound; the user must tap it to open the app and see the
  `AlarmRingView` ring/PIN screen. While the app is in the foreground, the
  ring screen does appear immediately.
- **Sabah Namazı's daily background refresh is best-effort.** Android
  schedules an exact nightly recompute via `AlarmManager`. iOS only offers
  `BGAppRefreshTask`, which the system may delay or skip entirely some days.
  To compensate, the app also self-heals on every launch/foreground
  (`SabahNamaziManager.healIfNeeded`) by regenerating the day's auto-alarms
  if none exist — this, not the background task, is the reliable path.
- **Notification sounds are capped at ~30 seconds and don't loop on their
  own.** Each melody is pre-rendered to a ~28s `.wav` (see
  `Scripts/generate_alarm_sounds.py`) so the notification's one-shot sound
  covers a long stretch; continuous looping only happens once the app is
  open (foreground delivery or the user tapping into the ring screen), via
  `AlarmTonePlayer`'s live `AVAudioEngine` synthesis.

## Distribution

There's no APK-style sideloading on iOS. To get this onto an iPhone you need
a Mac with Xcode and an Apple ID, at minimum to build-and-run over USB/WiFi
from Xcode directly (free, but the install expires after about 7 days and
must be re-run from Xcode to renew). TestFlight or the App Store require a
paid Apple Developer Program membership.

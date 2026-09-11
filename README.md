# Ojas

A personal assistant that lives entirely on your phone.

Ojas covers water, workouts, meditation, alarms, reminders, screen time and a calendar,
behind a space-themed interface built around a procedurally generated 3D galaxy you can
turn with your finger.

**There is no `android.permission.INTERNET` anywhere in the manifest.** The app cannot
open a socket even if it wanted to. Everything you log stays in a SQLite database on the
device.

## What it does

| Area | Detail |
| --- | --- |
| **Ojas (the assistant)** | A command bar on the home screen. Deterministic on-device intent matching, no model file and no network: "log 250 ml", "set an alarm for 6:30 am", "remind me to stretch in 20 minutes", "meditate 10 minutes", "how am I doing". |
| **Water** | Daily goal, cup size, quick-log amounts, per-drink history, seven-day chart, and hydration nudges that reschedule from your last drink rather than on a fixed grid. |
| **Workouts** | Five built-in routines plus a full editor. The session runner flattens a routine into work/rest steps with timed holds, rep counting, rest countdowns and a session log. |
| **Meditation** | Five breathing rhythms (box, 4-7-8, coherent, grounding, silent) with a pacer that animates for exactly the length of each phase, plus streaks and history. |
| **Alarms** | Repeat days, labels, gradual fade-in, vibration, snooze, skip-one-occurrence, and a full-screen ring surface that turns the display on over the lock screen. |
| **Reminders** | One-shot or repeating (hourly through yearly), with snooze from the notification shade. |
| **Calendar** | Month grid, day agenda, colour-coded events, repeat rules and alerts ahead of time. |
| **Screen time** | Per-app usage from Android's own usage statistics, daily budgets, warn-or-block enforcement, and focus sessions that hold limited apps shut. |

## Firing with the screen off

This is the part that usually breaks in apps like this, so it is worth stating how it works:

- Alarm-clock entries use `AlarmManagerCompat.setAlarmClock`, which is exempt from Doze
  and from the exact-alarm permission gate, and surfaces the next-alarm icon in the status bar.
- Reminders, calendar alerts and hydration nudges use `setExactAndAllowWhileIdle`,
  degrading to `setAndAllowWhileIdle` if the user revokes exact alarms.
- A ringing alarm is owned by a foreground service, so it survives the app being swiped
  away, and posts a full-screen intent that brings up `AlarmRingActivity` over the keyguard.
- `BootReceiver` replays the whole schedule out of the database on boot, locked boot,
  app update, timezone change and clock change, because AlarmManager forgets everything
  across all of those.
- A six-hourly `MaintenanceWorker` re-arms anything that slipped and snapshots screen time.

## The galaxy

`GalaxyModel` generates a two-armed logarithmic spiral — arm stars, a bulge and a sparse
halo — once, into a single interleaved float buffer that is uploaded to one VBO and never
touched again. Each frame is two additive draw calls over that buffer: a large, very faint
pass that produces the milky glow along the arms, and a small, bright pass for the star
cores. No depth buffer, no sorting, no per-frame allocation.

Rotation is a rigid spin in the vertex shader with a small differential term, so the arms
shear slowly the way a real disc does. Drag turns it like a turntable, pinch moves the
camera, and a fling decays into the ambient drift.

There is one renderer for the entire app, hoisted above the nav host, so screens fade over
a continuously running backdrop instead of each spinning up its own.

## Battery

- The galaxy is frame-capped to 30fps and pauses with the host lifecycle. **Reduce motion**
  in settings parks it entirely and switches the surface to render-on-demand, which takes
  its cost to zero.
- Screen-time enforcement only polls while the screen is on, and widens its interval from
  15s to 3 minutes automatically when nothing is near its cap.
- Every daily rollup is indexed on a denormalised `epochDay` column, so the "today" queries
  that run on almost every screen are answered from an index.
- Release builds are R8-shrunk with full mode: **1.6 MB**.

## Build

Requires JDK 17+ and Android SDK 36.

```bash
./gradlew assembleDebug
```

The release variant is signed with the debug key so `assembleRelease` works out of the box
for personal side-loading — swap in a real keystore in `app/build.gradle.kts` before
distributing anything.

Min SDK 26, target SDK 36. Kotlin 2.1, Compose BOM 2025.06, Room 2.7, hand-rolled DI.

## Permissions

| Permission | Why | Required? |
| --- | --- | --- |
| Exact alarms | Alarms firing to the second in Doze | Yes, for alarms |
| Notifications | Reminders and alarm alerts | Yes |
| Unrestricted battery | Stops the system deferring Ojas overnight | Recommended |
| Usage access | Reading screen time | Only for screen time |
| Display over other apps | Hard app blocking | Optional; without it Ojas warns instead |

<div align="center">

<img src="docs/icon.png" width="120" alt="Upcroc icon" />

# Upcroc

**Upwork job alerts for Android. Local search, local notifications.**

[![Android](https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.09-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Logisite/upcroc?label=release)](https://github.com/Logisite/upcroc/releases/latest)

</div>

Upcroc watches Upwork searches from your phone and shows new matches as notifications. Searches, jobs, settings and the account token stay on the device.

## What it does

- Up to three search slots: one smart profile search and two keyword groups.
- One shared filter panel for budget, type, experience, location, proposals, client history, words and more.
- Checks on a random gap between 90 and 300 seconds.
- Local notifications with your own sound. Tap one to open the job on Upwork.
- Save jobs, hide jobs, and keep a local history.
- Quiet hours slow checks down and silence alerts.
- Diagnostics with checks, alerts, quota, live status and a seven-day event log.
- Layer 0 for raw JSON and interval overrides.
- True black surfaces, one blue accent, Manrope type.

## Find here, apply there

Upcroc signs in with OAuth and reads job listings. Job details link back to Upwork, where you apply, message clients and manage proposals.

## How it works

Upcroc runs on the phone. An alarm wakes it, it asks Upwork for the searches that are due, stores new matches, posts notifications, and sleeps until the next gap.

```text
you set searches and filters
              ↓
        alarm fires
              ↓
   Android wakes Upcroc
              ↓
     ask Upwork once
              ↓
  filter, deduplicate, store
              ↓
      post a notification
              ↓
 pick the next random gap
              ↓
            sleep
```

### The loop

1. **Set up once.** You connect an Upwork account and choose up to three searches. One slot is a smart profile search, two are keyword groups. All three share the same filter panel.
2. **Wake.** An exact alarm starts the watcher for that check.
3. **Ask.** The watcher checks which search slots are due and calls Upwork for each one that is.
4. **Compare.** Results are filtered, deduplicated and compared with what is already stored.
5. **Store.** New jobs go into the local database before anything else happens.
6. **Notify.** Jobs that are new enough get a local notification. Tapping it opens the job on Upwork.
7. **Rest.** The app picks the next gap and goes back to sleep until the next alarm.

### Timing

Each search slot has a minimum and maximum gap. After each check the app chooses a random gap between them, with a five-minute ceiling. The default is 90 to 300 seconds.

Checks use irregular gaps. Quiet hours use slower intervals and silence alerts. A daily request budget caps polling, and alerts cover jobs up to ten minutes old.

### Background

Android only lets an app do so much in the background, so Upcroc layers its options:

- exact alarms for the next check,
- a special-use foreground service for the actual work,
- a watchdog that notices a missed check,
- a WorkManager backstop if the service cannot start,
- a boot receiver only when you ask it to resume.

When the phone delays background work, the UI shows the real last-check age.

### On the device

The device holds:

- the search configuration,
- the job database and check history,
- the OAuth token, encrypted with the Android Keystore,
- notifications and the seven-day event log.

## Install

Download the latest signed APK from [Releases](https://github.com/Logisite/upcroc/releases/latest).

```sh
adb install app-direct-release.apk
```

Open Upcroc, finish the short setup, and sign in to Upwork.

## Build

You need JDK 21 or newer and the Android SDK.

```sh
./gradlew :app:assembleDirectDebug
```

APK: `app/build/outputs/apk/direct/debug/app-direct-debug.apk`

```sh
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk
adb shell am start -n app.upcroc/.MainActivity
```

Release build:

```sh
./gradlew :app:assembleDirectRelease
```

## Tech

| Layer | What is used |
| --- | --- |
| UI | Kotlin, Jetpack Compose, a hand-built design system, Haze glass dock |
| State | ViewModel, StateFlow, DataStore |
| Storage | Room, Android Keystore for the OAuth token |
| Network | OkHttp, Upwork MCP over HTTPS |
| Scheduling | AlarmManager, foreground service, WorkManager backstop |
| Images | Custom crescent mark, Manrope |

The controls, type ramp, dock, sheets and dialogs are drawn in Compose.

## Project layout

```text
app/                 Android app, UI, services, Room, notifications
core/                Pure Kotlin engine, MCP client, search, config, filters
prototype/           The original HTML design reference
docs/                README images
```

## License

GPL-3.0. See [LICENSE](LICENSE).

# Upcroc

Android-only Upwork job watcher. Kotlin, Compose, Room, OkHttp. Read-only: it never applies to jobs or changes anything on Upwork.

Package: `app.upcroc` · License: GPL-3.0 · minSdk 26 · targetSdk 36 · compileSdk 37

Repo: <https://github.com/Logisite/upcroc>

## What it does

- Signs in to Upwork with OAuth 2.1 + PKCE through a loopback Custom Tab.
- Watches one primary smart search plus up to two keyword groups, all sharing the Upwork-style filters.
- Stores every job in Room, including client stats and invite activity.
- Posts a high-priority local notification with the user's sound; tapping opens the job URL.
- Survives swipes and process death with a `specialUse` foreground service, exact-alarm scheduling, a watchdog, and a WorkManager backstop.
- Restores the last screen, feed scroll position, and unfinished search draft after a cold start.
- Never trusts server ordering: results are sorted by `published_date` client-side.

## Build

```sh
# JDK 25 (Temurin) is used here. Gradle is capped to two cores by choice.
./gradlew :app:assembleDirectDebug --max-workers=1
```

APK: `app/build/outputs/apk/direct/debug/app-direct-debug.apk`

Flavors:
- `direct` — in-app updater enabled, `REQUEST_INSTALL_PACKAGES` declared.
- `foss` — no updater permission, suitable for F-Droid.

## Install on the phone

```sh
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk
adb shell am start -n app.upcroc/.MainActivity
```

## Architecture

```
core/   pure Kotlin/JVM
  auth/       PKCE, loopback listener, OAuth client, token set
  mcp/        thin OkHttp MCP client (initialize, tools/list, tools/call, inline SSE)
  engine/     PollingEngine, RateBudget, EngineState
  config/     WatcherConfig, cadence, notifications, budget, advanced
  filter/     shared filters, MCP params, quiet hours
  model/      Job, SearchGroup, wire DTOs
  metrics/    latency stats
  notify/     notification text formatting

app/    Android
  auth/       Custom Tabs + AuthRepository (TokenProvider)
  data/db/    Room: jobs, outbox, checks, engine state
  data/prefs/ DataStore: config, draft, UI state, running flag
  data/secure/ Android Keystore AES-GCM token store
  service/    WatcherService, AlarmReceiver, BootReceiver, BackstopWorker, scheduler
  notify/     channels + AndroidNotifier
  ui/         Compose onboarding, feed, search, settings, diagnostics, Layer 0
  update/     signed manifest check + verified APK install
```

## Durability

The database is the source of truth:

- Jobs are inserted **before** a notification is posted.
- A transactional `notification_outbox` replays anything that was queued but not posted.
- `check_runs` / engine state persist the next due time, budget, and backoff.
- UI state (route, scroll anchor, draft) is in DataStore.
- On cold start: mark stale checks interrupted, replay pending notifications, resume if the checkpoint is recent, restore the screen and scroll anchor.

## Scheduling

- Every search slot has a minimum and maximum gap. The default is 90 to 300 seconds.
- After each check the next gap is chosen uniformly at random inside that range. There is no fixed two-minute ping and no Gaussian noise.
- Five minutes is the hard maximum, so a job should not wait more than roughly five to six minutes for the next look.
- Quiet hours use the slower quiet intervals and silence alerts.
- Jobs older than ten minutes at notification time are dropped. The limit is configurable in Settings → Alert age and in Layer 0.
- Exact alarms are used when the user grants “Alarms & reminders”. In Doze Android may still stretch them; Diagnostics shows the gap honestly.

## Auth client

`app/src/main/assets/oauth_client.json` currently uses the **opencode** public native client so the app can be tested today. The rest of the auth stack is a pluggable provider; replace that one file with an `upcroc` client registration and rebuild. No engine, UI, or storage code changes.

## Quota

Upwork's published search limit is 15/minute, 150/hour, 750/day. The app defaults to a 750/day soft budget, warns at 90%, handles 429 `retry_after`, and never hard-blocks unless the user enables it in Layer 0. Quiet hours and the default intervals keep the default schedule under the daily cap.

## Updates

`direct` checks `latest.json` from this repository's latest GitHub release, at most once every 24 hours, and via Settings. It verifies SHA-256 and the APK signing certificate before opening the system installer. See `latest.example.json`.

The repository is public, so release assets are publicly downloadable. If it ever becomes private, updates stop working until the APK and manifest move to public storage.

`foss` has `SELF_UPDATE=false` and no install-packages permission.

## Releases

GitHub Actions does not run on push. Releases are manual: open Actions → Android → Run workflow, choose whether it is a release, and provide the tag and changelog. A normal run builds and uploads a debug APK only.

Required repository secrets for a release:

- `KEYSTORE_BASE64`: base64 of the release keystore
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Locally, put the same values in `keystore.properties` (ignored by git):

```properties
storeFile=/absolute/path/to/upcroc-release.jks
storePassword=...
keyAlias=upcroc
keyPassword=...
```

Then run the manual release workflow with `release = true` and a tag such as `v0.1.0`. It builds `app-direct-release.apk`, writes `latest.json` with the real version, SHA-256 and download URL, and publishes both assets on the latest GitHub release.

## Known limits

- Force-stopping the app from Android settings stops it until the app is opened again. Android does not restart force-stopped apps.
- OEM battery managers (TECNO/Infinix/itel, Samsung, Xiaomi, etc.) can still kill background apps. Settings → Battery opens the device-specific autostart and recents-lock steps.
- Doze can delay checks beyond the configured interval. FCM would be required for guaranteed sub-minute delivery; this app deliberately does not use it.
- Push apps such as Discord and Telegram are delivered by Google Play Services or APNs. Upcroc is local-only and can only approximate that with alarms, a foreground service, a watchdog and WorkManager.
- Reusing the opencode OAuth client is a temporary testing choice. Upwork rate limits and terms still apply to the user's account.

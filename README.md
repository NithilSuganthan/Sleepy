# Sleepy 🌙

> **Fall asleep. We'll handle the music.**

**Sleepy** is a native Android sleep-music timer designed to let you fall asleep to your favorite music without worrying about leaving playback running all night.

Set a timer, keep your music playing, and Sleepy takes care of the rest.

When the timer expires, the app attempts to gracefully fade out and pause active media playback, while using multiple Android mechanisms to make the timer reliable even when the app is no longer in the foreground.

---

## Overview

Sleepy is built around a simple idea:

```text
Start Music
     │
     ▼
Set Sleep Timer
     │
     ▼
Go to Sleep 😴
     │
     ▼
Timer Expires
     │
     ▼
Fade Audio
     │
     ▼
Pause Active Playback
     │
     ▼
Verify Playback Stopped
```

The application is designed to work with external media players rather than requiring music to be played inside Sleepy itself.

---

# Features

## ⏱️ Sleep Timer

Set a countdown for your desired sleep duration.

Sleepy maintains the timer through a dedicated timer manager and keeps the expiration state synchronized with persistent preferences.

Supported timer actions include:

- Start
- Pause
- Resume
- Cancel
- Add 10 minutes
- Automatic expiration

---

## 🎵 External Music Control

Sleepy can interact with active Android media sessions to control external playback.

The app can detect active media sessions and retrieve information such as:

- Track title
- Artist
- Application/package name
- Playback state
- Album artwork

This allows Sleepy to work alongside supported music applications instead of replacing them.

---

## 🔇 Automatic Playback Pause

When the timer reaches zero, Sleepy attempts to stop active playback using several strategies.

The pause workflow can use:

1. Android `MediaController` transport controls
2. Media button events
3. Targeted media-button broadcasts
4. Audio focus interruption
5. Volume muting as a final failsafe

After attempting to pause playback, Sleepy verifies whether playback has actually stopped.

```text
Timer Expired
     │
     ▼
Pause Request
     │
     ├── MediaSession pause
     │
     ├── Media button event
     │
     ├── Broadcast fallback
     │
     └── Audio focus
     │
     ▼
Playback Verification
     │
   ┌─┴─┐
   │   │
Stopped  Still Playing
   │        │
   │        ▼
   │     Retry
   │        │
   │        ▼
   │      Failsafe
   ▼
Timer Complete
```

The application records whether the pause operation succeeded and exposes diagnostic information for troubleshooting.

---

## 🌊 Audio Fade-Out

Instead of immediately stopping playback, Sleepy can gradually fade the music before the timer expires.

The fade duration can be configured through sleep settings.

Conceptually:

```text
100% ────────────────┐
                     │
                     │\
                     │ \
                     │  \
                     │   \
                     │    \
0%   ────────────────┴─────
                     Time
```

This creates a more gradual transition into sleep.

---

## 🔋 Low-Battery Protection

Sleepy can monitor battery conditions while the sleep timer is running.

A configurable low-battery threshold can automatically stop the sleep timer when the device reaches the configured level.

This helps avoid unnecessarily draining the device overnight.

When the device is charging, the low-battery protection does not trigger.

---

## 🎧 Headphone Safety Handling

Sleepy can monitor Android's `ACTION_AUDIO_BECOMING_NOISY` event.

When headphones or another audio output is disconnected, the application can trigger its timer-expiration handling instead of allowing playback to continue unexpectedly through the device speaker.

---

## 🔄 Timer Recovery After Reboot

Sleepy is designed to recover active timer state.

The application persists the timer's expiry timestamp and registers a boot receiver so the state can be restored after the device restarts.

The recovery workflow is approximately:

```text
Device Reboots
     │
     ▼
Boot Receiver
     │
     ▼
Read Saved Expiry
     │
     ├── Still Active ──► Restore Timer
     │
     └── Already Expired ──► Run Expiration Logic
```

A backup alarm is also scheduled to provide another expiration mechanism.

---

## ⏰ Exact Alarm + Backup Scheduling

Sleepy uses Android's `AlarmManager` as a backup timer mechanism.

Where permitted, the application uses exact alarms that can operate during idle conditions.

When exact-alarm access is unavailable, the application has fallback behaviour rather than relying on only a foreground countdown.

---

## 🔔 Foreground Service

Sleepy uses a foreground service while an active timer is running.

The service maintains the timer's background execution and exposes an ongoing notification with useful actions.

The notification can provide controls such as:

- Cancel
- +10 minutes
- Pause Now

This allows the timer to remain controllable without reopening the application.

---

## ⚡ Quick Settings Tile

Sleepy includes an Android Quick Settings tile.

This allows the sleep-timer functionality to be accessed directly from the Android system Quick Settings panel.

```text
Quick Settings
       │
       ▼
   Sleepy Tile
       │
       ▼
 Sleep Timer Control
```

---

# Architecture

Sleepy follows a layered Android architecture separating application logic, persistence, services, and presentation.

```text
                 ┌──────────────────┐
                 │   Jetpack Compose│
                 │       UI         │
                 └────────┬─────────┘
                          │
                          ▼
                 ┌──────────────────┐
                 │   Domain Layer   │
                 │   TimerManager   │
                 └────────┬─────────┘
                          │
          ┌───────────────┼────────────────┐
          ▼               ▼                ▼
   Preferences        Foreground       Media Session
    Repository          Service           Control
          │               │                │
          └───────────────┼────────────────┘
                          ▼
                    Android System
```

---

# Tech Stack

### Android

- Kotlin
- Android SDK
- Jetpack Compose
- Material 3
- AndroidX
- Kotlin Coroutines
- DataStore Preferences
- Hilt / Dagger dependency injection

### Android System APIs

- `AlarmManager`
- Foreground Services
- `MediaSessionManager`
- `MediaController`
- `NotificationListenerService`
- `AudioManager`
- Broadcast Receivers
- Quick Settings Tile APIs

---

# Project Structure

```text
Sleepy/
│
├── app/
│   ├── src/main/
│   │   ├── java/com/sleepmusictimer/app/
│   │   │
│   │   ├── data/
│   │   │   └── repository/
│   │   │
│   │   ├── di/
│   │   │
│   │   ├── domain/
│   │   │   ├── TimerManager.kt
│   │   │   └── model/
│   │   │
│   │   ├── presentation/
│   │   │   ├── main/
│   │   │   ├── onboarding/
│   │   │   ├── permissions/
│   │   │   ├── settings/
│   │   │   ├── splash/
│   │   │   └── theme/
│   │   │
│   │   ├── receiver/
│   │   │
│   │   ├── service/
│   │   │   ├── SleepTimerService.kt
│   │   │   └── MediaNotificationListener.kt
│   │   │
│   │   ├── tile/
│   │   │   └── SleepTimerTileService.kt
│   │   │
│   │   └── util/
│   │
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── App icon.png
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
└── gradlew.bat
```

---

# Core Components

## `TimerManager`

The central timer orchestration component.

Responsibilities include:

- starting timers
- pausing timers
- resuming timers
- cancelling timers
- extending timers
- tracking expiration
- scheduling backup alarms
- starting/stopping the foreground service
- triggering audio fade-out
- initiating playback pause
- verifying playback state
- recording timer and pause diagnostics

---

## `SleepTimerService`

A foreground Android service responsible for keeping the active timer operational in the background.

It also handles:

- ongoing timer notification
- dynamic receiver registration
- headphone disconnect events
- battery monitoring
- timer action commands
- expiration state

---

## `MediaNotificationListener`

The media-control layer.

It connects to Android's active media sessions and provides functionality for:

- discovering active media players
- reading playback state
- reading track metadata
- retrieving album artwork
- pausing playback
- play/pause toggle
- next track
- previous track
- playback verification

---

# Permissions

Sleepy uses Android permissions related to its background and media functionality.

Depending on Android version and enabled features, the application may request access for:

- foreground service execution
- notifications
- exact alarms
- battery optimization handling
- boot completion
- notification/media-session access

Some capabilities require explicit user permission through Android system settings.

---

# Requirements

The current project configuration targets:

```text
Minimum SDK : 26
Target SDK  : 36
Compile SDK : 36
Java        : 17
Kotlin      : JVM 17
```

Android Studio with a compatible Android SDK and Gradle environment is recommended.

---

# Build

Clone the repository:

```bash
git clone https://github.com/NithilSuganthan/Sleepy.git
cd Sleepy
```

Build the debug APK on Windows:

```powershell
.\gradlew.bat assembleDebug
```

The resulting APK will be generated under the Gradle build output directory.

For Android Studio, open the repository as a Gradle project and run the `app` configuration on a compatible Android device or emulator.

---

# How It Works

A typical session looks like this:

### 1. Start Music

Play music using your preferred Android media application.

### 2. Open Sleepy

Choose how long you want the music to continue.

### 3. Start the Timer

Sleepy stores the expiry timestamp and starts its background timer mechanisms.

### 4. Go to Sleep

The foreground service keeps the timer operational while the application is not visible.

### 5. Timer Expires

Sleepy begins the configured fade-out process.

### 6. Playback Pause

The application attempts to pause the active media session.

### 7. Verification

Sleepy checks whether playback actually stopped.

### 8. Failsafe

If playback remains active after retries, the application uses its configured fallback behaviour.

---

# Reliability Model

Sleepy deliberately avoids depending on a single timer mechanism.

```text
                 Timer Started
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
     Coroutine    Foreground    AlarmManager
     Countdown      Service       Backup
          │           │           │
          └───────────┼───────────┘
                      ▼
                 Expiration
                      │
                      ▼
              Playback Control
                      │
                      ▼
                Verification
```

This layered approach is intended to make background timer behaviour more resilient to Android lifecycle events.

---

# Privacy

Sleepy is designed around local Android system functionality.

The application interacts with:

- local timer state
- Android media sessions
- Android notifications
- device battery state
- system audio controls

The repository does not require a cloud backend for the core sleep-timer functionality.

Because Sleepy uses Android's notification/media-session APIs, users should understand that granting those permissions allows the application to access the corresponding system-level information required for its functionality.

---

# Current Status

Sleepy is an actively developed Android application.

The repository currently contains:

- native Kotlin implementation
- Jetpack Compose UI
- persistent timer state
- background timer service
- alarm-based recovery
- media-session integration
- playback verification
- audio fade support
- battery protection
- headphone disconnect handling
- reboot recovery
- Quick Settings integration
- diagnostic state tracking

---

# Roadmap

Potential future improvements include:

- richer sleep presets
- more advanced audio-fade curves
- additional media-player compatibility
- improved Android version-specific handling
- sleep statistics
- customizable Quick Settings behaviour
- smarter playback recovery
- improved accessibility
- automated testing across Android versions
- battery and lifecycle optimisation

---

# Disclaimer

Sleepy is a utility application for controlling media playback around a sleep timer.

Its ability to pause or control third-party media applications depends on Android's media-session APIs, the target media player's implementation, device manufacturer behaviour, and granted permissions.

---

# Author

**Nithil Suganthan**

GitHub: [@NithilSuganthan](https://github.com/NithilSuganthan)

Repository: [Sleepy](https://github.com/NithilSuganthan/Sleepy)

---

## License

Add your chosen license here before distributing the project.

# 🛰 LUMINAI Travel

> **Teleport your Android GPS to anywhere on Earth.**
> A futuristic mock location app powered by LUMIN AI.

---

## Features

- 🌐 **LUMIN AI Assistant** — Type any city, landmark, or coordinates; AI resolves and teleports instantly
- 🗺 **OpenStreetMap** — Interactive dark-themed map with smooth marker animation
- 🛰 **Mock GPS Engine** — Foreground service continuously broadcasts fake coordinates
- 📍 **50+ Built-in Landmarks** — Instant offline resolution (Eiffel Tower, Times Square, Taj Mahal…)
- 🌍 **Nominatim Geocoding** — Full address search via OpenStreetMap API
- 📌 **Tap-to-Travel** — Tap anywhere on the map to set GPS there
- ⚡ **Futuristic UI** — Dark `#050A0F` background, `#00E5FF` cyan accents, monospace typography

---

## Quick Start

### 1. Requirements

- Android 6.0+ (API 23+)
- Android Studio Hedgehog or newer
- Physical Android device (emulator has limited mock location support)
- Internet connection for map tiles and geocoding

### 2. Enable Developer Options

1. Go to **Settings → About Phone**
2. Tap **Build Number** 7 times until "You are now a developer" appears

### 3. Set Mock Location App

1. Go to **Settings → Developer Options**
2. Scroll to **Select mock location app**
3. Select **LUMINAI Travel**

### 4. Build & Install

```bash
# Clone / extract the project
cd LUMINAI_Travel_Project

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

Or open in Android Studio → **Run ▶**

---

## Usage

| Action | Result |
|--------|--------|
| Type a city name | LUMIN AI geocodes and sets GPS |
| Type "Eiffel Tower" | Instant offline match, GPS teleports |
| Type coordinates "48.8566, 2.3522" | Direct coordinate teleport |
| Tap map | Sets GPS to tapped location |
| Press 🚀 TRAVEL HERE | Activates mock GPS at selected location |
| Press ⏹ STOP GPS | Deactivates mock GPS |
| Press ⬡ (AI icon) | Toggle LUMIN AI response panel |
| Press ⚙ (gear icon) | Mock location setup instructions |

---

## Architecture

```
com.luminai.travel/
├── MainActivity.kt          UI orchestrator, map, search, AI panel
├── MockLocationService.kt   Foreground service, GPS spoofer (500ms loop)
├── GeocodingService.kt      Nominatim forward + reverse geocoding
└── LuminAIAssistant.kt      Offline AI: landmark DB + input normalization
```

### Key Libraries

| Library | Purpose |
|---------|---------|
| `osmdroid 6.1.17` | OpenStreetMap rendering |
| `okhttp3 4.12.0` | HTTP calls to Nominatim |
| `kotlinx-coroutines` | Async geocoding without blocking UI |
| Material Components | Futuristic input fields, buttons |

---

## How Mock Location Works

```
User input
    ↓
LuminAIAssistant.interpretLocation()
    ↓ (offline) landmark DB match
    ↓ (online)  Nominatim geocode
    ↓
GeocodingResult { lat, lon, displayName }
    ↓
MockLocationService.startMockLocation(lat, lon)
    ↓
LocationManager.addTestProvider(GPS_PROVIDER)
LocationManager.addTestProvider(NETWORK_PROVIDER)
    ↓
Scheduler @ 500ms → pushLocation()
    ↓
LocationManager.setTestProviderLocation(...)
    ↓
All apps on device receive fake GPS ✅
```

---

## Permissions

| Permission | Why |
|------------|-----|
| `ACCESS_FINE_LOCATION` | Required to set test provider |
| `ACCESS_MOCK_LOCATION` | Legacy mock location flag |
| `INTERNET` | OSM tiles + Nominatim geocoding |
| `FOREGROUND_SERVICE_LOCATION` | Keep service alive in background |
| `WAKE_LOCK` | Prevent CPU sleep during mock GPS |

---

## Troubleshooting

**"Mock location not working"**
→ Make sure LUMINAI Travel is selected as mock location app in Developer Options

**"Location set but apps still show real GPS"**
→ Wait 2-3 seconds for the mock loop to override. Some apps cache GPS.

**"Geocoding fails"**
→ Check internet connection. Built-in landmarks work offline.

**"SecurityException in logcat"**
→ App is not set as mock location provider. See Step 3 above.

---

## Based On

Inspired by [FakeTraveler](https://github.com/mcastillof/FakeTraveler) (open source).
Fully rewritten in Kotlin with new architecture, AI assistant, and futuristic UI.

---

## License

MIT License — Free for personal and commercial use.

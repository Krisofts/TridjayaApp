# Tridjaya Elektronik — Android App

Native Android app (Kotlin + Jetpack Compose) for **Tridjaya Elektronik** sales staff: browse
inventory, manage the CRM leads pipeline, track sales KPIs, and generate & share promotional
product flyers. It talks to an existing Rust microservices backend at
`https://tridjayaelektronik.tech/api`.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-24-blue">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-blue">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="UI" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
</p>

> This is the **Jetpack Compose rewrite** of the app (app name *Tridjaya App*, `minSdk 24`). It is a
> separate codebase from the older XML/Fragment "Tridjaya Elektronik" build and is backed by the Rust
> ERP API rather than Firebase.

---

## Features

- **Authentication** — login by NIK or WhatsApp number; JWT session persisted in an **encrypted
  DataStore** (Android Keystore AES-GCM); **proactive + reactive** token refresh (rotating refresh
  token, race-safe); change / forgot / reset password; forced `must_change_password` gate.
- **Home dashboard** — sales KPIs (today / MTD, growth vs. yesterday & last month), monthly target
  with projection, branch & sales rankings. Sections are reorderable and show/hide-able.
- **Inventory** — search, filter (ready-only / region / category / brand), sort, Paging 3 list with
  per-branch stock breakdown, and a product detail screen with a **poster-style flyer generator**
  (WhatsApp / generic share) plus an **installment (cicilan) simulator**.
- **CRM leads** — pipeline board with stage moves, won/lost/reopen, tasks & activities, and
  **offline-first lead creation** (optimistic local write + background sync queue).
- **Global search** — one field searches cached products + leads at once.
- **Settings & theming** — profile, 9 color presets + Material You dynamic color + light/dark, and
  a "Cek Pembaruan" in-app update flow (Firebase Remote Config, optional).
- **Resilient UX** — every network-backed screen shows a consistent **error + "Coba lagi"** state
  (never a blank screen or a stuck spinner) when it fails with no cached data.

## Tech stack

| Area | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Dependency injection | Hilt |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Local persistence | Room (5-hour TTL cache), DataStore (encrypted session) |
| Paging | Paging 3 (inventory list) |
| Async | Kotlin Coroutines + Flow |
| Secure storage | Android Keystore AES-256/GCM (encrypted DataStore) |
| Updates | Firebase Remote Config (optional, gated on `google-services.json`) |
| Build | Gradle (AGP), R8 minify + resource shrinking, Baseline Profile |

## Architecture

MVVM + a repository layer, with a thin domain (use-case) layer. ViewModels expose `StateFlow` UI
state; repositories are the single source of truth and cache into Room, syncing from the API only
when stale. Product identity is the composite key `kode + kodeCabang` (product codes collide across
regions). Screens read from cache first, so the app stays useful offline.

```
data/
  AuthRepository, InventoryRepository, CrmRepository   repositories
  TokenStore + SessionCrypto + SessionSerializer       encrypted-DataStore session
  local/                                               Room entities/DAOs/AppDatabase
  remote/                                              Retrofit APIs + NetworkModule (auth/refresh)
  model/                                               @Serializable DTOs
  pricing/                                             installment (cicilan) calculator
  export/                                              CSV + flyer PNG + share intents
di/                                                    Hilt modules
domain/                                                use cases (auth / home / inventory / leads / …)
ui/
  home/ inventory/ leads/ search/ login/ settings/     feature screens + ViewModels
  navigation/                                          bottom-nav destinations
  theme/                                               design system (ClayCard, ExpressiveComponents, …)
MainActivity.kt                                        root NavHost + keep-tabs-alive container
```

For deeper architecture notes and rationale, see [`CLAUDE.md`](CLAUDE.md).

## Getting started

### Prerequisites

- Android Studio (latest stable) or the Gradle CLI
- JDK 17
- Android SDK with `compileSdk 35`

### Build & run

```bash
git clone https://github.com/Krisofts/TridjayaApp.git
cd TridjayaApp

# Create local.properties with your SDK path (or let Android Studio generate it):
#   sdk.dir=/path/to/Android/Sdk

# Debug build + install on a connected device:
./gradlew installDebug
```

The app builds and runs **without any secret files** — Firebase and release signing are optional
(see below).

### Optional: Firebase (in-app update checks)

Drop your Firebase project's `google-services.json` into `app/`. The Google Services plugin
auto-applies on the next build, and the update system reads these Remote Config keys:
`min_version_code`, `latest_version_code`, `latest_version_name`, `update_url`, `release_notes`.
Without the file, the app still runs and the update check simply stays inert.

### Optional: Release signing

Release builds are signed only if a `keystore.properties` file exists in the project root:

```properties
storeFile=release-keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Without it, `./gradlew assembleRelease` produces an **unsigned** release APK. Remember to bump
`versionCode` in `app/build.gradle.kts` for each release.

## Backend API

Base URL: `https://tridjayaelektronik.tech/api`. The full public endpoint map (auth, inventory,
executive dashboard, finance, CRM, kinerja, notifications, …) is in
[`docs/backend-api-endpoints.md`](docs/backend-api-endpoints.md). The Rust backend lives in a
separate repository.

## Security

The following are **git-ignored and never committed** — add them per machine:

- `release-keystore.jks` and `keystore.properties` (signing key + plaintext passwords)
- `app/google-services.json` (Firebase project config)
- `local.properties`

The encrypted session store is excluded from Android cloud backup / device transfer (its Keystore
key never leaves the device), so a restore can't leak or resurrect tokens.

## Status

Actively developed. No automated test suite or CI pipeline yet — see `CLAUDE.md` for the current
gaps and roadmap.

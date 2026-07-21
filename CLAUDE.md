# Tridjaya Elektronik — Android App Master Plan

Native Android app (Kotlin + Jetpack Compose) for Tridjaya Elektronik's sales staff: browse
inventory, manage CRM leads/prospects, view sales KPIs, and generate/share promotional product
flyers. Talks to an existing Rust microservices backend at `https://tridjaya.com/api`
(separate repo, not part of this project).

Read this file first in any new session. It exists so a future agent doesn't have to
re-derive architecture decisions or repeat mistakes already fixed once.

## Tech stack

- Kotlin, Jetpack Compose (Material3 "Expressive"), no XML layouts
- Hilt for DI (`@HiltViewModel`, constructor injection everywhere)
- Room for local persistence/caching (`data/local/`)
- Retrofit + OkHttp + kotlinx.serialization for networking (`data/remote/`)
- Paging 3 for the Inventory product list
- Encrypted **DataStore** (`TokenStore`, Android Keystore AES-GCM) for JWT tokens + cached user
  profile fields (migrated off the deprecated EncryptedSharedPreferences — see auth section)
- Navigation Compose — one root `NavHost` (login ↔ main) + one nested `NavHost` per bottom-nav
  tab (`HomeNavHost`, `InventoryNavHost`, `LeadsNavHost`)
- minSdk 24, targetSdk/compileSdk 35, Compose BOM 2024.10.01

## Package layout

```
data/
  AuthRepository.kt        auth (login/profile/logout), token refresh race-condition-safe
  InventoryRepository.kt   product/stock sync + paging (Inventory tab only)
  SalesRepository.kt       KPI/target/leaderboard (klasemen) + Home dashboard cache + txn drill-down
  CrmRepository.kt         leads sync/cache + pipeline/CRM actions
  TokenStore.kt            encrypted DataStore (Keystore AES-GCM): tokens, expiry, profile, mustChangePassword
  SessionCrypto.kt         Android Keystore AES-256/GCM encrypt/decrypt for the session blob
  SessionSerializer.kt     DataStore Serializer<PersistedSession> (encrypts via SessionCrypto)
  local/                   Room entities/DAOs/AppDatabase (branch_stock, leads, dashboard cache, sync meta)
  remote/                  Retrofit API interfaces + NetworkModule (OkHttp client, auth interceptor)
  model/                   @Serializable DTOs mirroring backend JSON
  pricing/                 InstallmentCalculator (cicilan/OTR simulator, ported from TE KOTLINT reference)
  export/                  CSV export, flyer PNG export + WhatsApp/generic share intents
di/AppModule.kt            Hilt providers: Room DB, DAOs, TokenStore, repositories
ui/
  home/         Home dashboard (KPI, branch/sales rankings), RankingListScreen ("lihat semua")
  inventory/    Product list (search/filter/sort/paging), ProductDetailScreen (flyer generator)
  leads/        CRM: list/search, add, detail (stage move, won/lost/reopen)
  login/, settings/
  navigation/   AppDestination enum — single source of truth for bottom-nav tabs
  theme/        TridjayaAppTheme, ClayCard, TridjayaBottomNav, TridjayaHeader, custom icons
MainActivity.kt             hosts both NavHosts + the keep-all-tabs-alive bottom nav container
```

## Architecture decisions worth knowing before you touch things

**Region-aware product identity.** The ERP's `kode` (product code) collides across regions —
the same code can be a *different physical product* in a different branch region. Product
identity is always the composite key `kode + kodeCabang`, never `kode` alone. This shows up in
Room primary keys, DAO queries, ViewModel state maps, and nav route args. If you add a new
Inventory feature, key by both fields.

**Cache strategy — uniform 5-hour TTL, Room-backed, no network only.** Inventory
(`branch_stock` table), Home dashboard (`DashboardCacheEntity`, one JSON blob), and Leads
(`LeadEntity`) all sync from the API into Room and are considered fresh for 5 hours
(`SYNC_INTERVAL_MILLIS` / `DASHBOARD_CACHE_TTL_MILLIS` / `LEADS_SYNC_INTERVAL_MILLIS`). Screens
read from Room; a background sync only fires when stale, plus manual pull/refresh buttons.
**Leads are observed reactively**: `LeadDao.observe()/observeAll()` expose Room `Flow`s that
`LeadsListViewModel` collects, so any cache write (create lead, move stage, mark won/lost — all call
`CrmRepository.cacheLead()`) updates the list + KPI strip live with no manual reload. Network sync
still runs on init/refresh; it just writes into the same cache the UI already observes.
This was a deliberate user choice (they were shown a "smarter tiered TTL" option and picked
uniform 5h instead) — don't quietly change it back to tiered without asking.

**Tab switching must not tear down state.** `MainActivity.kt`'s `MainScreen` composes *every
visited tab* once and keeps it alive for the session (visibility-toggled via alpha + a
`blockInputWhen` pointer-input blocker on hidden tabs), instead of disposing/recomposing the
selected tab's NavHost. This was a deliberate fix — a naive `when(selected) { ... }` switch
was previously destroying each tab's ViewModels and forcing a full reload on every tab switch.
Don't revert to that pattern.

**Token refresh is synchronized + proactive.** `NetworkModule.kt` has one `TokenRefresher`
(`synchronized`) shared by two callers: `AuthHeaderInterceptor` refreshes **proactively** when the
access token is within ~1 min of its `expires_in`-derived expiry (so most requests skip the 401
round-trip), and `TokenRefreshAuthenticator` is the **reactive** 401 fallback. Both pass the token
they're dissatisfied with to `refresher.refresh(staleToken)`; if the store already holds a
different token (another thread rotated while we waited on the lock), it's reused instead of
refreshing again. This is critical because the refresh token is single-use/rotating: concurrent
refreshes with the same token would have the losers fail and wipe a session the winner just
renewed. Keep the single-refresher + synchronization — removing it reintroduces random forced
logouts.

**Session storage is an encrypted DataStore, not EncryptedSharedPreferences.** `TokenStore` now
persists the whole session (`PersistedSession`: tokens, access-token expiry, cached profile,
`mustChangePassword`) as **one AES-256/GCM blob** in a typed DataStore; the key is a
non-exportable Android Keystore key (`SessionCrypto`), and `SessionSerializer` encrypts/decrypts on
every read/write (returns the empty default on an undecryptable blob so a Keystore loss after a
restore can't crash startup). Jetpack Security's EncryptedSharedPreferences is deprecated — this
replaces it. **Why the API stayed synchronous:** OkHttp's interceptor/authenticator run on
background threads that can't suspend, so `TokenStore` keeps an in-memory `@Volatile` mirror of the
DataStore that those callers read instantly; writes update the mirror synchronously and persist to
the DataStore async (`scope.launch { dataStore.updateData { cache } }` — writing the *latest*
mirror, not a snapshot, so concurrent persists converge idempotently). `warmUp()` (called from
`TridjayaApplication` on `Dispatchers.IO`) seeds the mirror + runs a **one-time migration** from the
legacy `tridjaya_secure_prefs` EncryptedSharedPreferences store before the splash decides
login-vs-main, so existing users don't get logged out on update. Both the DataStore file
(`datastore/tridjaya_session.pb`) and the legacy prefs are excluded from cloud backup/transfer
(`backup_rules.xml` / `data_extraction_rules.xml`) — same "Keystore key isn't backed up" reasoning.

**Password flows + forced-change gate.** `AuthApi`/`AuthRepository` cover `change-password`
(snake_case body), `forgot-password`, `reset-password` (screens in `ui/login/`; voluntary change is
an inline sub-screen in Settings, forgot/reset are root routes off Login). The backend's
`must_change_password` flag is surfaced reactively via `TokenStore.mustChangePasswordState` →
`SessionViewModel.mustChangePassword`; `MainActivity`'s gate `LaunchedEffect` routes a logged-in
user with the flag set to a **blocking** `ROUTE_CHANGE_PW` (no back, system back swallowed) and
releases to Main once `markPasswordChanged()` clears it. The **required-WhatsApp** gate from
`android-api.md` is deliberately **not** implemented yet (was descoped by the user in this pass) —
`updateProfile` + the field plumbing exist, so it's a small follow-up if wanted.

**Floating pill bottom nav (Rhythm `FloatingNavigationBar`), not Material3 `NavigationBar`.**
`TridjayaBottomNav.kt` reproduces Rhythm's actual home-screen nav: a pill-shaped
`FloatingNavigationBar` at the bottom-start holding the browse tabs (Home + Prospek — selected
tab expands to icon+label, others are icon-only) plus a **separate bold circular search FAB** at
the bottom-end for the Cari tab (always solid `primary`, like Rhythm's persistent search
button). The **Cari tab opens a global search** (`GlobalSearchScreen`, `ui/search/`), NOT the
inventory browse screen — one field searches cached products (`InventoryRepository.searchProducts`)
+ leads (`CrmRepository.cachedLeads`) at once, grouped by type; results deep-link to product/lead
detail, and the full filterable inventory browse is still reachable via "Jelajahi semua barang".
The tab's `InventoryNavHost` root is `SEARCH_ROUTE_ROOT`; the browse list (`INVENTORY_ROUTE_LIST`)
is now a pushed sub-screen with its own back button. `GlobalSearchScreen` mirrors Rhythm's
`UniversalSearchScreen`: a top bar (back + "Cari" + a Tune/filter button that reveals
`Semua/Produk/Prospek` type-filter chips), results filling the top, and the **search field docked
at the bottom above the keyboard** — the floating bottom nav is hidden here (`showBottomNav` is
`false` for the whole Cari tab; a back button returns to Home via `onCloseSearch`).
**`MainActivity` MUST keep `android:windowSoftInputMode="adjustResize"`** (in AndroidManifest) —
without it, the bottom-docked search field double-pads on some devices (window auto-resize *plus*
`imePadding()`) and floats mid-screen. The search Column uses `.imePadding()`; adjustResize makes
that report the keyboard correctly. Wired via `TridjayaFloatingNav(pillItems, searchItem)`, **overlaid** at `BottomCenter`
inside `MainActivity`'s content `Box` (NOT `Scaffold.bottomBar`) so content scrolls *behind* it
like Rhythm — every scrollable tab (Home/Inventory/Leads/RankingList/Settings) adds ~100dp bottom
content clearance so nothing hides permanently. The pill uses `Modifier.weight(1f)` to stretch
full-width up to the FAB (items spread evenly), not wrap-content. The nav **hides on any
sub-screen**: `MainScreen` hoists each tab's nested `NavHostController`, watches its current route
via `currentBackStackEntryAsState`, and an `AnimatedVisibility` (slide down + fade) shows the nav
only when the selected tab is on its root list route (`HOME_ROUTE_DASHBOARD`/`INVENTORY_ROUTE_LIST`/
`LEADS_ROUTE_LIST`) — hidden on pushed details (product/lead/ranking/add) and on Settings, so those
full-screen pages own the frame. Each nested `NavHost` uses Rhythm's sub-screen transition
(`fadeIn(300) + slideInVertically(offsetY = it/4, tween 350 EaseInOutQuart)`, reversed on pop).
This was chosen over Material3 `NavigationBar` **and** over `NavigationSuiteScaffold` at the user's
explicit request (they compared all three) — don't swap it without asking. The Leads screen's own
add FAB is deliberately a smaller tonal `SmallFloatingActionButton` so it reads as secondary,
stacked above the search FAB rather than as a duplicate circle.

**Edge-to-edge is handled in `Theme.kt` via `SideEffect`**, not `enableEdgeToEdge()` in
`MainActivity`. Every screen's own `Scaffold` sets `contentWindowInsets = WindowInsets(0,0,0,0)`
and consumes status-bar/nav-bar insets itself (via `TridjayaHeader` or explicit
`windowInsetsPadding`) — don't let two layers both reserve the same inset or you get a double-padded
gap (this happened twice already, see git history/session logs if it recurs).

**`ClayCard` uses `Surface`, not Material3 `Card`.** Deliberately built with independent
`tonalElevation`/`shadowElevation` (shadow defaults to 0) because `Card`'s bundled shadow forces
a redrawn shadow layer on every visible row during list scroll — multiplied across 15-20 rows
during a fling, this was a measurable scroll-perf cost. Keep list-row usages shadow-free;
only opt into `shadowElevation > 0` for non-scrolling standalone cards if really needed.

**Consistent offline/error UX: `ExpressiveErrorState`.** Every data screen that fetches over the
network must, when it fails **with no cached data to fall back on**, show
`ExpressiveErrorState(message, onRetry)` (`ui/theme/ExpressiveComponents.kt` — cloud-off icon +
"Gagal memuat" + a "Coba lagi" button) wired to the ViewModel's existing reload — **never** a bare
`Text(errorMessage)`, a blank screen, or a stuck spinner. This is applied on Home
(`loadDashboard`), RankingList (`load`, plus an empty-state for zero results), Leads list
(`refresh`), Lead detail (`load`, only when it's a real error vs a genuine "not found"), Product
detail (`load`), and Inventory Paging (inline "Coba lagi" on the sync banner, `pagingItems.retry()`
on append errors, and a full error state when the initial refresh fails with an empty DB). Screens
that read only local Room/cache and stay useful offline (Global search, cached Leads/Inventory
lists, Add-lead's offline queue) deliberately show cached data or an empty state instead — the
error state is specifically for the network-failed-and-nothing-to-show case. Verified live via
airplane-mode (cleared Room DB but kept the session) — each screen showed the error+retry card, and
tapping retry either reloaded or, on an expired session, logged out cleanly to Login.
Use `ExpressiveEmptyState` for "no results" and `ExpressiveErrorState` for "load failed".

## Product flyer generation (Inventory → Product Detail)

`ProductDetailScreen.kt` renders a poster-styled "flyer" (`ProductFlyer` composable) matching a
specific reference design (blue/white poster with promo price, tenor/cicilan grid, frosted-glass
price cards) — colors are intentionally hardcoded in a `FlyerColors` object, not
`MaterialTheme`-driven, so the shared image looks identical regardless of the user's device theme.

Capture works via `View.draw(Canvas)` on the root view + cropping to the flyer's
`onGloballyPositioned` bounds (no newer Compose `GraphicsLayer` capture API — wasn't confirmed
available in this project's resolved Compose version, so don't assume it exists without checking
`ui-graphics-android`'s actual jar contents first). Three actions: "Buat Gambar" (generate +
generic Android share sheet), "Kirim ke WA" (generate + `Intent` targeted at `com.whatsapp`,
falls back to generic share if not installed), "Salin" (copies a formatted "Struktur Kredit" text
block to clipboard).

**Product images are not implemented.** `FlyerImagePlaceholder` is a static placeholder box —
the backend/local data model has no product photo URL field at all. Wiring in real photos (e.g.
via Coil `AsyncImage`) needs a backend field first; don't assume one exists.

## Installment/cicilan simulator (`data/pricing/InstallmentCalculator.kt`)

Ported line-for-line from a separate reference project (`C:\laragon\www\TE KOTLINT`, an older
Fragment/XML version of similar functionality) — OTR/DP/tenor math is copied exactly, including
its quirks (two different calculation paths depending on product category — "ADV" categories
like Sepeda Listrik/Laptop/Handphone/TV use one bracket-lookup table, everything else derives a
final OTR via a two-step 12-month lookup). Price-bracket lookup tables are bundled CSVs in
`app/src/main/assets/pricing/`. If the reference project's business logic ever changes, this
needs to be re-ported by hand — there's no shared library between the two projects.

## Absen (Kehadiran) — mobile, WIRED ke backend nyata

Fitur absen **check-in + selfie + lokasi (geofence)** di `ui/attendance/`, tersambung ke backend
**`kinerja-service` modul absensi** (`tridjaya` repo, branch `feat/absensi-karyawan`,
`src/absensi.rs` + `absensi_upload.rs`) via gateway `/api/absensi/*`. **Bukan dummy** — awalnya
dibangun dummy (backend belum ada), lalu user membuat backend-nya dan modul di-refactor ke wiring
nyata. Kontrak lengkap: `docs/absen-api-contract.md`.

- **Alur punch**: ambil GPS + selfie → **upload selfie dulu** (`POST /api/absensi/upload-photo`
  multipart field `file`, ≤5 MB) → dapat URL relatif → `POST /api/absensi/check-in|check-out`
  `{lat,lng,photoUrl}`. Server menghitung jarak geofence (Haversine), telat/pulang-cepat, dan
  `status` (`valid` bila dalam radius, `pending_review` bila di luar → butuh approve reviewer).
  App **tidak** menghitung geofence sendiri (config cabang admin-only) — verdict tampil dari record.
- **Layer app**: `data/model/AttendanceModels.kt` (`AbsensiRecordDto` 1:1 camelCase),
  `data/remote/AbsensiApi.kt` (Retrofit, `today`/`list`/`check-in`/`check-out`/`upload-photo`),
  `data/AbsensiRepository.kt` (no cache — absen harus real-time), `AttendanceViewModel`
  (today+history paralel, kompres selfie ≤2 MB/1600px + EXIF, punch), `AttendanceScreen`.
  DI: `NetworkModule.createAbsensiApi` + `AppModule.provideAbsensiApi`.
- **Selfie**: full-res `ActivityResultContracts.TakePicture()` + FileProvider (cache-path `absensi/`
  di `file_paths.xml`, authority `${applicationId}.fileprovider`). **Tanpa izin `CAMERA`** (delegasi
  ke app kamera; kalau CAMERA dideklarasi wajib request runtime — jangan tambah tanpa alasan).
- **Lokasi**: framework `LocationManager` (`ui/attendance/LocationProvider.kt`, suspend, tanpa
  play-services) — izin `ACCESS_FINE/COARSE_LOCATION` di manifest + request runtime.
- **Menu**: tile "Absen" (ikon Fingerprint, teal) paling depan di `QuickAccessRow` (Home), route
  nested `home_absen` di `HomeNavHost`. Role gate di **backend** (STAFF_ROLES self-service); app
  belum menyembunyikan tile per-role (semua user login lihat menu, backend menolak yang tak berhak).
- **Prasyarat data**: tiap cabang perlu di-set geofence via `PUT /api/absensi/config/{cabangId}`
  (admin). Tanpa config → jarak null, absen tak pernah di-flag telat/luar-area (fail-open).

## Signing / release builds

- `release-keystore.jks` + `keystore.properties` (git-ignored, **not** committed) hold the real
  release signing key. **Back these up outside the repo** — if lost, this app can never be
  updated again under the same signature on a real device/Play Store.
- `app/build.gradle.kts` reads `keystore.properties` at build time and wires
  `signingConfigs.release` only if the file exists — a machine without `keystore.properties`
  still builds an *unsigned* release APK (won't `adb install`), which is intentional (never
  hardcode credentials in the build script).
- `isMinifyEnabled = true` + `isShrinkResources = true` for release, with proguard rules for
  kotlinx.serialization, Retrofit, and Google Tink (`security-crypto`'s transitive dep — needs
  `-dontwarn com.google.errorprone.annotations.**` or R8 fails on missing classes).
- **R8 is legitimately slow in this dev environment** (observed 5-15+ min for `minifyReleaseWithR8`
  alone) — this is environment-specific, not a sign the build is stuck. Don't kill it prematurely;
  check CPU usage via `Get-Process java` if in doubt (climbing CPU = still working).
- A signed release APK and a debug APK have **different signatures** — installing one over the
  other on the same device requires `adb uninstall` first (wipes local app data/login). Always
  ask before doing this; it's destructive to the user's test session.
- Observed R8 wall-clock on a full release build here: **~44 min** once (cold-ish). Build via
  `run_in_background`, watch the Gradle daemon's `java.exe` CPU/RAM climbing to confirm progress,
  and only trust `BUILD SUCCESSFUL` in the output — `--console=plain` buffers, so an empty output
  file mid-build is normal, not a hang.
- **`versionCode` must be bumped** in `app/build.gradle.kts` for every release (currently `1`) —
  the update system's Remote Config comparison and Play/side-load upgrades both depend on it.

## Release hardening (production-readiness pass)

Done in a dedicated "is this ready to ship?" pass; don't regress these:

- **Launcher icon works on API 24/25.** minSdk is 24 but the icon was previously *only*
  `mipmap-anydpi-v26/ic_launcher.xml` (adaptive icons are API 26+), so on Android 7.0/7.1 the
  launcher had no raster to fall back to. Fixed by generating PNG mipmaps for **all five
  densities** (`mipmap-mdpi…xxxhdpi`, both `ic_launcher` + `ic_launcher_round`) from the exact
  `ic_launcher_foreground.xml` geometry (white building glyph on `#0D47A1`), plus an
  `ic_launcher_round.xml` adaptive icon for v26+ and `android:roundIcon` in the manifest. The PNGs
  were rasterized with a one-off Pillow script (no Android Studio Image Asset tool in this env) —
  if the icon design changes, regenerate all 10 PNGs, don't hand-edit them.
- **Encrypted prefs excluded from backup/transfer.** `res/xml/backup_rules.xml` (`fullBackupContent`,
  API<31) and `res/xml/data_extraction_rules.xml` (`dataExtractionRules`, API 31+) both `<exclude>`
  the `tridjaya_secure_prefs` sharedpref. Reason: its AES key lives in the Android Keystore, which
  is never backed up — restoring the encrypted blob onto a new device would be undecryptable and
  can crash on first read. User just re-logs in. Keep this exclusion if you add more secure prefs.
- **Dev artifacts removed from the release surface.** The stale LAN IP `10.132.14.53` was dropped
  from `network_security_config.xml` (only emulator loopback `10.0.2.2`/`localhost` keep cleartext;
  prod is HTTPS-only), and the root `serve.log` was deleted.
- **Two loading-spinner hangs fixed.** `ProductDetailViewModel` and `GlobalSearchViewModel` wrapped
  their `viewModelScope.launch` load in try/catch so a thrown read never leaves `isLoading`/
  `isSearching` stuck forever (they fall through to the existing "not found"/empty states;
  `GlobalSearch` rethrows `CancellationException` so a superseding search still cancels cleanly).

## Build & deploy workflow (this dev environment specifically)

- No Android Studio GUI available in this environment — everything via Gradle CLI.
- `gradlew` invoked directly via
  `"C:\Program Files\Android\Android Studio\jbr\bin\java.exe" -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain <task>`
  (bypasses `gradlew.bat`/shell script quoting issues), with `JAVA_HOME` pointed at Android
  Studio's bundled JBR.
- Builds are slow (1-3+ min for debug, much longer for release/R8) — always run via
  `run_in_background: true` and poll with `ScheduleWakeup`, don't block synchronously.
- `adb.exe` lives at `C:\Users\adm_c\AppData\Local\Android\Sdk\platform-tools\adb.exe` (not on
  PATH in the sandboxed shell — use the full path).
- Test device: physical phone, serial `30531702210004R`. `adb devices -l` sometimes shows it
  disconnected if the USB cable/authorization dropped — ask the user to reconnect rather than
  assuming the device is gone.

## Performance hardening (perf/UI/responsiveness pass)

Three fixes from a dedicated performance audit — don't regress these:

- **Flyer capture is off the main thread.** `ProductDetailScreen.kt`'s `captureBitmap()` now uses
  `PixelCopy` (API 24+) to copy the already-rendered window pixels on the render thread and deliver
  the result via callback, instead of the old `View.draw(Canvas)` path that allocated a full-screen
  `ARGB_8888` bitmap and rasterised the whole view tree synchronously on the UI thread (a visible
  freeze on tap). A `legacyCapture()` software fallback remains for the rare case where no host
  Window is reachable. It's a `suspend` fn — callers already invoke it from a coroutine.
- **Home dashboard fires its 4 endpoints concurrently.** `InventoryRepository.homeDashboard()`
  wraps the KPI / monthly-target / branch-performance / sales-performance calls in
  `coroutineScope { async { … } }` so cold-load latency is the slowest single round-trip, not the
  sum of four. Keep them independent — don't serialise them back.
- **Baseline Profile.** A `:baselineprofile` module (`com.android.test` +
  `androidx.baselineprofile` plugin, `useConnectedDevices = true`) generates an AOT-compilation
  profile from the app's startup path; `androidx.profileinstaller` (added to `:app`) installs it on
  first run, removing cold-start / first-scroll JIT jank. The committed profile lives at
  `app/src/release/generated/baselineProfiles/`. **Regenerate** after meaningful startup/UI changes:
  `:app:generateBaselineProfile` (needs the physical device connected + unlocked). Gotchas seen
  once: (1) the cold generation build is very slow in this env (~2h the first time, ~8min cached);
  (2) `INSTALL_FAILED_UPDATE_INCOMPATIBLE` if a differently-signed build of the app is already on
  the device — the generator's `nonMinifiedRelease` is release-signed, so `adb uninstall` any debug
  build first (UTP also clears it on teardown). The deeper journeys (product-list scroll, CRM) sit
  behind the login gate and aren't automated, so the profile is startup-focused by design.

**Deferred from the same audit (not done — check with the user before starting):** no
`WindowSizeClass`/adaptive layouts (single-column `fillMaxWidth` everywhere — fine for phones, not
optimised for tablet/landscape/foldable); the shared flyer is captured at device width, so the
exported image's aspect ratio varies by screen (a fixed render width would make it consistent).

## App update system + Firebase (Remote Config)

Force-update / optional-update / "Cek Pembaruan" (Settings) driven by **Firebase Remote Config**:

- `UpdateManager` (`data/update/`) reads Remote Config keys `min_version_code`, `latest_version_code`,
  `latest_version_name`, `update_url`, `release_notes` and compares to `BuildConfig.VERSION_CODE`:
  below `min` → **force** (blocking, non-dismissible `UpdateDialog`, back/outside ignored); below
  `latest` → optional dismissible prompt; else up-to-date. Awaits Play-services `Task`s via a tiny
  local `awaitResult()` (no `kotlinx-coroutines-play-services` dep).
- Startup gate: `TridjayaNavHost` (MainActivity) hosts `UpdateViewModel`, checks once, overlays the
  force dialog over the whole app (incl. login). Settings → **Aplikasi**: shows `BuildConfig`
  version + a "Cek Pembaruan" item (manual check → dialog or "sudah terbaru" toast).
- **Firebase is optional at build time — gated on `app/google-services.json` like the release
  keystore.** The `com.google.gms.google-services` plugin is applied only if that file exists
  (`app/build.gradle.kts` tail); `firebase-bom` + `firebase-config-ktx` are always present but inert
  without a default `FirebaseApp` — `UpdateManager` checks `FirebaseApp.getApps().isEmpty()` and
  returns `Unknown` (never forces). So the app builds & runs today; force-update stays off.
- **To activate:** (1) drop your Firebase project's `google-services.json` into `app/` (plugin
  auto-applies on next build), (2) set the 5 Remote Config keys in the Firebase console. No code
  change needed. Bump `versionCode` in `app/build.gradle.kts` for each release so the comparison works.

## What's implemented

- Login (NIK/WhatsApp + password), JWT session in encrypted DataStore, proactive + reactive
  auto-refresh, forced `must_change_password` gate, and change/forgot/reset-password flows
- Home: greeting card (with mascot image + wave animation), KPI summary (today/MTD + growth
  badges vs yesterday/last month), branch + sales rankings (top 5 + "lihat semua"). Dashboard
  sections (KPI / Target / Ranking Cabang / Ranking Sales) are **user-reorderable + show/hide**
  via a "Tune" button → `HomeCustomizeSheet` (up/down arrows, not drag). Order+visibility persist
  in plain (non-encrypted) SharedPreferences via `HomeLayoutPreferences` (Hilt constructor-injected).
  The greeting card is fixed at the top and not customizable, matching Rhythm's welcome section.
- Inventory: search (Material3 `SearchBar`), filter chips (ready-only, region, category, brand),
  sort, Paging3 list with expandable per-branch stock breakdown, product detail with flyer
  generator + WhatsApp share + installment simulator
- CRM/Leads: list with search + summary stats, add lead, detail screen (WhatsApp chat deep link,
  pipeline stage picker, won/lost/reopen actions)
- Settings: profile display, logout
- All three tabs' data is Room-cached with a uniform 5-hour TTL and survives tab switches

## Official Android/Material guideline compliance

Audited against developer.android.com guidance (architecture was already largely compliant from
earlier work — Hilt DI, Repository pattern, StateFlow-exposed ViewModels, Room caching, Paging3,
edge-to-edge, R8/shrinking, synchronized token refresh). This pass covered the remaining gaps:

- **Predictive back gesture** (Android 13+ guideline): `android:enableOnBackInvokedCallback="true"`
  set in `AndroidManifest.xml`. Navigation Compose 2.8.4 wires into the system back dispatcher
  automatically once this flag is on — no extra `BackHandler` code needed for the standard
  push/pop nav flows already in use.
- **Accessibility touch targets**: `TridjayaBottomNav.kt`'s `PillNavItem` now has an explicit
  `.heightIn(min = 48.dp)` on the clickable region, guaranteeing Material's minimum touch target
  regardless of font-scale settings (previously relied on content-wrap height, which happened to
  clear 48dp at default scale but wasn't guaranteed).
- **Accessibility content descriptions**: audited every `contentDescription = null` usage — all
  current instances are on decorative icons sitting directly next to a text label (e.g. the Star
  icon beside "Ranking Cabang", the Call icon beside "Chat WhatsApp" button text). Per Material's
  own accessibility guidance, `null` is *correct* there — a real description would cause
  screen readers to double-announce the same information. No bugs found.

**Deliberately not applied — with reasoning, don't "fix" these without checking with the user first:**

- ~~Dynamic color deliberately not applied~~ — **superseded** (user requested full theming):
  `TridjayaAppTheme(themeState)` renders the chosen preset (`colorSchemeFor` in `ThemeSchemes.kt`
  — 9 presets: the **default is now `Biru Tridjaya`** — the flyer's brand blue `#1E63E9`, built by
  `blueDefaultScheme()` in `ThemeSchemes.kt` (blue primary/secondary/tertiary triad **plus**
  softly blue-tinted neutral roles — background `#F6F8FF`, container `#EAEEF6` — unlike the other
  presets which share the M3 neutrals; this replaced the old purple `#6750A4` default). Plus
  Lavender/Rose/Warm/Amber/Forest/Mint/Cool/Ocean) OR Material
  You `dynamicLight/DarkColorScheme` when enabled on Android 12+, with dark mode system/light/dark.
  Choices persist in `ThemePreferences` (`data/`, Hilt singleton + StateFlow) which
  `MainActivity.setContent` observes so the whole app recolours live; edited from Settings → Tema
  (`ThemeSettingsScreen`). Flyer stays theme-independent (hardcoded `FlyerColors`), unaffected.
  Icons app-wide use `Icons.Rounded.*` (rounded variants, Rhythm-style); primary interactions fire a
  light `CONTEXT_CLICK` haptic via `rememberHapticClick`; `ExpressiveShapes` adds squircle/asymmetric
  tokens.

**App theme = Rhythm's default M3 theme.** The color palette (`Color.kt` — seed `#6750A4` M3
baseline purple, rosy-pink tertiary, full surface-container elevation set), type scale (`Type.kt`),
and shape scale (`Shape.kt`, 8/12/16/24/32 dp) were ported *exactly* from the Rhythm reference app
(github.com/cromaguy/Rhythm) at the user's explicit request. This deliberately replaced the earlier
custom violet (`#5C4AD5` + amber tertiary) branding — that decision is superseded, don't restore it.
Only the theme's *visual tokens* were ported, NOT Rhythm's theming engine (album-art dynamic color,
downloadable "Geom" font, 12 switchable preset schemes, festive overlays) — those are music-app
machinery and out of scope. If the user later wants the switchable presets or the Geom font, they'd
need to be wired in fresh.
- **String resource extraction**: the whole UI hardcodes Indonesian strings directly in `Text(...)`
  calls rather than `stringResource(R.string.xxx)` (`strings.xml` only has `app_name`). This is a
  real localization/testability gap per official guidance, but migrating 30+ call sites across
  every screen is a large mechanical refactor with real regression risk (easy to typo a key or
  break a format-string argument) — out of scope for a guideline *pass*, worth a dedicated task.
- **Gradle version catalog** (`libs.versions.toml`): dependency versions are still inline in
  `app/build.gradle.kts` rather than centralized in a version catalog, which is the current
  official Gradle/AGP recommendation. Low runtime impact, pure tooling/maintainability — worth
  doing but isn't urgent, and touching every dependency line in one pass is unnecessary risk for
  a build that's currently working.

## Known gaps / natural next steps

- No product photos (see flyer section above) — needs a backend image URL field first
- **No automated tests exist** (`app/src/test/` and `app/src/androidTest/` don't even have the
  default template files) — the single highest-value next investment for this project's health
- No CI/CD pipeline — builds and releases are manual
- Debug builds have no signing story beyond the Android SDK default debug key; only one release
  keystore exists and it's local-only (not backed up anywhere but the user's own storage)
- `arr.csv` is bundled as a pricing asset in the TE KOTLINT reference but was confirmed unused in
  its actual calculation logic — not ported here; if a 5th product-category bracket table is
  ever needed, check the reference project's newest logic first, don't assume `arr.csv`'s old
  intent is still correct
- **Offline create (add lead) is supported** via an optimistic local-first write + sync queue:
  `CrmRepository.createLead()` inserts the lead into Room immediately (temp **negative** `id`,
  `LeadEntity.pendingSync = true`) so it shows in the list at once marked **"Antre"** (amber cloud
  badge in `LeadCard`), then `appScope.launch { syncPendingLeads() }` pushes it. `syncPendingLeads()`
  (Mutex-guarded queue) POSTs each pending lead oldest-first and, on success, **replaces** the temp
  row with the authoritative server row; failures stay pending and are retried on create / manual
  refresh / list-VM init (`GetLeadsUseCase.syncPending()`). `syncLeads()` flushes pending first and
  re-appends any still-pending rows after `replaceAll` so a refresh never drops an unsynced lead.
  `@AppScope CoroutineScope` (in `AppModule`) keeps the push alive past the Add-Lead screen. DB
  bumped to v5 for the `pendingSync` column (destructive migration — cache re-syncs).
  **Still online-only:** move-stage / mark won/lost (they act on a server `id`, so a pending lead
  can't be mutated until it syncs).
- No string resources (see guideline section above) and no Gradle version catalog — both real
  gaps, both deliberately deferred rather than rushed

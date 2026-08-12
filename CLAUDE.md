# Tridjaya Elektronik — Android App Master Plan

Native Android app (Kotlin + Jetpack Compose) untuk staf lapangan Tridjaya Elektronik. Awalnya
hanya "browse inventory + CRM + KPI + flyer", kini juga alat kerja harian operasional: absen,
raport harian, alur SPK → surat jalan → serah terima → PDI → kasir, stok opname per serial,
indent, mutasi, payroll, dan Pusat Notifikasi. Talks to an existing Rust microservices backend at
`https://tridjaya.com/api` (separate repo, not part of this project).

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
  tab (`ActivityNavHost` — shared route table, mounted twice with a different `startDestination`
  for the Activity and Operasional tabs; `InventoryNavHost`, `LeadsNavHost`)
- minSdk 24, targetSdk/compileSdk 35, Compose BOM 2024.10.01

## Package layout

Daftar di bawah tidak lengkap — ia menyebut yang perlu konteks. `ls` dulu sebelum menyimpulkan
sebuah modul belum ada; app ini sudah jauh lebih luas dari "inventory + CRM + KPI" di paragraf
pembuka (ada alur SPK/pengiriman/PDI, opname, indent, mutasi, payroll, notifikasi, dll).

```
data/
  AuthRepository.kt        auth (login/profile/logout), token refresh race-condition-safe
  InventoryRepository.kt   product/stock sync + paging (Inventory tab only)
  SalesRepository.kt       KPI/target/leaderboard (klasemen) + Home dashboard cache + txn drill-down
  CrmRepository.kt         leads sync/cache + pipeline/CRM actions
  DeliveryFlowRepository.kt  SPK → surat jalan → serah terima → PDI → kasir
  OpnameRepository.kt      stok opname per unit/serial + antrean offline
  RaportRepository.kt      raport harian / Input Aktivitas (BETA) — parseError utamakan `errors[0]`
  NotificationsRepository.kt  Pusat Notifikasi (+ FCM deep-link)
  Indent/Mutasi/Deadstock/Payroll/ErpPriceChanges/SerialInput/Off/Device Repository.kt
  SpkTodayCounter.kt       hitungan SPK hari ini untuk kartu Activity
  ProductImageUrl.kt       resolusi field ERP `Gambar` → URL gambar (dipakai flyer, list, search)
  TokenStore.kt            encrypted DataStore (Keystore AES-GCM): tokens, expiry, profile, mustChangePassword
  SessionCrypto.kt         Android Keystore AES-256/GCM encrypt/decrypt for the session blob
  SessionSerializer.kt     DataStore Serializer<PersistedSession> (encrypts via SessionCrypto)
  ThemePreferences.kt / SearchHistoryPreferences.kt   plain SharedPreferences (bukan terenkripsi)
  local/                   Room entities/DAOs/AppDatabase (branch_stock, leads, dashboard cache,
                           opname_unit, sync meta) — **version 14**
  remote/                  Retrofit API interfaces + NetworkModule (OkHttp client, auth interceptor)
  model/                   @Serializable DTOs mirroring backend JSON
  pricing/                 InstallmentCalculator (cicilan/OTR simulator, ported from TE KOTLINT reference)
  export/                  CSV export, flyer PNG export + WhatsApp/generic share intents
domain/                    use case murni + logika teruji (auth, home, indent, inventory, leads,
                           sales/KlasemenStandings, search) — target utama unit test
di/AppModule.kt            Hilt providers: Room DB, DAOs, TokenStore, repositories
ui/
  activity/     Activity — layar pertama app (tugas harian + antrian ber-gate), ActivityNavHost
                (tabel route dipakai juga oleh tab Operasional, lihat catatan arsitektur di bawah),
                ActivityRegistry/ActivityPlan (gating), PanduanAlurScreen (alur + direktori petugas)
  home/         Dashboard lama (KPI, branch/sales rankings) — kini tab kedua "Operasional";
                QuickAccessMenus.kt = grid Akses Cepat ber-gate (pola sama ActivityRegistry)
  deliveryflow/ SpkHub + layar lapangan (surat jalan, serah terima, PDI, kasir), BranchRegions
  opname/, serials/, indent/, mutasi/, deadstock/, priceerp/, payroll/, notifications/
  raport/       Input Aktivitas (BETA) — lihat "What's implemented"
  inventory/    Product list (search/filter/sort/paging), ProductDetailScreen (flyer generator)
  leads/        CRM: list/search, add, detail (stage move, won/lost/reopen)
  attendance/, search/, sales/, security/, session/, splash/, login/, settings/, update/
  navigation/   AppDestination enum — single source of truth for bottom-nav tabs
  theme/        TridjayaAppTheme, ClayCard, TridjayaBottomNav, TridjayaHeader, RupiahInput, custom icons
MainActivity.kt             hosts every tab's NavHost + the keep-all-tabs-alive bottom nav container
```

## Architecture decisions worth knowing before you touch things

**`java.time` DILARANG di `app/src/main`.** minSdk 24, dan `coreLibraryDesugaring` TIDAK
diaktifkan (cek sendiri: `grep -r desugar` di semua `*.kts` — nihil), sedangkan `java.time`
baru ada di API 26. Kompilasi tetap hijau; yang pecah adalah HP Android 7.0/7.1 di lapangan,
dengan `NoClassDefFoundError` — turunan `Error`, BUKAN `Exception`, jadi gejalanya berbeda-beda
tergantung penangkap terdekat: `runCatching` (menangkap `Throwable`) menelannya jadi **nol
senyap selamanya**, sementara `catch (e: Exception)` tidak menangkapnya sama sekali dan
**app-nya tutup**. Sudah menggigit dua kali dalam sehari (371d0f5 `isGantung` → senyap;
`InventoryRepository.findInTransitHint` → crash saat hasil pencarian kosong). Pakai
`SimpleDateFormat`/`Calendar`, dan pakai ULANG helper yang sudah ada alih-alih menulis util
tanggal ke-sekian: `parseIsoUtcMillis` (`data/model/NotificationModels.kt`) untuk parse ISO,
`KlasemenStandings.todayIso()`/`shiftDays()` (`domain/sales/`) untuk `yyyy-MM-dd` + geser hari.

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

**Migrasi Room ditulis eksplisit begitu tabel memegang data yang belum tersinkron.** `AppDatabase`
kini **version 14** (`branch_stock`, `leads`, dashboard cache, `opname_unit`, sync meta). Bump-bump
awal mengandalkan `fallbackToDestructiveMigration()` dan itu aman selama isi tabel cuma cache yang
bisa di-fetch ulang. Sudah tidak aman lagi: `opname_unit` dan lead `pendingSync` menyimpan **hasil
kerja lapangan yang belum sampai server**, jadi migrasi destruktif = data user hilang diam-diam.
Karena itu `AppModule.kt` (bukan `AppDatabase.kt`) mendaftarkan `MIGRATION_11_12` dan
`MIGRATION_13_14` lewat `.addMigrations(...)`. **Jebakannya:** `.fallbackToDestructiveMigration()`
masih terpasang sebagai jaring pengaman, jadi menaikkan `version` **tanpa** menulis `Migration`
tetap kompilasi hijau dan tetap menghapus tabel di HP user tanpa peringatan (itulah yang terjadi di
12→13). Tiap bump: tanyakan "tabel ini bisa hilang tanpa rugi?" — kalau tidak, tulis `Migration`
sungguhan dan daftarkan.

**Tab switching must not tear down state.** `MainActivity.kt`'s `MainScreen` composes *every
visited tab* once and keeps it alive for the session (visibility-toggled via alpha + a
`blockInputWhen` pointer-input blocker on hidden tabs), instead of disposing/recomposing the
selected tab's NavHost. This was a deliberate fix — a naive `when(selected) { ... }` switch
was previously destroying each tab's ViewModels and forcing a full reload on every tab switch.
Don't revert to that pattern.

**Layar pertama app = `ui/activity/` (Activity), bukan dashboard lama.** Sejak redesain
2026-07-28 (spec `docs/superpowers/specs/2026-07-28-mobile-activity-home-redesign-design.md`
di repo **tridjaya** — backend, bukan repo ini —, branch `feat/activity-home-redesign` di sini),
tab pertama menjawab "hari ini aku harus
ngapain?": tugas harian (absen, prospek), antrian milik role user (PDI/kasir/surat jalan/
approval), dan pintasan "Buat Baru" (`ActivityScreen.kt` + `ActivityViewModel.kt`). Dashboard
lama (KPI/Target/Ranking) pindah utuh ke tab kedua "Operasional" — **satu tabel route**
(`ActivityNavHost.kt`, route anak `home_*` tak berubah) dipakai KEDUA tab lewat parameter
`startDestination` (`ACTIVITY_ROUTE_ROOT` vs `HOME_ROUTE_DASHBOARD`), masing-masing dengan
`NavHostController` sendiri, supaya deep-link push FCM yang sudah ada tetap jalan tanpa
disentuh. Siapa-melihat-apa di Activity diatur **registri ber-gate** `ActivityRegistry.kt`
(`ACTIVITY_ITEMS`, pola sama `ui/home/QuickAccessMenus.kt`) — setiap item WAJIB menyatakan
`capability` (kunci `GET /api/me/capabilities`) + `allowedRoles` cadangan offline +
`backendGuard` (rujukan guard backend asli). `navKey` di registri diterjemahkan jadi route lewat
`routeForNavKey` (fungsi murni, diuji `ActivityNavHostRouteTest`) — kontrak stringly-typed tanpa
pemeriksa kompiler, jangan menambah item baru tanpa menambah kasusnya di sana juga.

**Alur SPK = SATU pencatatan per SPK, kerja fisik tetap per unit.** Pipeline
backend memecah SPK banyak barang jadi satu baris `delivery_jobs` per unit
fisik — itu benar untuk PDI/serial/serah terima, dan baris per unit itulah yang
menghitung statistik kiriman. Tapi sejak 2026-08-05/06 (`b6cbb132`, `b68e2792`,
`c0ee01ac` di repo **tridjaya**) hampir semua endpoint tahap **FAN-OUT se-SPK**:
konfirmasi kasir, klaim PDI (POST+DELETE), surat jalan, penugasan driver,
`dispatch`, `deliver`, dan approve/reject diskon — satu panggilan menyelesaikan
seluruh unit sebatch. Di GS, SPK banyak barang memang SATU transaksi satu nomor.

Konsekuensi yang mengikat app:
- **Jangan pernah memanggil endpoint tahap dalam loop per unit.** Panggilan
  ke-2 dst dijawab 400 "sudah tidak di tahap ini" — pekerjaannya sudah selesai
  di panggilan pertama, tapi layarnya membacanya sebagai kegagalan. Antrian
  (`DeliveryQueueScreen`) mengelompokkan unit lewat `groupJobsBySpk`
  (`SpkBatch.kt`, cerminan `batch_prefix` backend + `utils/spkBatch.ts` web).
- **Tiga bentuk antrian, sengaja berbeda:**
  (a) **Kasir** (`pending_spk`) = SATU kartu per SPK (`SpkRingkasCard`), ketuk
  membuka detail; tombol konfirmasinya HANYA di detail. Kasir menyalin satu
  penjualan ke GS sebagai satu transaksi satu nomor — N baris untuk satu
  penjualan membuatnya mengira ada N pekerjaan.
  (b) **PDI & surat jalan** = baris per unit + header grup + tombol di kepala
  grup; kerja fisiknya memang per unit (serial, checklist), keputusannya per
  SPK.
  (c) **Manifest driver** (`reorderable`) = daftar RATA per unit tanpa grup —
  `POST /delivery/driver/reorder` mengurutkan id unit dan panah naik/turun
  bekerja atas indeks daftar itu.
- **Layar detail memuat saudara se-SPK lewat `loadBatchUnits`**, TAPI hanya
  untuk `pending_spk` (satu-satunya tahap yang isiannya butuh daftar itu:
  `units[]` menuntut nominal DP tiap unit COD `dp` sebatch). Sumbernya antrian
  kasir itu sendiri (`GET /delivery?status=pending_spk`) sehingga himpunannya
  sama persis dengan `siblings` yang divalidasi `confirm_spk`. Fail-soft: gagal
  = jatuh balik ke satu unit. Kalau nanti tahap lain butuh daftar saudara,
  perluas fungsi ini — jangan menebak dari `state.items` (isinya antrian mana
  pun yang terakhir dibuka).
- **Ambang barang besar datang dari server**, `GET /delivery/context` field
  `barangBesarThreshold`. Barang besar tetap PDI per unit (checklist + no.
  rangka); barang kecil tuntas sekali klik lewat `POST /delivery/{id}/pdi-kecil`.
  `isBarangBesar` FAIL-CLOSED — harga/ambang tak diketahui = besar, jadi server
  lama otomatis kembali ke perilaku per unit. **Jangan hardcode 1.500.000.**
- **Picker serial SPK MEMPERINGATKAN unit repair/retur, tidak memblokirnya**
  (keputusan user 2026-08-09). `SerialRegistryRow` kini membawa `kondisi` +
  `kondisiKeterangan`; `SpkItemCard` menampilkan peringatan merah saat unit
  terpilih bermasalah, dan `serialUntukDisarankan()` menaikkan unit sehat ke
  atas SEBELUM daftarnya dipotong lima — tanpa itu satu batch unit retur bisa
  mengisi seluruh saran dan menyembunyikan unit layak di posisi keenam. Memblokir
  sengaja TIDAK dipilih: registry bisa telat diperbarui, dan unit repair yang
  sudah selesai diperbaiki masih bertanda repair. Server tidak menegakkan apa pun
  soal ini — murni lapisan klien.
- **Diskon ditolak TIDAK lagi melepas unit.** SPK kembali ke sales dan unitnya
  tetap `pending_discount` sampai dia memilih: revisi diskon (lewat web —
  `POST /discount-requests` tak pernah dipanggil dari app), sunting isi SPK
  (`bolehSuntingSpk`: admin, ATAU sales PEMILIK saat `pending_discount`), atau
  `POST /discount-requests/{id}/lanjut-tanpa-diskon`. Tanpa jalan keluar itu SPK
  mandek permanen dari sisi app **tanpa satu pun pesan error**.
- **Gate serah terima dinilai atas unit yang DIBUKA, bukan se-SPK.** Driver yang
  membuka unit non-COD menuntaskan unit COD sekamar tanpa pernah diminta foto
  uang. App belum bisa memilih anchor sendiri (layar detail tak memuat saudara
  se-SPK) — mitigasinya label "COD · tagih Rp…" di kartu antrian + kalimat
  pengarah di form serah terima. Kalau nanti ada endpoint "unit se-batch",
  inilah tempat pertama yang harus memakainya (`loadBatchUnits` sudah jadi
  polanya — tinggal dilebarkan ke tahap driver).

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
`FloatingNavigationBar` at the bottom holding the browse tabs (Activity + Operasional — selected
tab expands to icon+label, others are icon-only).

> **Tombol Cari (search FAB) DIHAPUS 2026-07-29** atas permintaan user. Pill kini memenuhi
> lebar layar sendirian dan `TridjayaFloatingNav` tak lagi punya parameter `searchItem`.
> **`AppDestination.INVENTORY` SENGAJA tetap ada** — ia HOST `InventoryNavHost`, jadi
> menghapusnya dari enum akan mematikan seluruh menu Inventory (jelajah barang, detail
> produk, flyer) DAN pencarian gabungan, bukan cuma tombolnya. Ia hanya dilepas dari
> `bottomNavItems` dan kini dijangkau lewat **dua ubin berbeda di seksi PINTASAN layar
> Activity** — satu tab, dua tujuan; jangan digabung jadi satu:
>
> | Ubin | Item registri / `navKey` | Callback | Mendarat di |
> |---|---|---|---|
> | **"Cari Barang"** | `inventory` | `onQuickAccessInventory` → `inventoryOpenListSignal++` | `INVENTORY_ROUTE_LIST` (jelajah barang: filter, urut, paging, stok per cabang) |
> | **"Cari Semua"** | `cari_semua` | `onQuickAccessSearch` → `inventoryOpenSearchSignal++` | `SEARCH_ROUTE_ROOT` (`GlobalSearchScreen`: produk + prospek dalam satu kolom) |
>
> Keduanya di luar `routeForNavKey` (ditangani seperti `"crm"` — tujuannya NavHost lain).
> Tile "Inventory" di grid Akses Cepat Operasional memakai callback yang pertama.
> **Dua sinyal terpisah itu WAJIB**, bukan kemewahan: tab ini tetap ter-compose seumur sesi,
> jadi sesudah sekali "Cari Barang" nav controller-nya duduk di `INVENTORY_ROUTE_LIST` dengan
> `SEARCH_ROUTE_ROOT` sudah di-pop — pindah tab tanpa sinyal akan memunculkan daftar barang
> lagi. `openSearchSignal` memakai `popUpTo(graph.id) { inclusive = true }` (kosongkan seluruh
> tumpukan tab, apa pun isinya) supaya Back dari pencarian keluar ke Activity, bukan mendarat
> di daftar barang. Dijaga `ActivityRegistryTest` (`inventory punya pintu masuk di Activity dan
> tak lagi di bottom nav` + `cari semua adalah pintu kedua yang terpisah dari cari barang`).
>
> Riwayat: antara 41f570d dan perbaikan ini, kedua pintu masuk sama-sama menaikkan
> `inventoryOpenListSignal` sehingga `GlobalSearchScreen` praktis tak terjangkau.

Deskripsi historis tab Cari (masih berlaku untuk isi `InventoryNavHost` itu sendiri): tab ini
membuka **global search** (`GlobalSearchScreen`, `ui/search/`), NOT the
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
that report the keyboard correctly. Wired via `TridjayaFloatingNav(pillItems)`, **overlaid** at `BottomCenter`
inside `MainActivity`'s content `Box` (NOT `Scaffold.bottomBar`) so content scrolls *behind* it
like Rhythm — every scrollable tab (Activity/Operasional/Inventory/Leads/RankingList/Settings) adds
~100dp bottom content clearance so nothing hides permanently. Sejak search FAB dihapus, pil
memenuhi lebar sendirian dan tab terpilih diberi **sisa ruang** supaya label panjang
("Operasional") tak terpotong (6b40d08) — jangan kembalikan ke pembagian rata. The nav **hides on
any sub-screen**: `MainScreen` hoists each tab's nested `NavHostController`, watches its current route
via `currentBackStackEntryAsState`, and an `AnimatedVisibility` (slide down + fade) shows the nav
only when the selected tab is on its root route — hidden on pushed details (product/lead/ranking/add)
and on Settings, so those full-screen pages own the frame. Each nested `NavHost` uses Rhythm's
sub-screen transition
(`fadeIn(300) + slideInVertically(offsetY = it/4, tween 350 EaseInOutQuart)`, reversed on pop).
This was chosen over Material3 `NavigationBar` **and** over `NavigationSuiteScaffold` at the user's
explicit request (they compared all three) — don't swap it without asking. The Leads screen's own
add FAB is deliberately a smaller tonal `SmallFloatingActionButton` so it reads as secondary.

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

Capture works via **`PixelCopy`** (API 24+) on the host Window, cropped to the flyer's
`onGloballyPositioned` bounds, with a `legacyCapture()` `View.draw(Canvas)` fallback when no Window
is reachable — see the performance section for why. No newer Compose `GraphicsLayer` capture API is
used (wasn't confirmed available in this project's resolved Compose version, so don't assume it
exists without checking `ui-graphics-android`'s actual jar contents first). Three actions: "Buat
Gambar" (generate + generic Android share sheet), "Kirim ke WA" (generate + `Intent` targeted at
`com.whatsapp`, falls back to generic share if not installed), "Salin" (copies a formatted
"Struktur Kredit" text block to clipboard).

**Product images ARE implemented now** (catatan lama "tidak ada field foto" sudah kedaluwarsa).
Foto datang dari field ERP `Gambar`; `data/ProductImageUrl.resolve()` menormalkan nilainya —
formatnya belum pasti dari backend, bisa path relatif atau URL penuh, jadi ia melewatkan yang
sudah `http(s)://` dan memprefiks sisanya dengan `BuildConfig.API_BASE_URL`. **Semua** pemakai
harus lewat helper itu supaya aturannya seragam (`FlyerLayouts.kt`, `InventoryScreen.kt`,
`GlobalSearchScreen.kt`); render pakai Coil (`io.coil-kt:coil-compose`). Placeholder tetap dipakai
saat `Gambar` kosong/null.

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
- **Menu**: absen adalah tugas harian pertama di layar **Activity**, route nested `home_absen` di
  `ActivityNavHost` (nama route `home_*` sengaja tak berubah saat `HomeNavHost` di-rename — lihat
  catatan arsitektur Activity). Role gate di **backend** (STAFF_ROLES self-service); gate di app
  kini dinyatakan lewat `ActivityRegistry`/`QuickAccessMenus`, bukan lagi "semua user lihat menu".
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
  check CPU usage via `ps aux | grep java` or `top` if in doubt (climbing CPU = still working).
- A signed release APK and a debug APK have **different signatures** — installing one over the
  other on the same device requires `adb uninstall` first (wipes local app data/login). Always
  ask before doing this; it's destructive to the user's test session.
- Observed R8 wall-clock on a full release build here: **~44 min** once (cold-ish). Build via
  `run_in_background`, watch the Gradle daemon's `java` CPU/RAM climbing to confirm progress,
  and only trust `BUILD SUCCESSFUL` in the output — `--console=plain` buffers, so an empty output
  file mid-build is normal, not a hang.
- **`versionCode` must be bumped** in `app/build.gradle.kts` for every release (per 2026-07-29:
  `versionCode = 49`, `versionName = "2.38"`) — the update system's Remote Config comparison and
  Play/side-load upgrades both depend on it. Pola commit yang dipakai: satu commit
  `chore(release): bump versi X.YY (<ringkasan>)` di akhir tiap batch fitur/fix.

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

**Dipindah dari Windows/laragon ke Kali Linux pada 2026-08-08 — semua path di bawah sudah
diverifikasi ulang di mesin baru; jangan pakai catatan `C:\...` versi lama.**

- No Android Studio GUI available in this environment — everything via Gradle CLI.
- Sistem cuma punya `openjdk-25-jre` (JRE, **tanpa `javac`**) — `javac`/`java` di PATH tidak cukup
  untuk build. JDK 17 yang benar ada di `~/.jdks/jdk-17.0.20+8` (Temurin), diekspor sebagai
  `$JAVA_HOME_17` lewat `~/.zshrc`. **Selalu override eksplisit:**
  `JAVA_HOME=$JAVA_HOME_17 ./gradlew <task>` (gradlew fallback ke `java` biasa di PATH kalau
  `JAVA_HOME` kosong, yang salah versi).
- Tidak ada lagi isu quoting PowerShell untuk flag `-P` — di bash/zsh
  `-Pkotlin.compiler.execution.strategy=in-process` jalan normal tanpa perlu tanda kutip khusus.
- Builds are slow (1-3+ min for debug, much longer for release/R8) — always run via
  `run_in_background: true` and poll with `ScheduleWakeup`, don't block synchronously.
  Patokan nyata: `:app:installDebug` dingin (daemon baru) memakan **~18 menit** di mesin ini.
  (Angka ini dari mesin Windows lama — belum ada patokan baru di Kali, catat ulang kalau sudah ada.)
- Android SDK: `~/Android/Sdk`, dipasok lewat `local.properties` (`sdk.dir`) **dan** `$ANDROID_HOME`
  (diekspor di `~/.zshrc`).
- `adb` ada di `$ANDROID_HOME/platform-tools/adb` dan **sudah masuk `$PATH`** (lewat `~/.zshrc`) —
  panggil `adb` langsung, tidak perlu path lengkap.
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
- **Home dashboard fires its 4 endpoints concurrently.** `SalesRepository.homeDashboard()`
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
- Home (tab Operasional): KPI summary (today/MTD + growth badges vs yesterday/last month),
  branch + sales rankings (top 5 + "lihat semua"). Dashboard
  sections (KPI / Target / Ranking Cabang / Ranking Sales) are **user-reorderable + show/hide**
  via a "Tune" button → `HomeCustomizeSheet` (up/down arrows, not drag). Order+visibility persist
  in plain (non-encrypted) SharedPreferences via `HomeLayoutPreferences` (Hilt constructor-injected).
  **Kartu sapaan sudah TIDAK di sini** — pindah ke Activity (lihat baris berikut); slot teratas
  Home kini murni `EventCarousel`, dan tak dirender sama sekali kalau tak ada event aktif.
- Kartu sapaan (`ui/activity/GreetingCard.kt`): gradien + ikon berubah per waktu
  (pagi/siang/sore/malam) dengan override musiman (`seasonalGreeting`, mis. Agustus =
  Kemerdekaan). Tampil paling atas di **Activity** (layar pertama app), menggantikan
  `GreetingRow` teks polos lama; baris tanggal ikut membawa nama cabang (param `cabang`)
  supaya info yang dulu ditampilkan `GreetingRow` tak hilang. Kartunya fixed, tak ikut
  pengaturan urutan `HomeCustomizeSheet`.
- Inventory: search (Material3 `SearchBar`), filter chips (ready-only, region, category, brand),
  sort, Paging3 list with expandable per-branch stock breakdown, product detail with flyer
  generator + WhatsApp share + installment simulator
- CRM/Leads: list with search + summary stats, add lead, detail screen (WhatsApp chat deep link,
  pipeline stage picker, won/lost/reopen actions)
- **Alur SPK → pengiriman → PDI → kasir** (`ui/deliveryflow/`): SpkHub + daftar SPK, input item
  (autocomplete barang ber-stok+harga, picker serial, No PO, kolom nominal berformat rupiah lewat
  `ui/theme/RupiahInput.kt`), terbit surat jalan sekali ketuk, serah terima ber-GPS, klaim PDI
  ("Ambil PDI" + label "diproses oleh X"), konfirmasi pembayaran kasir. Syarat kirim disatukan
  (link Maps wajib untuk Sales Antar Sendiri); alasan tolak diskon wajib.
- **Stok opname per unit/serial** (`ui/opname/`, `data/OpnameRepository.kt`): hitung fisik per unit
  ber-serial — **bukan** angka jumlah per SKU — layar scan per unit, laporan PDF berisi daftar
  serial, antrean offline. Normalisasi serial dijaga sejajar implementasi Rust lewat unit test.
  **Kondisi unit = 4 nilai** sejak 2026-08-09 (`KONDISI_PILIHAN`: layak / tidak_layak / repair /
  retur, + kolom keterangan bebas), menggantikan checkbox biner "TIDAK layak". Daftarnya cerminan
  `opname::KONDISI_VALID` Rust (migrasi 194) dan dijaga `OpnameKondisiTest` — kontrak
  stringly-typed lintas repo tanpa pemeriksa kompiler. **Server kini MENOLAK nilai asing** per
  baris (`kondisi_tidak_dikenal`); dulu dipaksa jadi `layak` diam-diam, jadi nilai yang meleset
  bukan lagi "tersimpan salah" melainkan unitnya tak terhitung sama sekali. Kondisi & keterangan
  sengaja BERTAHAN antar scan dalam satu sheet (satu rak rusak ditandai berturut-turut);
  meresetnya tiap unit membuat petugas diam-diam mencatat sisanya sebagai layak.
  **Selisih registry vs lapangan** (`kondisiRegistry`/`kondisiSelisih` dari server) tampil
  sebagai kartu di layar detail sesi. Nilainya SENGAJA tidak disimpan ke Room: vonis registry
  berubah kapan saja admin-stok mengubahnya, jadi menyalinnya ke buffer lokal = menampilkan
  vonis basi di layar yang justru dipakai memverifikasi. Konsekuensinya kartu itu kosong saat
  offline — itu jujur, pembandingnya memang tak terbaca. `refreshValidationStatuses` karena
  itu MENGEMBALIKAN daftar unit versi server, bukan cuma menulis ke Room.
  **Multi-petugas + kartu "Opname Cabang" (2026-08-09)**: server memisahkan izin
  MENGHITUNG (`opname.hitung`, dikunci ke cabang akun) dari izin MEMILIKI SESI,
  jadi petugas cabang kini bisa ikut scan di sesi yang dibuat orang lain. Kartu
  Activity `opname_cabang` memakai kunci `opname.hitung` — BUKAN `opname.view`,
  yang menyetir menu Stock Opname di web dan cuma dipegang pengelola/pemantau.
  **Tile "Opname" di grid Akses Cepat Operasional SENGAJA tidak dihapus** walau
  konvensi repo bilang menu yang naik ke Activity dilepas dari grid: dua kunci
  itu beda audiens (manager/owner punya `opname.view` tapi TIDAK punya
  `opname.hitung`), jadi menghapusnya akan membuang satu-satunya pintu pemantau.
  `canManage`/`canHitung` kini datang dari server (`OpnameDetailDto`), bukan lagi
  disimpulkan `isOwner && draft` — kesimpulan itu menyembunyikan tombol scan dari
  petugas yang justru berhak. Keduanya **`Boolean?`, bukan `Boolean = false`**:
  `null` berarti "server belum mengenal field ini" dan app jatuh balik ke aturan
  lama. Default `false` akan mencabut tombol scan bahkan dari pemilik sesi begitu
  APK baru beredar di atas server lama — mengurangi fungsi yang sudah jalan,
  bukan sekadar konservatif. Tombol hapus per baris memakai `serialMilikSaya`
  (dari daftar unit versi server): petugas boleh mengoreksi salah scan sendiri,
  tak boleh membongkar klaim orang lain.
  **Penolakan server kini dibagi dua (2026-08-09, bersama jendela waktu migrasi
  196)**: PERMANEN (`duplikat_dalam_sesi`, `jendela_sudah_tutup`, dst) barisnya
  dibuang dari Room seperti dulu; SEMENTARA (`jendela_belum_mulai`) barisnya
  **BERTAHAN** dan dikirim ulang otomatis begitu jendela terbuka, dan hasilnya
  dilaporkan `ScanResult.Queued` bukan `Rejected`. Aturan lama (buang SETIAP
  penolakan) benar selama semua penolakan permanen; begitu server bisa menjawab
  "sesi belum dibuka", ia membuang hasil scan petugas yang cuma kepagian —
  tanpa error, tanpa tanda di layar, baru ketahuan saat hitungan akhir kurang.
  **Kode tak dikenal diperlakukan SEMENTARA** (`tolakPermanen` daftar-putih):
  APK yang tertinggal versi tak boleh membuang data karena kode barunya belum
  dikenal.
  **Input jendela waktu** ada di sheet buat-sesi (`OpnameListScreen`), MATI
  secara default — sesi tanpa batas adalah perilaku lama dan tetap sah.
  **Tombol "Nihil"** ada di baris barang yang belum dihitung (`StockSearchRow`),
  hanya untuk yang boleh menghitung DAN yang belum punya unit — menandai nihil
  barang yang sudah dihitung akan membuang hasil kerja orang lain, dan server
  pun menolaknya. Dikonfirmasi dulu karena tercatat SELISIH PENUH (barang
  dilaporkan hilang), bukan "lewati saja". `tandaiNihil` SENGAJA tidak diantre
  offline seperti scan: nihil adalah PERNYATAAN yang bisa diulang kapan saja,
  dan pernyataan yang "tersimpan" menurut layar tapi belum sampai server jauh
  lebih menyesatkan daripada penolakan yang jelas.
  Validasinya di `ui/opname/OpnameJendela.kt` (fungsi murni, diuji): cerminan
  `parse_jendela` Rust, memeriksa rentang angka juga karena regex saja
  meloloskan bulan 13 / jam 25. Perbandingan urutan mulai-selesai **leksikografis
  atas string wall-clock**, sengaja tanpa aritmetika tanggal — itu sekaligus
  menjauhkannya dari `java.time` yang haram di `app/src/main`.
- **Pusat Notifikasi** (`ui/notifications/`) + deep-link FCM; notifikasi terbaca bisa dihapus.
- Indent, mutasi histori, deadstock, perubahan harga ERP, payroll, input serial — masing-masing
  satu layar + ViewModel, semuanya ber-gate (lihat `ActivityRegistry`/`QuickAccessMenus`).
- **Input Serial Number (`ui/serials/`) = SATU tile, DUA pekerjaan.** Layar pertamanya
  pilihan mode (`SerialInputMode`), bukan langsung daftar produk: `TETAPKAN` mendaftarkan
  SN pabrik yang sudah tertempel di unit (`POST /inventory/serial-numbers`), `BUAT_BARU`
  membuat kode pengganti `GEN-…` untuk barang yang memang tak pernah punya nomor pabrik
  (`POST /inventory/serial-numbers/generate`). Keduanya dulu ditumpuk dalam satu form dan
  yang kedua tersembunyi di kaki halaman — di web keduanya memang menu terpisah
  (`AdminStokSerialInputPage.tsx` + `SerialGeneratePage.tsx`).
  **Tetap satu tile** (`serial_input`, kunci `serial.input`) walau web punya dua menu:
  `SERIAL_INPUT_ROLES` dan `SERIAL_GENERATE_ROLES` sama-sama `["admin-stok"]`, jadi tile
  kedua tak menyaring siapa pun — ia cuma menambah kunci yang bisa melenceng.
  **Daftar produknya membawa badge cakupan + filter Semua/Belum lengkap/Lengkap** dari
  `GET /inventory/serial-numbers/summary` (`SerialCoverage.kt`, fungsi murni + diuji).
  Alur kerjanya menetapkan SN ke SELURUH produk, jadi tanpa filter itu satu-satunya cara
  tahu mana yang belum tergarap adalah membuka produk satu per satu. **`TAK_DIKETAHUI`
  BUKAN sinonim `BELUM`**: saat cakupan gagal dimuat atau dipotong di batas server (8.000
  kode, field `truncated`), produk yang absen dari peta bisa saja sudah lengkap —
  memvonisnya `BELUM` memicu pendaftaran ulang. Cakupan gagal SENGAJA tidak mengisi
  `contextError`: daftar produk yang sudah terbaca tak boleh ditutup layar error, karena
  pendaftaran SN tetap sah tanpa peta kelengkapan.
  Registry inilah yang jadi bahan verifikasi lapangan — petugas cabang men-scan barcode
  tiap unit saat opname dan server menolak serial yang sama dua kali dalam satu sesi
  (`duplikat_dalam_sesi`), jadi produk yang SN-nya belum ditetapkan di sini **tak bisa
  diverifikasi sama sekali** di sana.
- **Panduan Alur + Direktori Petugas** (`ui/activity/PanduanAlurScreen.kt`) dari tombol PINTASAN.
- Settings: profile display, nomor WA bisa diubah, semua role terlihat, logout dikonfirmasi,
  cabang, cek pembaruan (`ui/settings/SettingsFormat.kt` memformat nilai tampilan)
- Input Aktivitas / raport harian (`ui/raport/`, **BETA** — kartu di Activity berlabel BETA):
  daftar jobdesk posisi karyawan dari `GET /api/jobdesk-divisions` (dicocokkan ke `divisi`
  profil lewat `matchJobdeskPosition`, port 1:1 `getPositionMatch` web — **tak boleh** jatuh ke
  posisi pertama saat tak cocok, itu bikin orang dinilai atas jobdesk divisi lain), kirim per
  baris ke `POST /api/raport-harian`. Bukti = foto kamera ber-watermark (`PhotoWatermark`,
  sama dengan absensi/PDI) atau `mode=none` + alasan ≥10 karakter. **Belum ada** video &
  unggah dari galeri (masih lewat web).
  Jendela jam pelaporan (default 08:00–18:00) & larangan hari Minggu ditegakkan server;
  pesan detailnya ada di `errors[0]`, bukan `message` — `RaportRepository.parseError`
  sengaja mengutamakan `errors[0]` (repository lain di app ini belum).
  **Gate tampilan DIBUKA 2026-08-12** atas permintaan user: gate akun-uji dicabut
  (`ITEM_KHUSUS_AKUN_UJI` kini kosong — mekanismenya sengaja dipertahankan untuk fitur
  BETA berikutnya) dan `RAPORT_INPUT_ROLES` = `ALL_LOGGED_IN`. **Perhatikan mismatch yang
  disengaja:** `POST /raport-harian` di server TETAP `KARYAWAN_ROLES` (role `karyawan` saja),
  jadi pemilik role lain bisa membuka layarnya tapi kiriman mereka dijawab 403. Kalau semua
  orang harus benar-benar bisa mengirim, yang diubah `KARYAWAN_ROLES` di backend — jangan
  menambalnya dari app.
- **Komplain / Home Service** (`ui/homeservice/`, `data/HomeServiceRepository.kt`) — alur purna-jual
  penuh di mobile: lapor → triase CS → kunjungan teknisi → penarikan unit. Lima kartu di Activity
  (`lapor_komplain`, `komplain_masuk`, `tugas_home_service`, `tarik_unit`, `tugas_tarik_unit`),
  route `home_hs_*`. Yang mengikat:
  * **`status` server cuma menerima SATU nilai** (`"baru,ditugaskan"` → 400), sementara tiap antrian
    butuh beberapa — jadi daftar dimuat TANPA filter status lalu disaring klien (`saringStatus` +
    `HsMode`, cerminan cara web). Angka badge Activity juga dihitung dari hasil saringan itu, BUKAN
    `total` (yang berarti "semua tiket terambil", bukan "yang menunggu kamu").
  * **`mine=true` memilih KOLOM berdasarkan `jenis`**: `tarik_unit` → `tarik_driver_id`, selain itu
    `assigned_teknisi_id`. Layar driver yang lupa mengirim `jenis=tarik_unit` selalu kosong TANPA
    error — dijaga `HomeServicePlanTest`.
  * **`jadwalAt` hanya `YYYY-MM-DD` / `YYYY-MM-DD HH:MM:SS`** (ISO8601 ber-`Z` → 400), dan jamnya
    **WIB apa adanya** — server menyimpan yang dikirim tanpa konversi zona. Disaring
    `jadwalUntukServer` sebelum dialog tertutup.
  * **Foto di-serve terautentikasi** (`api/home-service/photo/{berkas}` + bearer) — `/uploads/…`
    mentah selalu gagal (`fotoHsUrl`).
  * `umurJam`/`melewatiSla` dipakai APA ADANYA dari server. Jangan hitung ulang: `created_at`
    ditulis WIB tapi dibandingkan dengan `Utc::now()`, jadi angkanya sudah punya bias ~7 jam yang
    diketahui — menghitung sendiri cuma menghasilkan angka KEDUA yang beda dari yang dilihat CS.
  * **Role `cs` SENGAJA tak ditulis** di `HS_LAPOR_ROLES`/`HS_DISPATCH_ROLES` walau ada di daftar
    server: rust-shared menyatakan sendiri role literal `cs` belum ada di sistem, jadi ejaan itu tak
    akan pernah cocok (baris mati). CS sungguhan lolos lewat `homeservice.dispatch`.
  * **Belum ada di app** (sengaja): sparepart berbiaya saat menutup kunjungan (nominal + bukti bayar
    + setoran kasir — alur uang yang belum diuji lewat HP), dan tak ada endpoint edit/komentar tiket
    sama sekali di backend (salah input = batalkan lalu buat ulang).
  * Dropdown teknisi memakai `GET /api/users?role=pdi`, yang gate-nya (`USERS_READ_ROLES`) TIDAK
    memuat `cs` — kegagalannya ditampilkan sebagai keterangan "tugaskan lewat web", sama seperti web.
- **Nilai Aktivitas (PIC raport)** — `ui/raport/RaportReviewScreen.kt` + `RaportReviewViewModel`
  + logika murni `RaportReviewPlan.kt`, kartu ANTRIAN `raport_review` di Activity (route
  `home_raport_review`). Memuat `GET /api/raport-harian?tanggal=…&status=pending` (SELURUH
  karyawan — `antrianReview`, bukan `raportOfDay` yang menyaring ke diri sendiri) dan memutus
  lewat `PATCH /api/raport-harian/{id}/review` `{status, score, comment}`. Yang perlu diketahui:
  * Gate `raport.review` / `RAPORT_REVIEW_ROLES` — **`owner` tidak termasuk** (ia boleh membaca
    lewat `RAPORT_VIEW_ALL_ROLES`, tapi ditolak `review_raport`).
  * Skor ditentukan SERVER: `rejected` → 0, `approved` → `score ?: 100` di-clamp 0..100.
    `skorReview` mencerminkannya supaya angka di layar sama dengan yang tersimpan; tolak wajib
    berkomentar (`bolehSimpanReview`, dijaga di ViewModel juga, bukan cuma di tombol).
  * Bukti TIDAK bisa dirender dari `/uploads/raport/…` mentah (upload privat, S-02 web) —
    `evidenceImageUrl` memetakannya ke `api/raport-harian/evidence/{berkas}` + header
    `Authorization` (pola `AuthedImage`). Sengaja BUKAN alias gateway `api/raport-files/*`
    yang dipakai web: alias itu menolak role `hrd`, padahal `hrd` termasuk penilai.
    `evidenceUrl` bisa berupa string JSON array (baris lama multi-bukti) → `parseEvidenceUrls`.
  * `mode=video` tak diputar di app (alasan sama dengan `ChatReviewScreen`) — barisnya diberi
    penanda dan pemeriksaannya diserahkan ke web.
  * Server TIDAK men-scope penilai↔karyawan sama sekali (`service.review()` tak menerima
    identity): siapa pun yang lolos role boleh menilai baris cabang mana pun.
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

## Tema & warna

Bagian ini pernah terpecah dua catatan yang saling bertabrakan (satu bilang default `Biru
Tridjaya` `#1E63E9` lewat `blueDefaultScheme()`, satu bilang tema app = ungu Rhythm `#6750A4`).
**Keduanya sudah tidak cocok dengan kode** — `blueDefaultScheme()` tak ada lagi, dan ungu Rhythm
bukan warna app. Yang berlaku sekarang, dari `ui/theme/ThemeSchemes.kt`:

- **Default = `AppColorScheme.DEFAULT`, label "Tridjaya Web", primary `#465FFF`**, dibangun
  `tridjayaWebScheme(dark)` — mengikuti palet web Tridjaya, bukan M3 baseline. Ia mendefinisikan
  **seluruh** role sendiri termasuk netral (light: background/surface putih `#FFFFFF`, dark:
  `#101828` dengan tangga `surfaceContainer*` sendiri), jadi jangan berasumsi ia memakai netral
  bersama seperti preset lain.
- **8 preset lain** — Lavender/Rose/Warm/Amber/Forest/Mint/Cool/Ocean — cuma memasok triad
  primary/secondary/tertiary lewat helper `lightTriad()`/`darkTriad()`, dan **meminjam netral +
  error dari `Color.kt`**. Di situlah warisan Rhythm sesungguhnya masih hidup.
- **`Color.kt` = sisa port Rhythm** (github.com/cromaguy/Rhythm), bersama `Type.kt` dan
  `Shape.kt` (8/12/16/24/32 dp) — di-port persis atas permintaan user, menggantikan branding
  violet lama (`#5C4AD5` + amber tertiary); jangan pulihkan yang lama. Tapi seed ungu `#6750A4`
  kini **praktis mati**: `PrimaryLight` tidak dirujuk di mana pun (cek `grep -rn "PrimaryLight\b"`
  — hanya deklarasinya sendiri). Yang benar-benar dipakai dari file itu adalah netral, error, dan
  `InversePrimaryLight`. Jadi: Rhythm = substrat netral + tipografi + bentuk, **bukan** warna
  utama app.
- Hanya *visual token* Rhythm yang di-port, BUKAN mesin temanya (dynamic color dari album art,
  font "Geom" unduhan, preset festive) — itu mesin app musik, di luar lingkup.
- **Material You** (`dynamicLight/DarkColorScheme`) tersedia sebagai pilihan di Android 12+;
  mode gelap ikut sistem/terang/gelap. Pilihan disimpan `ThemePreferences` (`data/`, Hilt
  singleton + StateFlow) yang di-observe `MainActivity.setContent`, jadi seluruh app berganti
  warna live; diatur dari Settings → Tema (`ThemeSettingsScreen`).
- **Flyer sengaja kebal tema** (`FlyerColors` hardcoded) supaya gambar yang dibagikan identik di
  HP mana pun — lihat bagian flyer.
- Ikon se-app pakai `Icons.Rounded.*`; interaksi utama memicu haptic `CONTEXT_CLICK` ringan lewat
  `rememberHapticClick`; `ExpressiveShapes` menambah token squircle/asimetris.

Catatan historis: "dynamic color deliberately not applied" sudah **superseded** — user memang
meminta theming penuh.

## Known gaps / natural next steps

- No product photos (see flyer section above) — needs a backend image URL field first
- **Automated tests exist but are JVM-unit-only.** `app/src/test/` has a real suite (pure-logic
  tests for delivery flow models, branch regions, indent decisions, menu access gates, the
  Activity registry/plan/nav-key mapping — run via `:app:testDebugUnitTest`); `app/src/androidTest/`
  still has no instrumented Compose UI tests. That remains the next investment.
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
  was bumped to v5 for the `pendingSync` column at the time (destructive migration — cache
  re-syncs); skema sekarang **v14**, lihat catatan migrasi Room di bawah.
  **Still online-only:** move-stage / mark won/lost (they act on a server `id`, so a pending lead
  can't be mutated until it syncs).
- No string resources (see guideline section above) and no Gradle version catalog — both real
  gaps, both deliberately deferred rather than rushed

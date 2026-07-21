# Tridjaya App — Kebutuhan API

Daftar kebutuhan endpoint API backend (`https://tridjaya.com/api`, repo `C:\laragon\www\tridjaya`)
untuk aplikasi Android **Tridjaya App**, dikelompokkan per domain: **Auth**, **Inventory**, **CRM**,
**Sales — Klasemen (Leaderboard)**, **Sales — KPI**. Dipakai sebagai checklist koordinasi dengan tim
backend — status per endpoint mencerminkan kondisi kode app saat dokumen ini ditulis (2026-07-13).

> Catatan penamaan: path aktual di backend **tidak** memakai prefix `api/sales/*` — leaderboard
> ada di `api/mobile/v1/leaderboards/*` dan KPI ada di `api/inventory/executive/*`. Dokumen ini
> mengelompokkan keduanya di bawah domain fungsional "Sales" karena itu yang dipakai app, tapi
> tabel di bawah selalu pakai path asli yang benar-benar di-deploy, bukan path yang diusulkan.

## Legenda status

| Simbol | Arti |
|---|---|
| ✅ | Sudah diimplementasi & dipanggil di app |
| ⚠️ | Ada/terdokumentasi di backend, **belum** dipakai app |
| 🔒 | Butuh role manager/admin/owner (atau tier finance) — bukan semua user login bisa akses |

---

## 1. Auth — `api/auth/*`

Semua publik kecuali `profile`/`change-password` (butuh Bearer token).

| Method + Path | Status | Dipakai untuk |
|---|---|---|
| `POST /auth/login` | ✅ | Login NIK/WhatsApp + password |
| `POST /auth/refresh` | ✅ | Rotasi access+refresh token (interceptor otomatis) |
| `POST /auth/logout` | ✅ | Logout, hapus token lokal |
| `GET /auth/profile` | ✅ | Ambil profil user setelah login |
| `PATCH /auth/profile` | ✅ | Update nama/WhatsApp |
| `POST /auth/change-password` | ✅ | Ganti password (voluntary + gate `must_change_password`) |
| `POST /auth/forgot-password` | ✅ | Kirim link reset via email |
| `POST /auth/reset-password` | ✅ | Set password baru dari token reset |

**Status: lengkap.** Tidak ada gap diketahui untuk domain Auth.

---

## 2. Inventory — `api/inventory/*`, `api/catalogs`, `api/owner/*-transactions`

### Sudah dipakai

| Method + Path | Status | Dipakai untuk |
|---|---|---|
| `GET /inventory/stok-cabang` | ✅ | List produk tab Inventory (paginated, di-sync ke Room) |
| `GET /owner/sales-transactions` | ✅ 🔒 | Drill-down transaksi dari baris Klasemen Sales |
| `GET /owner/branch-sales-transactions` | ✅ 🔒 | Drill-down transaksi dari baris Ranking Cabang |

### Belum dipakai (gap)

| Method + Path | Status | Kegunaan potensial |
|---|---|---|
| `GET /catalogs`, `GET /catalogs/{id}` | ⚠️ | Katalog produk enriched (promo, selling points, **gambar produk** — belum ada field foto di app; ini kandidat sumbernya) |
| `GET /product-categories` | ⚠️ | Filter kategori katalog |
| `GET /inventory/barang` | ⚠️ | Data barang ERP mentah alternatif (mirip stok-cabang, shape beda) |
| `GET /landing/home` | ⚠️ | Konten landing (tidak relevan untuk app internal) |
| `GET /partners` | ⚠️ | Daftar partner (tidak relevan untuk app internal) |
| `GET /inventory/top-selling` | ⚠️ 🔒 | Admin — produk terlaris |
| `GET /inventory/stock-summary` | ⚠️ 🔒 | Admin — ringkasan stok |

---

## 3. CRM — `api/crm/*`, `api/mobile/v1/crm/*`

### Sudah dipakai

| Method + Path | Status | Dipakai untuk |
|---|---|---|
| `GET /crm/pipelines` | ✅ | Daftar pipeline + stage (Kredit/Cash) |
| `GET /crm/leads` | ✅ | List lead (self-scoped utk karyawan) |
| `POST /crm/leads` | ✅ | Tambah lead baru (offline-first, antre sync) |
| `GET /crm/leads/{id}` | ✅ | Detail lead + task + aktivitas + history |
| `POST /crm/leads/{id}/move-stage` | ✅ | Pindah stage pipeline |
| `POST /crm/leads/{id}/won` | ✅ | Tandai menang |
| `POST /crm/leads/{id}/lost` | ✅ | Tandai kalah + alasan |
| `POST /crm/leads/{id}/reopen` | ✅ | Buka kembali lead won/lost |

### Belum dipakai (gap)

| Method + Path | Status | Kegunaan potensial |
|---|---|---|
| `GET /crm/dashboard` | ⚠️ | Funnel + openLeads/wonThisMonth/lostThisMonth agregat (403 utk karyawan) |
| `GET /crm/reports` | ⚠️ | Laporan periode (conversion rate, won value, by-source) |
| `GET /crm/lost-reasons` | ⚠️ | Master alasan lost (dropdown saat tandai lost) |
| `PATCH /crm/leads/{id}` | ⚠️ | Edit field lead (nama/phone/catatan dll) |
| `GET /crm/leads/check-phone` | ⚠️ | Cek duplikat nomor sebelum create lead |
| `POST /crm/leads/{id}/activities` | ⚠️ | Log aktivitas manual (call/wa/visit/meeting/note) |
| `GET /crm/tasks`, `POST /crm/tasks` | ⚠️ | List/buat follow-up task |
| `POST /crm/tasks/{id}/done\|cancel\|reopen` | ⚠️ | Transisi status task |
| `GET /api/mobile/v1/crm/summary` | ⚠️ 🔒 | Facade mobile → proxy ke `crm/dashboard`, guard `crm-manager`/`admin` |
| `GET /api/mobile/v1/crm/analytics` | ⚠️ 🔒 | Facade mobile → proxy ke `crm/analytics` (funnel, stage aging, response time, dll) |
| `GET /api/mobile/v1/crm/pipelines` | ⚠️ 🔒 | Facade mobile setara `crm/pipelines`, guard lebih ketat |
| `GET /api/mobile/v1/crm/leads` | ⚠️ 🔒 | Facade mobile setara `crm/leads` |

> Dua jalur CRM tersedia: `api/crm/*` (yang sudah dipakai app, karyawan self-scoped di
> crm-service) dan `api/mobile/v1/crm/*` (facade baru). **Koreksi**: gateway guard kedua jalur ini
> sama persis — `CRM_ROLES = admin, crm-manager, karyawan, manager` (`gateway/src/lib.rs:740`) —
> jadi tidak ada alasan pindah ke facade, `api/crm/*` yang sudah dipakai app sudah tepat.

---

## 4. Sales — Klasemen (Leaderboard) — `api/mobile/v1/leaderboards/*`

| Method + Path | Status | Dipakai untuk |
|---|---|---|
| `GET /api/mobile/v1/leaderboards/sales` | ✅ 🔒 | **Satu-satunya call yang dipakai** — respons berisi `salesTable` (→ Klasemen Sales) **dan** `omsetPerCabang` (→ Ranking Cabang) sekaligus |
| `GET /api/mobile/v1/leaderboards/branches` | ⚠️ 🔒 | **Sengaja tidak dipanggil** — proxy ke handler backend yang identik (`owner/sales-report`), jadi memanggil keduanya = duplikat request untuk data yang sama |

Field per baris `salesTable`: `rank, sourceCode, name, dealerCode, cabang, totalTransaksi, totalQty,
activeDateRevenue, revenue`. Field per baris `omsetPerCabang`: `kodeDealer, cabang, totalTransaksi,
totalQty, activeDateOmset, currentMonthOmset, omset, comparisonPercent`.

**Gap**: tidak ada data pergerakan peringkat (naik/turun vs hari sebelumnya) — beda dari fitur
Klasemen di web (`StandingsSection.tsx`) yang hitung movement dari `GET /finance/omset` dua kali
(hari ini vs kemarin). Kalau movement badge dibutuhkan di app, perlu endpoint baru atau app
menghitung sendiri dari dua snapshot `finance/omset` (lihat §5).

---

## 5. Sales — KPI — `api/inventory/executive/*`, `api/finance/*`

### Sudah dipakai

| Method + Path | Status | Dipakai untuk |
|---|---|---|
| `GET /inventory/executive/kpi` | ✅ | Kartu "Sales KPI" Home (transaksi/unit/revenue hari ini vs kemarin, MTD vs bulan lalu) |
| `GET /inventory/executive/monthly-target` | ✅ | Kartu "Target Bulanan" Home (achievement %, proyeksi) |

### Belum dipakai (gap)

| Method + Path | Status | Kegunaan potensial |
|---|---|---|
| `GET /inventory/executive/sparkline` | ⚠️ | Grafik tren 7 hari (pelengkap kartu KPI) |
| `GET /inventory/executive/finco` | ⚠️ | Data leasing/finco (unit & amount per kodeleasing) |
| `GET /finance/summary` | ⚠️ | Ringkasan omset MTD per cabang (`?periode=`) |
| `GET /finance/omset` | ⚠️ | Data omset harian mentah — dasar perhitungan klasemen di web, juga sumber movement badge kalau mau direplikasi (§4) |
| `GET/PUT /finance/targets` | ⚠️ | Target per cabang/karyawan (baca + set) |
| `POST /finance/sync` | ⚠️ | Paksa sync omset ERP→finance (dipakai web sebelum tampilkan klasemen realtime) |

---

## Ringkasan cakupan

| Domain | Endpoint dipakai | Endpoint belum dipakai |
|---|---|---|
| Auth | 8 / 8 | 0 |
| Inventory | 5 | 7 |
| CRM | 8 | 12 |
| Sales — Klasemen | 1 (cakup 2 fungsi) | 1 (duplikat, sengaja diskip) |
| Sales — KPI | 2 | 6 |

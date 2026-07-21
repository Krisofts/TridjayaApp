# Tridjaya Backend — Referensi Endpoint Lengkap

> Diekstrak langsung dari kode `gateway/src/lib.rs` (repo backend `C:\laragon\www\tridjaya`,
> Axum 0.8) — **186 route publik** lewat gateway, dikelompokkan jadi 23 grup router (masing-masing
> punya guard/role sendiri), dari ~16 microservice. Diregenerasi 2026-07-13 (sebelumnya 164 route/
> 14 service — selisihnya termasuk facade `api/mobile/v1/*` yang baru ditambahkan).
>
> **Sumber kebenaran untuk detail request/response (subset app mobile):** `docs/api/android-api.md`
> dan `docs/api/mobile-v1.md` di repo backend, plus `docs/api/auth.md`. Dokumen ini adalah *peta
> permukaan* seluruh backend (bukan cuma yang dipakai app) — untuk bentuk JSON, cek doc itu atau
> kode service.

## Dasar

| Hal | Nilai |
|---|---|
| Base URL produksi | `https://tridjaya.com/api` |
| Protokol | HTTPS (Let's Encrypt), HTTP/1.1, JSON |
| Auth | `Authorization: Bearer <access_token>` (JWT 15 mnt); refresh token dirotasi |
| Konvensi field | `camelCase` (kecuali respons login/refresh: `snake_case`) |
| Envelope sukses | `{ "message": "...", "data": { } }` |
| Envelope gagal | `{ "code": "...", "message": "...", "errors": [ ] }` |
| Rate limit | login 10/5mnt · global 600/mnt · beberapa endpoint publik punya limit sendiri (lihat §Publik) |
| Catatan | `GET /catalogs` cold start ±51 dtk (snapshot 70k produk) → timeout ≥60 dtk |

Kode error: `400 validation_error` · `401 unauthorized` (→ refresh) · `403 forbidden` ·
`404 not_found` · `409 conflict` · `429` (backoff) · `502 gateway_error` (retry).

Semua route diakhiri `.merge(protected)` (butuh JWT valid via `require_auth`) kecuali grup
**Publik** & **Karier** di bawah (`optional_auth`/tanpa auth). Beberapa grup protected menambah
guard *role* di atas `require_auth`; beberapa (`mutasi`, `wa_blast`) sengaja **tidak** punya guard
role di gateway — RBAC-nya ditegakkan di service masing-masing lewat header `x-user-role`.

### Referensi role/guard

| Guard | Role diizinkan | Catatan |
|---|---|---|
| `require_user_admin_access` | admin, manager | |
| `require_users_read_access` | admin, manager, crm-manager | |
| `require_marketplace_admin` | admin, operator | |
| `require_finance_access` | manager, sales-manager, kepala-cabang, admin, owner | GET/HEAD lebih longgar (union grant-role); write = role asli saja |
| `require_owner_access` | owner, admin | |
| `require_manager_access` | manager, kepala-cabang, admin | |
| `require_login_anomaly_access` | manager, admin, owner | GET = union; POST = role asli saja; sengaja tidak termasuk kepala-cabang |
| `require_crm_access` | admin, crm-manager, karyawan, manager | Sama untuk `/api/crm/*` **dan** `/api/mobile/v1/crm/*` |
| `require_hrd_access` | hrd, admin, owner | |
| `require_sop_ai_access` | admin, owner, ai-engineer | |
| `require_raport_file_access` | admin, owner, manager, kepala-cabang, pic_raport, karyawan | |
| `require_admin` | admin | |
| `require_indent_submitter` | GET: admin, kepala-cabang, manager, owner, indent-approver · POST: admin, kepala-cabang, manager | |
| `require_indent_item_access` | GET: (sama seperti submitter) · non-GET: owner, indent-approver | |
| `require_indent_upload_access` | admin, kepala-cabang, manager, owner, indent-approver | |
| *(tidak ada)* | — | `mutasi`, `wa_blast` — hanya butuh login, role-check di service |

---

## 🔐 Auth & Profil — `/api/auth/*` (auth-service)

| Method | Path | Akses |
|---|---|---|
| POST | `/auth/login` | publik (rate-limited) |
| POST | `/auth/refresh` | publik |
| POST | `/auth/logout` | publik |
| POST | `/auth/forgot-password` | publik |
| POST | `/auth/reset-password` | publik |
| POST | `/auth/verify-email` | publik |
| POST | `/auth/change-password` | login |
| GET / PATCH | `/auth/profile` | login |
| POST | `/auth/users` | login (buat akun — RBAC lanjut di service) |
| GET | `/auth/login-anomalies` | `require_login_anomaly_access` |
| POST | `/auth/users/{id}/revoke-sessions` | `require_login_anomaly_access` |

## 🛒 Katalog & konten publik (tanpa token) — catalog-service

| Method | Path |
|---|---|
| GET | `/catalogs` (`?page=&limit=≤100&category=&status=&search=&sort=`) |
| GET | `/catalogs/{id}` |
| GET | `/product-categories` |
| GET | `/landing/home` |
| GET | `/partners` |

Mutasi tulis (create/update/delete/restore/sync-sql-server) katalog dipindah ke §Admin — semua
`require_admin`, bukan lagi digabung dengan GET publik.

## 📦 Inventory — inventory-service

| Method | Path | Akses |
|---|---|---|
| GET | `/inventory/barang` | publik |
| GET | `/inventory/stok-cabang` | publik |
| GET | `/inventory/stok-cabang/detail` | login (per-item serial number) |
| GET | `/inventory/product-price-search` | login |
| GET | `/inventory/barang/categories` | login |
| ANY | `/inventory/mutasi`, `/inventory/mutasi/{*rest}` | login (RBAC di service: admin cabang ajukan/terima, manager approve) — body limit 8MB |
| GET | `/inventory/stock-summary`, `/inventory/aging-stock`, `/inventory/stock-audit` | `require_admin` |
| GET / PUT | `/inventory/indent/notify-settings` | `require_admin` |
| GET / POST | `/inventory/indent` | `require_indent_submitter` |
| POST | `/inventory/indent/upload-proof` | `require_indent_upload_access` (body limit 6MB) |
| ANY | `/inventory/indent/{*rest}` | `require_indent_item_access` (termasuk `/bukti/{filename}`, body limit 6MB) |

## 📊 Dashboard eksekutif & Finance — `require_finance_access` (inventory + finance-service)

| Method | Path |
|---|---|
| GET | `/inventory/executive/kpi` |
| GET | `/inventory/executive/sparkline` |
| GET | `/inventory/executive/monthly-target` |
| GET | `/inventory/executive/monthly-chart` |
| GET | `/inventory/executive/finco` |
| GET | `/inventory/branch-performance` (4 tanggal wajib) |
| GET | `/inventory/sales-performance` (4 tanggal + `kodeJabatan?`) |
| GET | `/inventory/size-split`, `/inventory/sales-by-category` |
| GET | `/inventory/top-products-by-branch`, `/inventory/product-strength-by-sales`, `/inventory/product-branch-mismatch` |
| GET | `/inventory/top-selling` |
| GET | `/finance/summary` (`?periode=YYYY-MM`) |
| GET | `/finance/omset` (`?periode=&cabangId?`) |
| GET / PUT | `/finance/targets`, `/finance/automation` |
| POST | `/finance/automation/test`, `/finance/sync` |
| GET | `/owner/sales-transactions`, `/owner/sales-broker-comparison`, `/owner/broker-fee`, `/owner/employee-anomalies`, `/owner/employee-detail`, `/owner/recent-transactions`, `/owner/branch-sales-transactions` | ikut guard finance (bukan owner-only) karena dipakai dashboard/detail manager |

### Facade mobile — `/api/mobile/v1/leaderboards/*` (`require_finance_access`, sama seperti di atas)

| Method | Path | Proxy ke |
|---|---|---|
| GET | `/mobile/v1/leaderboards/sales` | `owner/sales-report` (sama persis dgn web, formula konsisten) |
| GET | `/mobile/v1/leaderboards/branches` | `owner/sales-report` (handler identik dgn `sales`) |

> Request pending: alias `/api/sales/{kpi,monthly-target,sales-leaderboard,branch-leaderboard}` —
> lihat `docs/superpowers/specs/2026-07-13-mobile-api-namespace-audit.md` di repo backend.

## 👔 Owner reports — `require_owner_access` (owner, admin) — inventory-service

| Method | Path |
|---|---|
| GET | `/owner/sales-report` · POST `/owner/sales-report/sync` |
| GET | `/owner/realtime-branch-transactions` |
| GET | `/owner/resigned-sales-yearly` |
| GET | `/owner/stock-audit` · POST `/owner/stock-audit/sync` |

## 🤝 CRM pipeline — `require_crm_access` (admin, crm-manager, karyawan, manager) — crm-service

Scoping per-lead (karyawan cuma lihat lead sendiri) ditegakkan di crm-service, bukan gateway.

| Method | Path |
|---|---|
| ANY | `/crm/{*rest}` — semua sub-path (`leads`, `pipelines`, `dashboard`, `analytics`, `reports`, `tasks`, dll) lewat satu wildcard |

### Facade mobile — `/api/mobile/v1/crm/*` (guard identik dgn di atas)

| Method | Path | Proxy ke |
|---|---|---|
| GET | `/mobile/v1/crm/summary` | `crm/dashboard` |
| GET | `/mobile/v1/crm/analytics` | `crm/analytics` |
| GET | `/mobile/v1/crm/pipelines` | `crm/pipelines` |
| GET | `/mobile/v1/crm/leads` | `crm/leads` |

## 🧑‍💼 Manager tools — `require_manager_access` (manager, kepala-cabang, admin)

| Method | Path |
|---|---|
| GET / POST | `/manager/sales-role-overrides` · DELETE `/manager/sales-role-overrides/{nama}` |
| POST | `/manager/branch-insight` (AI insight) · `/manager/branch-solution` |

## 🧑‍💻 Users — admin

| Method | Path | Guard |
|---|---|---|
| GET | `/users`, `/users/page-registry`, `/users/login-geo`, `/users/{id}/login-activity` | `require_users_read_access` (admin, manager, crm-manager) |
| GET | `/owner/erp-pegawai-cabang` | `require_users_read_access` (path `owner/*` tapi digabung grup ini) |
| POST | `/users` | `require_user_admin_access` (admin, manager) |
| PATCH / DELETE | `/users/{id}` · POST `/users/{id}/restore` · POST `/users/{id}/reset-password` · DELETE `/users/{id}/permanent` | `require_user_admin_access` |
| POST | `/users/profiles` | login saja |

## 📈 Kinerja (prospek/raport/off/KPI) — login saja (kinerja-service, RBAC di service)

| Method | Path |
|---|---|
| ANY | `/prospek-harian`, `/prospek-harian/{*rest}` |
| ANY | `/raport-harian`, `/raport-harian/{*rest}` (body limit 32MB) |
| ANY | `/off-requests`, `/off-requests/{*rest}` |
| ANY | `/jobdesk-report-settings`, `/jobdesk-divisions` |
| ANY | `/sales/delivery-schedules` |
| ANY | `/kpi/{*rest}` |
| ANY | `/admin/prospect-report/{*rest}` (body limit 12MB) |
| GET | `/raport-files/{*file}` | `require_raport_file_access` |

## 🔔 Notifikasi — login saja (audit-service)

| Method | Path |
|---|---|
| GET / POST / DELETE | `/notifications` |
| GET | `/notifications/unread-count` |
| PATCH | `/notifications/read-all`, `/notifications/{id}/read` |

## 🧑‍🎓 HRD rekrutmen — `require_hrd_access` (hrd, admin, owner) + lamaran publik

| Method | Path | Akses |
|---|---|---|
| ANY | `/hrd/{*rest}` | `require_hrd_access` (hrd-service tak punya role-gate sendiri, percaya gateway) |
| GET | `/admin/career-files/{*file}` | `require_admin` (dokumen CV/SKCK/foto — PII, tidak pernah publik) |
| GET / POST | `/jobs` · PATCH/DELETE `/jobs/{id}` | publik/optional (`careers`) |
| GET / POST | `/job-applications` (rate-limit 10/jam/IP) · PATCH `/job-applications/{id}/status` | publik/optional |
| POST | `/job-applications/upload` (rate-limit 30/jam/IP, body limit 12MB) | publik/optional |
| POST | `/public/hrd/ingest/{source_id}` | publik (auth via token sumber webhook, bukan JWT; rate-limit 60/mnt/IP, body limit 64KB) |

## 🛍️ Marketplace (toko online) — marketplace-service

| Method | Path | Akses |
|---|---|---|
| ANY | `/public/marketplace/{*rest}` | publik |
| ANY | `/admin/marketplace/{*rest}` | `require_marketplace_admin` (admin, operator) |

## 💬 WhatsApp ops — login saja (RBAC per-role di service via `x-user-role`) — wa-service

| Method | Path | Guard |
|---|---|---|
| ANY | `/wa/campaigns`, `/wa/campaigns/{*rest}` | login saja (body limit 17MB) |
| ANY | `/wa/blast-contacts`, `/wa/blast-contacts/{*rest}` | login saja |
| ANY | `/wa/recipients/{*rest}` | login saja |
| ANY | `/v1/wa/templates`, `/v1/wa/templates/{*rest}` | login saja |
| ANY | `/v1/wa/pool/summary`, `/v1/wa/send-template` | login saja |
| GET / POST | `/wa/accounts` · PATCH/DELETE `/wa/accounts/{id}` | login saja (grup `protected` dasar) |
| POST | `/v1/wa/sessions/{id}/connect` · `/disconnect` | login saja |
| GET | `/v1/wa/sessions/{id}/qr` · `/groups` | login saja |

## 📣 Ads / Pixel / Campaign — login saja (ads-service)

| Method | Path |
|---|---|
| ANY | `/pixels`, `/pixels/{*rest}` |
| ANY | `/campaigns`, `/campaigns/{*rest}` |
| POST | `/pixel-events/test` |
| ANY | `/pixel-analytics/{*rest}` |
| POST | `/pixel-events` | publik (tracking pixel ingest, rate-limit 100/mnt/IP, body limit 64KB) |

## 🤖 AI / Agent / Knowledge

| Method | Path | Guard |
|---|---|---|
| ANY | `/admin/ai-employees`, `/admin/ai-employees/{*rest}`, `/admin/ai-agent/{*rest}`, `/admin/home-service-report/{*rest}`, `/admin/ads-managers`, `/admin/ads-managers/{*rest}`, `/admin/leads/{*rest}`, `/admin/ai-scan/{*rest}` | `require_admin` |
| POST / GET / DELETE | `/admin/knowledge/{sync,stats,sync-logs,settings,chat,chat-stream,chat-history}` | `require_admin` |
| ANY | `/admin/sop-ai/{*rest}` | `require_sop_ai_access` (admin, owner, ai-engineer — prefix terpisah biar ai-engineer tidak otomatis dapat akses `/admin/ai-agent/*` lain) |
| GET | `/agent/stats`, `/leaderboard`, `/reward-tiers` | login saja |
| GET / POST | `/agent/claims` | login saja |
| GET | `/public/referrals/{slug}` | publik (rate-limit 120/mnt/IP) |

## ⚙️ Admin lain (katalog, cabang, landing, harga)

Semua `require_admin` kecuali disebutkan lain.

| Domain | Endpoint |
|---|---|
| Katalog (tulis) | POST `/catalogs` · PATCH/DELETE `/catalogs/{id}` · POST `/catalogs/{id}/restore` · POST `/catalogs/sync-sql-server` |
| Katalog admin | GET `/admin/catalogs/{id}` · GET `/admin/catalogs/paginated` (login saja) · POST `/admin/catalogs/bulk` · GET/POST `/admin/catalogs/price-markups` · DELETE `/admin/catalogs/price-markups/{id}` |
| Kategori | GET/POST `/admin/categories` (login saja) · PATCH/DELETE `/admin/categories/{id}` (login saja) · POST `/admin/products/{kode_barang}/category` (login saja) |
| Cabang | GET/POST `/admin/cabang` (login saja) · PATCH/DELETE `/admin/cabang/{id}` (login saja) |
| Landing | GET/POST `/admin/landing/slides` (login saja) · PATCH `/admin/landing/slides/order` · PATCH/DELETE `/admin/landing/slides/{id}` · POST `/admin/landing/slides/upload` |
| Partner | GET/POST `/admin/partners` (login saja) · PATCH `/admin/partners/order` · PATCH/DELETE `/admin/partners/{id}` (body limit 12MB, logo multipart) |
| Harga | GET `/price-changes` (login saja) · POST `/price-changes/notify` (login saja) |
| Halaman produk custom | PATCH `/admin/custom-product-pages/assignment` (login saja) |
| Sistem | POST `/admin/uploads/image` (login saja) · GET `/admin/system/data-mode` |
| Upload gambar katalog | POST `/admin/uploads/image` |
| Inventory admin | GET `/inventory/stock-summary`, `/inventory/aging-stock`, `/inventory/stock-audit` · GET/PUT `/inventory/indent/notify-settings` |
| PIC Raport | ANY `/pic-raport/{*rest}` (login saja) |

## 🌐 Publik / webhook / statis — tanpa token

| Method | Path | Catatan |
|---|---|---|
| GET | `/health` | gateway sendiri |
| GET | `/public/services/status` | termasuk flag `maintenanceMode`/`staffMaintenanceMode` |
| POST | `/xendit/webhooks/payment-requests` | webhook payment gateway |
| GET | `/uploads/landing/{*file}`, `/uploads/catalog/{*file}`, `/uploads/wa/{*file}` | `wa` sengaja publik — satu-satunya penulis adalah gambar blast keluar (dipilih admin/operator), bukan media masuk dari customer |

---

## Catatan arsitektur

- **Gateway = satu-satunya pintu publik.** Semua di atas lewat `gateway/src/lib.rs` (strip
  identity header → global rate limit → auth → role guard per grup → proxy ke service upstream).
  Route bertanda wildcard `{*rest}` diteruskan apa adanya ke service; sub-path konkretnya sudah
  dibuka di tabel di atas kalau diketahui.
- **Beberapa grup protected sengaja TANPA guard role di gateway** (`mutasi`, `wa_blast`,
  `off-requests`/`raport-harian`/`prospek-harian` dkk di grup `protected` dasar) — cuma butuh JWT
  valid, RBAC detailnya ditegakkan di service masing-masing lewat header `x-user-role`. Jangan
  asumsikan "tidak ada guard di gateway" = "semua role boleh apa saja" — cek service kalau perlu
  detail persis.
- **Tidak publik (jangan panggil dari app):** tiap service punya `GET /health` sendiri dan
  beberapa `POST /internal/*` (mis. `wa-service` & `ai-service`: `/internal/ads-lead/incoming`,
  `/internal/chatbot/incoming`) untuk komunikasi antar-service — tidak diekspos gateway.
- **Riwayat keamanan**: foto bukti mutasi (`/uploads/indent`) dan bukti pembayaran indent sengaja
  **dipindah dari router publik** ke belakang auth (grup `mutasi`/`indent`) sebagai bagian remediasi
  keamanan (paket SEC-02) — jangan kembalikan ke publik tanpa alasan kuat.
- **Yang dipakai app mobile saat ini** (`tridjayaapp`): auth (8 endpoint), inventory/stok-cabang,
  crm/leads (7 endpoint), executive/kpi + monthly-target + leaderboards (via `SalesApi`),
  owner/sales-transactions + branch-sales-transactions. Detail lengkap + gap per domain ada di
  `docs/api-requirements.md` (repo ini).

_Digenerate dari ekstraksi kode backend (`gateway/src/lib.rs`, 186 route / 23 grup guard,
2026-07-13). Regenerasi bila route backend berubah._

# Tridjaya Backend — Referensi Endpoint Lengkap

> Diekstrak langsung dari kode `gateway/src/lib.rs` + tiap `services/*/src` (repo backend
> `C:\laragon\www\tridjaya`, Axum 0.8). **164 route publik** lewat gateway, dari 14 microservice.
> Wildcard `{*rest}` sudah dibuka dari service pemiliknya.
>
> **Sumber kebenaran untuk detail request/response (subset app):** `docs/api/android-api.md` di
> repo backend (terverifikasi live per 2026-07-08), plus `docs/api/auth.md`. Dokumen ini adalah
> *peta permukaan* seluruh backend — untuk bentuk JSON, cek doc itu atau kode service.

## Dasar

| Hal | Nilai |
|---|---|
| Base URL produksi | `https://tridjayaelektronik.tech/api` |
| Protokol | HTTPS (Let's Encrypt), HTTP/1.1, JSON |
| Auth | `Authorization: Bearer <access_token>` (JWT 15 mnt); refresh token dirotasi |
| Konvensi field | `camelCase` (kecuali respons login/refresh: `snake_case`) |
| Envelope sukses | `{ "message": "...", "data": { } }` |
| Envelope gagal | `{ "code": "...", "message": "...", "errors": [ ] }` |
| Rate limit | login 10/5mnt · global 600/mnt |
| Catatan | `GET /catalogs` cold start ±51 dtk (snapshot 70k produk) → timeout ≥60 dtk |

Kode error: `400 validation_error` · `401 unauthorized` (→ refresh) · `403 forbidden` ·
`404 not_found` · `409 conflict` · `429` (backoff) · `502 gateway_error` (retry).

Role: `admin` · `owner` · `manager` · `karyawan` · `crm-manager` · `pic_raport` · `operator` ·
`ads-manager` · `agent`.

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
| GET | `/auth/login-anomalies` | admin/owner |
| POST | `/auth/users` | admin (buat akun login) |
| POST | `/auth/users/{id}/revoke-sessions` | admin |

## 🛒 Katalog & konten publik (tanpa token) — catalog-service

| Method | Path |
|---|---|
| GET | `/catalogs` (`?page=&limit=≤100&category=&status=&search=&sort=`) |
| POST | `/catalogs` |
| GET / PATCH / DELETE | `/catalogs/{id}` |
| POST | `/catalogs/{id}/restore` |
| POST | `/catalogs/sync-sql-server` |
| GET | `/product-categories` |
| GET | `/landing/home` |
| GET | `/partners` |

## 📦 Inventory — inventory-service

| Method | Path | Akses |
|---|---|---|
| GET | `/inventory/barang` | publik |
| GET | `/inventory/stok-cabang` | publik |
| GET | `/inventory/stock-audit` | manager/admin/owner |
| GET | `/inventory/stock-summary` | admin |
| GET | `/inventory/top-selling` | admin |
| GET | `/inventory/size-split` | manager/admin/owner |
| GET | `/inventory/sales-by-category` | manager/admin/owner |
| GET / POST | `/inventory/mutasi` | login |
| GET | `/inventory/mutasi/context` | login |
| POST | `/inventory/mutasi/upload-photo` | login |
| GET | `/inventory/mutasi/{id}` | login |
| POST | `/inventory/mutasi/{id}/cancel` · `/receive` · `/pic-review` | login |

## 📊 Dashboard eksekutif & Finance — manager/admin/owner (inventory + finance-service)

| Method | Path |
|---|---|
| GET | `/inventory/executive/kpi` |
| GET | `/inventory/executive/sparkline` |
| GET | `/inventory/executive/monthly-target` |
| GET | `/inventory/executive/monthly-chart` |
| GET | `/inventory/executive/finco` (`?month=&year=`) |
| GET | `/inventory/branch-performance` (4 tanggal wajib) |
| GET | `/inventory/sales-performance` (4 tanggal + `kodeJabatan?`) |
| GET | `/finance/summary` (`?periode=YYYY-MM`) |
| GET | `/finance/omset` (`?periode=&cabangId?`) |
| GET / PUT | `/finance/targets` |
| GET / PUT | `/finance/automation` |
| POST | `/finance/automation/test` |
| POST | `/finance/sync` |

## 👔 Owner reports — manager/admin/owner (inventory-service)

| Method | Path |
|---|---|
| GET | `/owner/sales-report` · POST `/owner/sales-report/sync` |
| GET | `/owner/sales-transactions` |
| GET | `/owner/branch-sales-transactions` |
| GET | `/owner/realtime-branch-transactions` |
| GET | `/owner/sales-broker-comparison` |
| GET | `/owner/broker-fee` |
| GET | `/owner/employee-anomalies` |
| GET | `/owner/employee-detail` (`?kode=`) |
| GET | `/owner/resigned-sales-yearly` |
| GET | `/owner/stock-audit` · POST `/owner/stock-audit/sync` |

## 🤝 CRM pipeline — `/api/crm/*` (crm-service)

Akses: `crm-manager`/`admin` penuh · `karyawan` hanya lead milik sendiri · `manager`/`kepala-cabang` **tidak**.

| Method | Path |
|---|---|
| GET / POST | `/crm/leads` |
| GET / PATCH | `/crm/leads/{id}` |
| POST | `/crm/leads/{id}/move-stage` |
| POST | `/crm/leads/{id}/won` · `/lost` · `/reopen` |
| POST | `/crm/leads/{id}/activities` |
| GET | `/crm/leads/check-phone` (`?phone=`) |
| GET | `/crm/pipelines` |
| GET | `/crm/dashboard` · `/crm/analytics` · `/crm/reports` · `/crm/insight` |
| GET | `/crm/lost-reasons` |
| GET / POST | `/crm/tasks` |
| POST | `/crm/tasks/{id}/done` · `/cancel` · `/reopen` |

## 📈 Kinerja (prospek/raport/KPI/off) — kinerja-service

| Method | Path |
|---|---|
| GET / POST | `/prospek-harian` |
| GET | `/prospek-harian/summary` |
| PATCH / DELETE | `/prospek-harian/{id}` |
| GET / POST | `/raport-harian` |
| POST | `/raport-harian/upload` |
| PATCH | `/raport-harian/{id}/review` |
| GET / POST | `/off-requests` |
| PATCH | `/off-requests/{id}/review` |
| GET | `/kpi/me` · `/kpi/karyawan` · `/kpi/karyawan/{id}` · `/kpi/positions` |
| PUT | `/kpi/actuals` · `/kpi/assignments` |
| GET / PATCH | `/jobdesk-divisions` · `/jobdesk-report-settings` |
| GET / POST | `/sales/delivery-schedules` |

## 🔔 Notifikasi — semua login (audit-service)

| Method | Path |
|---|---|
| GET / POST / DELETE | `/notifications` |
| GET | `/notifications/unread-count` |
| PATCH | `/notifications/read-all` |
| PATCH | `/notifications/{id}/read` |

## 🧑‍💼 HRD rekrutmen — `/api/hrd/*` (hrd-service) + lamaran publik (catalog-service)

| Method | Path |
|---|---|
| GET / POST | `/hrd/candidates` |
| GET / PATCH | `/hrd/candidates/{id}` |
| POST | `/hrd/candidates/{id}/activities` · `/move-status` |
| GET | `/hrd/dashboard` |
| GET / POST | `/hrd/sources` |
| PATCH | `/hrd/sources/{id}` · POST `/hrd/sources/{id}/rotate-token` |
| GET / POST | `/job-applications` · POST `/job-applications/upload` |
| PATCH | `/job-applications/{id}/status` |
| GET / POST | `/jobs` · PATCH/DELETE `/jobs/{id}` |
| POST | `/public/hrd/ingest/{source_id}` (publik, token sumber) |

## 🛍️ Marketplace (toko online) — marketplace-service

Customer/publik:

| Method | Path |
|---|---|
| POST | `/marketplace/auth/request-otp` · `/marketplace/auth/verify` |
| GET / PUT / DELETE | `/marketplace/cart` |
| POST | `/marketplace/cart/items` · DELETE `/marketplace/cart/items/{product_id}` |
| POST | `/marketplace/checkout` |
| GET | `/marketplace/customer/dashboard` · `/customer/orders` · `/customer/orders/{id}` |
| POST | `/marketplace/customer/orders/{id}/simulate-payment` |
| GET | `/marketplace/orders/{order_code}` · POST `/reviews` · `/simulate-payment` |
| GET | `/marketplace/session` · `/marketplace/pixel-config` |

Admin (`/api/admin/marketplace/*`): orders · orders/{id}/status · reviews · customer-service · whatsapp-pool + reminder-settings.

## 💬 WhatsApp ops — `/api/wa/*`, `/api/v1/wa/*` (wa-service)

Sessions & templates:

| Method | Path |
|---|---|
| POST | `/v1/wa/sessions/{id}/connect` · `/disconnect` |
| GET | `/v1/wa/sessions/{id}/qr` · `/groups` |
| GET | `/v1/wa/pool/summary` |
| POST | `/v1/wa/send-template` |
| GET / POST | `/v1/wa/templates` · GET/PATCH/DELETE `/v1/wa/templates/{id}` |

Accounts, blast & campaigns:

| Method | Path |
|---|---|
| GET / POST | `/wa/accounts` · PATCH/DELETE `/wa/accounts/{id}` |
| GET / POST | `/wa/blast-contacts` · PATCH/DELETE `/wa/blast-contacts/{id}` |
| — | `/wa/blast-contacts/{upload-excel,export-vcf,stats,bulk-status,bulk-status-by-phone,import-to-campaign/{id}}` |
| GET / POST | `/wa/campaigns` · GET/PATCH/DELETE `/wa/campaigns/{id}` |
| POST | `/wa/campaigns/{id}/{start,pause,reset,recipients,recipients/upload-excel,upload-image}` |
| GET | `/wa/campaigns/{id}/{status,metrics}` |
| GET | `/wa/recipients/template` · PATCH/DELETE `/wa/recipients/{id}` |
| POST | `/wa/send` · `/wa/send-media` |

## 📣 Ads / Pixel / Campaign — ads-service

| Method | Path |
|---|---|
| GET / POST | `/campaigns` · GET/PATCH/DELETE `/campaigns/{id}` |
| GET / POST | `/campaigns/{id}/conversions` · PATCH/DELETE `/campaigns/{id}/conversions/{conversion_id}` |
| GET / POST | `/pixels` · GET/PATCH/DELETE `/pixels/{id}` |
| POST | `/pixel-events` · `/pixel-events/test` |
| GET | `/pixel-analytics/admin` · `/pixel-analytics/sales` |

## 🤖 AI / Agent / Knowledge — ai-service + agent-service (admin)

AI-service (`/api/admin/*`):

- `/admin/ai-agent/{automation,automation/test,chatbot-settings,intent-analytics,llm/models,llm/test}`
- `/admin/ai-employees` · `/admin/ai-employees/{id}` · `/{id}/chat-history`
- `/admin/ai-scan/groups`
- `/admin/ads-managers` · `/{id}` · `/accounts` · `/leads` · `/leads/scan` · `/leads/{id}/{chat-history,forward}`
- `/admin/sop-ai/{sop_id}/run` · `/runs` · `/runs/{run_id}` · `/runs/{run_id}/review`
- `/admin/home-service-report/{automation,automation/test,sync-sheet}`
- `/admin/leads/pipeline` · `/admin/leads/{source}/{id}/status`
- `/admin/knowledge/{chat,chat-stream,chat-history,settings,stats,sync,sync-logs}`
- `POST /manager/branch-insight` · `/manager/branch-solution`

Agent/gamifikasi (agent-service): `GET/POST /agent/claims` · `GET /agent/stats` · `/leaderboard` ·
`/reward-tiers` · `GET /public/referrals/{slug}`

## ⚙️ Admin lain

| Domain | Endpoint |
|---|---|
| Users (user/auth-service) | `GET/POST /users` · `PATCH/DELETE /users/{id}` · `/restore` · `/reset-password` · `POST /users/profiles` |
| Cabang (catalog) | `GET/POST /admin/cabang` · `PATCH/DELETE /admin/cabang/{id}` |
| Katalog admin (catalog) | `/admin/catalogs/{paginated,bulk,{id}}` · `/admin/catalogs/price-markups` · `/admin/categories` · `/admin/partners` · `/admin/custom-product-pages/assignment` · `POST /admin/products/{kode_barang}/category` |
| Landing (catalog) | `/admin/landing/slides` · `/order` · `/upload` · `/{id}` |
| Harga (catalog) | `GET /price-changes` · `POST /price-changes/notify` |
| Kinerja admin | `/admin/prospect-report/{automation,off-days,off-days/manual,off-days/status,off-days/upload}` |
| Sistem | `POST /admin/uploads/image` · `GET /admin/system/data-mode` |
| Manager | `GET/POST /manager/sales-role-overrides` · `DELETE /manager/sales-role-overrides/{nama}` · `/manager/branch-insight` · `/branch-solution` |
| PIC Raport (auth) | `/api/pic-raport/{cabang,karyawan,karyawan/{id},...}` |

## 🌐 Publik / webhook / statis

| Method | Path |
|---|---|
| GET | `/health` (gateway) · `/public/services/status` |
| POST | `/xendit/webhooks/payment-requests` |
| POST | `/pixel-events` (publik, tracking) |
| GET | `/uploads/{catalog,landing,careers,mutasi,raport,wa}/*` (file statis; prefix `https://tridjayaelektronik.tech`) |

---

## Catatan arsitektur

- **Gateway = satu-satunya pintu publik.** Semua di atas lewat `gateway/src/lib.rs` (auth,
  rate-limit, role-gate, lalu proxy ke service upstream). Route bertanda wildcard `{*rest}` di
  gateway diteruskan apa adanya ke service; sub-path konkretnya sudah dibuka di tabel di atas.
- **Tidak publik (jangan panggil dari app):** tiap service punya `GET /health` sendiri dan
  beberapa `POST /internal/*` (mis. `wa-service` & `ai-service`: `/internal/ads-lead/incoming`,
  `/internal/chatbot/incoming`) untuk komunikasi antar-service — tidak diekspos gateway.
- **Yang dipakai app saat ini:** auth, inventory (barang/stok-cabang/executive/branch+sales-
  performance), crm/leads, finance/kinerja. Detail bentuk JSON-nya ada di
  backend `docs/api/android-api.md`.

_Digenerate dari ekstraksi kode backend. Regenerasi bila route backend berubah._

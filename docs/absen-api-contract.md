# Kontrak API Absen (Kehadiran) — Backend Nyata

**Status:** ✅ **Diimplementasi di backend** — `tridjaya` repo, branch `feat/absensi-karyawan`,
`services/kinerja-service/src/absensi.rs` + `absensi_upload.rs`. App sudah di-wire ke sini
(`data/remote/AbsensiApi.kt` + `data/AbsensiRepository.kt` + `ui/attendance/`).

Gateway route: `/api/absensi` + `/api/absensi/{*rest}` → kinerja-service (body limit 8 MB untuk
`upload-photo`). Envelope standar `ApiResponse { message, data }`.

## Model punch
Check-in (masuk) + check-out (pulang), sekali per hari (UNIQUE `karyawan_id + tanggal`). Tiap punch
membawa **GPS + selfie**. Jarak geofence (Haversine), status telat/pulang-cepat, dan
valid/pending_review **dihitung server** dari config cabang — app hanya kirim `lat/lng/photoUrl`.

- **Selfie wajib.** Di-upload dulu (multipart) → dapat URL relatif → dikirim sebagai `photoUrl`.
- **Di luar geofence** → punch tetap tercatat tapi `status = pending_review` (wajib approve
  admin/kepala cabang). **Di dalam** → `status = valid`.

## Endpoint (dipakai app)
Semua protected (JWT), `karyawan_id` diambil dari token. Role staff = self-service.

| Method | Path | Body (camelCase) | Data response |
|---|---|---|---|
| `GET` | `/api/absensi/today` | — | `{ tanggal, record: AbsensiRecord? }` |
| `POST` | `/api/absensi/check-in` | `{ lat, lng, photoUrl }` | `AbsensiRecord` (201) |
| `POST` | `/api/absensi/check-out` | `{ lat, lng, photoUrl }` | `AbsensiRecord` |
| `GET` | `/api/absensi?page&limit&tanggalFrom&tanggalTo` | — | `{ items:[AbsensiRecord], page, limit, total, totalPages }` |
| `POST` | `/api/absensi/upload-photo` | multipart field `file` (JPEG/PNG/WebP ≤5 MB) | `{ url: "/uploads/absensi/{uuid}.jpg" }` |

Endpoint lain (belum dipakai app): `PATCH /api/absensi/review/{id}` `{status: approved|rejected, comment}`
(reviewer), `GET/PUT /api/absensi/config[/{cabangId}]` (admin — set titik geofence + jam kerja).
Foto di-serve authenticated di `GET /api/absensi/photo/{filename}` (bukan publik).

## AbsensiRecord (camelCase)
`id, karyawanId, karyawanNama, cabangId, cabangNama, divisi, tanggal,
checkInAt, checkInLat, checkInLng, checkInPhotoUrl, checkInDistanceM, checkInInGeofence, checkInLate,
checkOutAt, checkOutLat, checkOutLng, checkOutPhotoUrl, checkOutDistanceM, checkOutInGeofence, checkOutEarly,
status (valid|pending_review|approved|rejected), reviewerId, reviewerNama, reviewerComment, reviewedAt,
createdAt, updatedAt`

DTO app: `data/model/AttendanceModels.kt` (`AbsensiRecordDto` dst) — sudah 1:1.

## Catatan integrasi app
- Selfie: capture full-res (`TakePicture` + FileProvider) → kompres ≤2 MB/1600px
  (`AttendanceViewModel.compress`) → `AbsensiRepository.uploadPhoto` → `checkIn/out`.
- App **tidak** menghitung geofence (config cabang admin-only) — verdict tampil dari record hasil.
- **Prasyarat data**: tiap cabang perlu di-set geofence via `PUT /api/absensi/config/{cabangId}`
  (lat/lng/radius/jam masuk/pulang). Tanpa config → jarak null, tak pernah di-flag telat/luar-area.

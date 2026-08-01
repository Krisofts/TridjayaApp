package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/**
 * Body `POST /api/absensi/register-device` — daftarkan FCM token milik user login.
 *
 * [abi], [model], dan [sdkInt] = telemetri perangkat (migrasi backend 130). Sampai
 * ini ada, tak ada satu pun tempat yang mencatat HP apa yang beredar: `platform`
 * selalu "android", auth cuma menyimpan User-Agent ter-hash, dan seluruh trafik
 * app ber-UA `okhttp/4.12.0` — versi PUSTAKA, bukan versi app.
 *
 * Ketiganya nullable dengan default null dan `explicitNulls = false` di
 * `NetworkModule`, jadi kalau suatu saat tak terisi ia tak dikirim sama sekali,
 * bukan dikirim sebagai null. Server memperlakukan "tak dikirim" = kolom NULL.
 */
@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android",
    val abi: String? = null,
    val model: String? = null,
    val sdkInt: Int? = null
)

/** Response register device (data `{}`), tak dipakai isinya — cukup cek sukses. */
@Serializable
class DeviceAckDto

package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.Serializable

/** Body `POST /api/absensi/register-device` — daftarkan FCM token milik user login. */
@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android"
)

/** Response register device (data `{}`), tak dipakai isinya — cukup cek sukses. */
@Serializable
class DeviceAckDto

package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.DeviceAckDto
import com.krisoft.tridjayaelektronik.data.model.RegisterDeviceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/** Daftar FCM device token — kinerja-service via gateway `/api/absensi/register-device`. */
interface DeviceApi {
    @POST("api/absensi/register-device")
    suspend fun register(@Body body: RegisterDeviceRequest): Response<ApiResponse<DeviceAckDto>>
}

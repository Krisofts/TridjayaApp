package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.NotificationDto
import com.krisoft.tridjayaelektronik.data.remote.NotificationsApi
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Notifikasi in-app — langsung ke `audit-service` via [NotificationsApi]. Tanpa cache lokal. */
@Singleton
class NotificationsRepository @Inject constructor(
    private val api: NotificationsApi
) {
    private val errorJson = Json { ignoreUnknownKeys = true }

    suspend fun list(): AuthResult<Pair<List<NotificationDto>, Int>> = try {
        val response = api.list()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.items to data.unreadCount)
        else parseError(response, "Gagal memuat notifikasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun unreadCount(): AuthResult<Int> = try {
        val response = api.unreadCount()
        val data = response.body()?.data
        if (response.isSuccessful && data != null) AuthResult.Success(data.unreadCount)
        else parseError(response, "Gagal memuat jumlah notifikasi")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun markRead(id: String): AuthResult<Unit> = try {
        val response = api.markRead(id)
        if (response.isSuccessful) AuthResult.Success(Unit) else parseError(response, "Gagal menandai dibaca")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    suspend fun markAllRead(): AuthResult<Unit> = try {
        val response = api.markAllRead()
        if (response.isSuccessful) AuthResult.Success(Unit) else parseError(response, "Gagal menandai semua dibaca")
    } catch (e: Exception) {
        AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
    }

    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            parsed?.message ?: "$fallback (${response.code()})"
        )
    }
}

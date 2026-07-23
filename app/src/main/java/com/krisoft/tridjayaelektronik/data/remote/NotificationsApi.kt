package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.MarkReadData
import com.krisoft.tridjayaelektronik.data.model.NotificationListData
import com.krisoft.tridjayaelektronik.data.model.UnreadCountData
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

/**
 * Notifikasi in-app — semua login, audit-service via gateway `/api/notifications` (android-api.md §7).
 * Mark-read/read-all adalah `PATCH` (bukan `POST`, meski §7 di dokumen menyebutnya begitu — diverifikasi
 * langsung dari route gateway/audit-service; dokumen belum diperbarui).
 */
interface NotificationsApi {

    @GET("api/notifications")
    suspend fun list(): Response<ApiResponse<NotificationListData>>

    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): Response<ApiResponse<UnreadCountData>>

    @PATCH("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<ApiResponse<MarkReadData>>

    @PATCH("api/notifications/read-all")
    suspend fun markAllRead(): Response<ApiResponse<MarkReadData>>
}

package com.krisoft.tridjayaelektronik.data.remote

import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.ChangePasswordRequest
import com.krisoft.tridjayaelektronik.data.model.ForgotPasswordRequest
import com.krisoft.tridjayaelektronik.data.model.LoginRequest
import com.krisoft.tridjayaelektronik.data.model.LogoutRequest
import com.krisoft.tridjayaelektronik.data.model.RefreshRequest
import com.krisoft.tridjayaelektronik.data.model.ResetPasswordRequest
import com.krisoft.tridjayaelektronik.data.model.SessionData
import com.krisoft.tridjayaelektronik.data.model.UserDto
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<SessionData>>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<SessionData>>

    @POST("api/auth/logout")
    suspend fun logout(@Body request: LogoutRequest): Response<ApiResponse<Map<String, Boolean>>>

    @GET("api/auth/profile")
    suspend fun profile(): Response<ApiResponse<UserDto>>

    @PATCH("api/auth/profile")
    suspend fun updateProfile(@Body body: Map<String, String>): Response<ApiResponse<UserDto>>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<ApiResponse<JsonElement>>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<JsonElement>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<JsonElement>>
}

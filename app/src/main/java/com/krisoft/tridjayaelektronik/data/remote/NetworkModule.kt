package com.krisoft.tridjayaelektronik.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.krisoft.tridjayaelektronik.BuildConfig
import com.krisoft.tridjayaelektronik.data.TokenStore
import com.krisoft.tridjayaelektronik.data.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json as KJson

object NetworkModule {

    private val json = KJson {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private var retrofit: Retrofit? = null

    /** Builds (once) the authenticated Retrofit instance shared by every API interface. */
    private fun authenticatedRetrofit(tokenStore: TokenStore): Retrofit {
        retrofit?.let { return it }

        // A dedicated Retrofit/OkHttp instance without the auth interceptor/authenticator,
        // used only for calling /api/auth/refresh so it can never recurse into itself.
        val plainClient = baseClientBuilder().build()
        val plainAuthApi = buildRetrofit(plainClient).create(AuthApi::class.java)

        // Single refresher shared by the proactive interceptor and the 401 authenticator so only one
        // /auth/refresh ever fires per rotation (the refresh token is single-use).
        val refresher = TokenRefresher(tokenStore, plainAuthApi)

        val client = baseClientBuilder()
            .addInterceptor(AuthHeaderInterceptor(tokenStore, refresher))
            .authenticator(TokenRefreshAuthenticator(refresher))
            .build()

        return buildRetrofit(client).also { retrofit = it }
    }

    fun createAuthApi(tokenStore: TokenStore): AuthApi =
        authenticatedRetrofit(tokenStore).create(AuthApi::class.java)

    fun createInventoryApi(tokenStore: TokenStore): InventoryApi =
        authenticatedRetrofit(tokenStore).create(InventoryApi::class.java)

    fun createCrmApi(tokenStore: TokenStore): CrmApi =
        authenticatedRetrofit(tokenStore).create(CrmApi::class.java)

    private fun baseClientBuilder(): OkHttpClient.Builder {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }
}

/**
 * Owns the one-refresh-per-rotation logic. Refresh tokens are single-use/rotating on the server, so
 * concurrent 401s (or concurrent proactive refreshes) must not each fire their own `/auth/refresh`
 * with the same token — the losers would fail and wipe a session the winner just renewed. The
 * `synchronized` block plus the "did someone already rotate?" check guarantees exactly one refresh.
 *
 * [staleToken] is the access token the caller is dissatisfied with — either near-expiry (proactive)
 * or just-401'd (reactive). If the store already holds a *different* token, another thread refreshed
 * while we waited on the lock, so we reuse that instead of refreshing again.
 *
 * Returns the usable access token, or null if refresh failed (session is cleared → UI logs out).
 */
private class TokenRefresher(
    private val tokenStore: TokenStore,
    private val plainAuthApi: AuthApi
) {
    fun refresh(staleToken: String?): String? = synchronized(this) {
        val current = tokenStore.accessToken
        if (!current.isNullOrBlank() && current != staleToken) return current

        val refreshToken = tokenStore.refreshToken ?: run { tokenStore.clear(); return null }

        val response = try {
            runBlocking { plainAuthApi.refresh(RefreshRequest(refreshToken)) }
        } catch (_: Exception) {
            null // network error — leave the session intact, let the request fail/retry naturally
        }

        val body = response?.body()
        if (response?.isSuccessful != true || body == null) {
            // A genuine rejection (not a transient network error) means the refresh token is dead.
            if (response != null && !response.isSuccessful) tokenStore.clear()
            return null
        }

        tokenStore.updateTokens(body.data.accessToken, body.data.refreshToken, body.data.expiresIn)
        return body.data.accessToken
    }
}

/**
 * Attaches the Bearer header, refreshing the token *before* the request when it is within
 * [REFRESH_MARGIN_MILLIS] of expiry (proactive) so most requests never have to eat a 401 round-trip.
 */
private class AuthHeaderInterceptor(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        var token = tokenStore.accessToken
        if (!token.isNullOrBlank() && tokenStore.accessTokenExpiresWithin(REFRESH_MARGIN_MILLIS)) {
            // On failure fall through with the old token: a real 401 then triggers the authenticator.
            token = refresher.refresh(token) ?: token
        }
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(request)
    }

    private companion object {
        const val REFRESH_MARGIN_MILLIS = 60_000L // refresh ~1 min before the 15-min token expires
    }
}

/**
 * Reactive fallback: when a protected request still gets a 401 (token expired unexpectedly, or the
 * proactive refresh raced), rotate once and retry. If refresh fails the session is cleared.
 */
private class TokenRefreshAuthenticator(
    private val refresher: TokenRefresher
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = refresher.refresh(failedToken) ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}

package com.krisoft.tridjayaelektronik.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.krisoft.tridjayaelektronik.data.model.SessionData
import com.krisoft.tridjayaelektronik.data.model.UserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists the session (access/refresh tokens + cached profile) in an **encrypted DataStore**
 * (Android Keystore AES-GCM, see [SessionCrypto]) — it survives process death but never sits on
 * disk in plaintext.
 *
 * The public read surface is intentionally **synchronous** because OkHttp's auth interceptor and
 * authenticator run on background threads that cannot suspend. We keep an in-memory [cache] mirror
 * of the DataStore that those callers read instantly; every write updates the mirror synchronously
 * and persists to the DataStore asynchronously.
 */
class TokenStore(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dataStore: DataStore<PersistedSession> = DataStoreFactory.create(
        serializer = SessionSerializer,
        scope = scope,
        produceFile = { context.dataStoreFile(DATASTORE_FILE) }
    )

    // Synchronous mirror for OkHttp-thread consumers. @Volatile so writes are visible across threads.
    @Volatile private var cache = PersistedSession()
    @Volatile private var loaded = false
    private val loadMutex = Mutex()

    // Reactive login flag — flips false on logout or when a background refresh fails, so the nav
    // gate can react from anywhere without a per-screen callback.
    private val _sessionState = MutableStateFlow(false)
    val sessionState: StateFlow<Boolean> = _sessionState.asStateFlow()

    // Reactive forced-password-change flag so the nav gate can block/release live.
    private val _mustChangePassword = MutableStateFlow(false)
    val mustChangePasswordState: StateFlow<Boolean> = _mustChangePassword.asStateFlow()

    init {
        // Keep the mirror + flows live with any external write. Does NOT flip `loaded`:
        // the one-time migration in load() must run before we trust the store to be seeded.
        scope.launch {
            dataStore.data.collect { s ->
                cache = s
                if (loaded) {
                    _sessionState.value = s.accessToken.isNotBlank()
                    _mustChangePassword.value = s.accessToken.isNotBlank() && s.mustChangePassword
                }
            }
        }
    }

    // --- Synchronous reads (seed lazily; every caller is on a background thread) ---

    val accessToken: String? get() { ensureLoaded(); return cache.accessToken.ifBlank { null } }
    val refreshToken: String? get() { ensureLoaded(); return cache.refreshToken.ifBlank { null } }
    val userId: String? get() { ensureLoaded(); return cache.userId.ifBlank { null } }
    val userName: String? get() { ensureLoaded(); return cache.userName.ifBlank { null } }
    val cabangName: String? get() { ensureLoaded(); return cache.cabangName.ifBlank { null } }
    val whatsapp: String? get() { ensureLoaded(); return cache.whatsapp.ifBlank { null } }
    val mustChangePassword: Boolean get() { ensureLoaded(); return cache.mustChangePassword }
    val isLoggedIn: Boolean get() { ensureLoaded(); return cache.accessToken.isNotBlank() }

    /** The last profile the server gave us, rebuilt as a [UserDto] — the offline fallback for the
     *  Settings/profile screen. Null until a login or profile fetch has ever populated it. */
    fun cachedProfile(): UserDto? {
        ensureLoaded()
        val s = cache
        if (s.accessToken.isBlank() || s.userName.isBlank()) return null
        return UserDto(
            id = s.userId,
            nik = s.nik,
            email = s.email,
            name = s.userName,
            role = s.role,
            cabangName = s.cabangName,
            whatsapp = s.whatsapp,
            mustChangePassword = s.mustChangePassword
        )
    }

    /** True when the access token is missing or within [marginMillis] of expiry — used for proactive refresh. */
    fun accessTokenExpiresWithin(marginMillis: Long): Boolean {
        ensureLoaded()
        val exp = cache.accessTokenExpiresAtMillis
        if (cache.accessToken.isBlank()) return false // nothing to refresh proactively
        if (exp == 0L) return false // unknown expiry — let the 401 path handle it
        return System.currentTimeMillis() >= exp - marginMillis
    }

    // --- Writes (mirror updated synchronously, DataStore persisted async) ---

    /** Full session after a successful login. */
    fun saveLogin(session: SessionData) = mutate {
        it.copy(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            accessTokenExpiresAtMillis = expiryFrom(session.expiresIn),
            userId = session.user.id,
            userName = session.user.name,
            cabangName = session.user.cabangName,
            whatsapp = session.user.whatsapp,
            role = session.user.role,
            nik = session.user.nik,
            email = session.user.email,
            mustChangePassword = session.user.mustChangePassword
        )
    }

    /** New rotated tokens from `/auth/refresh` (profile untouched). */
    fun updateTokens(accessToken: String, refreshToken: String, expiresInSeconds: Int) = mutate {
        it.copy(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAtMillis = expiryFrom(expiresInSeconds)
        )
    }

    /** Refreshed profile (no token change). */
    fun updateProfile(user: UserDto) = mutate {
        it.copy(
            userId = user.id,
            userName = user.name,
            cabangName = user.cabangName,
            whatsapp = user.whatsapp,
            role = user.role,
            nik = user.nik,
            email = user.email,
            mustChangePassword = user.mustChangePassword
        )
    }

    /** After a successful password change, drop the forced-change flag. */
    fun markPasswordChanged() = mutate { it.copy(mustChangePassword = false) }

    fun clear() {
        cache = PersistedSession()
        loaded = true
        _sessionState.value = false
        _mustChangePassword.value = false
        scope.launch { dataStore.updateData { PersistedSession() } }
    }

    /** Pay the seed + legacy-migration cost early, off the main thread (called from Application). */
    suspend fun warmUp() = load()

    // --- internals ---

    private fun mutate(transform: (PersistedSession) -> PersistedSession) {
        val updated = transform(cache)
        cache = updated
        loaded = true
        _sessionState.value = updated.accessToken.isNotBlank()
        _mustChangePassword.value = updated.accessToken.isNotBlank() && updated.mustChangePassword
        // Persist the latest mirror. Writing `cache` (not a captured snapshot) makes concurrent
        // persists idempotently converge on the final state — no lost-update or resurrection.
        scope.launch { dataStore.updateData { cache } }
    }

    private fun ensureLoaded() {
        if (loaded) return
        // Safe: only background-thread callers reach here before warmUp() has run.
        runBlocking { load() }
    }

    private suspend fun load() = loadMutex.withLock {
        if (loaded) return@withLock
        migrateLegacyIfNeeded()
        cache = dataStore.data.first()
        loaded = true
        _sessionState.value = cache.accessToken.isNotBlank()
        _mustChangePassword.value = cache.accessToken.isNotBlank() && cache.mustChangePassword
    }

    /** One-time move of the legacy EncryptedSharedPreferences session into the DataStore. */
    private suspend fun migrateLegacyIfNeeded() {
        if (dataStore.data.first().accessToken.isNotBlank()) return // already have a session
        val legacy = readLegacySession() ?: return
        if (legacy.accessToken.isNotBlank()) {
            dataStore.updateData { legacy }
        }
        clearLegacyPrefs()
    }

    private fun readLegacySession(): PersistedSession? = runCatching {
        val prefs = legacyPrefs() ?: return null
        val access = prefs.getString("access_token", null).orEmpty()
        if (access.isBlank()) return null
        PersistedSession(
            accessToken = access,
            refreshToken = prefs.getString("refresh_token", null).orEmpty(),
            userId = prefs.getString("user_id", null).orEmpty(),
            userName = prefs.getString("user_name", null).orEmpty(),
            cabangName = prefs.getString("cabang_name", null).orEmpty(),
            whatsapp = prefs.getString("whatsapp", null).orEmpty()
        )
    }.getOrNull()

    private fun clearLegacyPrefs() {
        runCatching { legacyPrefs()?.edit()?.clear()?.apply() }
    }

    private fun legacyPrefs() = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "tridjaya_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrNull()

    private fun expiryFrom(expiresInSeconds: Int): Long =
        if (expiresInSeconds > 0) System.currentTimeMillis() + expiresInSeconds * 1000L else 0L

    companion object {
        private const val DATASTORE_FILE = "tridjaya_session.pb"
    }
}

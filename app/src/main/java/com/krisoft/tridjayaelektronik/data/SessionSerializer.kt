package com.krisoft.tridjayaelektronik.data

import androidx.datastore.core.Serializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * The whole persisted session, stored as one encrypted blob in the DataStore. Secret fields
 * (tokens) and cached profile fields live together so a single atomic write keeps them consistent.
 */
@Serializable
data class PersistedSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    /** Epoch millis when the access token expires (derived from login/refresh `expires_in`). 0 = unknown. */
    val accessTokenExpiresAtMillis: Long = 0L,
    val userId: String = "",
    val userName: String = "",
    val cabangName: String = "",
    val whatsapp: String = "",
    val role: String = "",
    val nik: String = "",
    val email: String = "",
    val mustChangePassword: Boolean = false,
    /** Role efektif (folded) CSV — dipakai gating menu (mis. hub SPK). Kosong di
     *  blob lama pra-update sampai profil ter-refresh. */
    val rolesCsv: String = "",
    /** Divisi CSV (multi-nilai) — folding operasional (pdi/kasir/driver/dst). */
    val divisi: String = "",
    /** Prefix page-grant CSV — deteksi approver (diskon/aki) untuk gating menu. */
    val grantsCsv: String = ""
)

/**
 * DataStore serializer that transparently encrypts/decrypts the session via [SessionCrypto].
 * On any read failure (empty file, or an undecryptable blob — e.g. the Keystore key was lost after
 * a cloud restore) it returns the empty default so the app starts logged-out instead of crashing.
 */
object SessionSerializer : Serializer<PersistedSession> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: PersistedSession = PersistedSession()

    override suspend fun readFrom(input: InputStream): PersistedSession {
        // `input.readBytes()` IKUT di dalam try. Sebelumnya ia berada di luar,
        // sehingga IOException saat membaca `tridjaya_session.pb` (sektor flash
        // rusak, berkas terpangkas saat proses dimatikan paksa, izin kacau
        // sesudah restore) lolos sebagai uncaught exception dan membunuh app
        // SAAT START — layar putih lalu tertutup, tanpa jalan keluar selain
        // hapus data. Blob yang tak bisa didekripsi sudah ditangani sejak awal;
        // yang belum justru kegagalan membacanya.
        return try {
            val bytes = input.readBytes()
            if (bytes.isEmpty()) return defaultValue
            val plain = SessionCrypto.decrypt(bytes)
            json.decodeFromString(PersistedSession.serializer(), plain.decodeToString())
        } catch (e: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: PersistedSession, output: OutputStream) {
        // Enkripsi memakai Android Keystore: `SessionCrypto.encrypt` bisa
        // melempar ProviderException/KeyStoreException di HP yang keystore-nya
        // rusak (lazim sesudah pindah HP / restore cloud). DataStore memanggil
        // ini dari coroutine tanpa penangkap, jadi lemparan di sini = force
        // close. Sesi yang gagal DISIMPAN cuma berarti user harus login lagi
        // nanti — itu jauh lebih baik daripada app yang tak bisa dibuka.
        val plain = json.encodeToString(PersistedSession.serializer(), t).encodeToByteArray()
        val terenkripsi = try {
            SessionCrypto.encrypt(plain)
        } catch (e: Exception) {
            return
        }
        output.write(terenkripsi)
    }
}

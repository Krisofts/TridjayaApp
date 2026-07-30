package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitDao
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.ApiErrorResponse
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsData
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDeleteData
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameSessionDto
import com.krisoft.tridjayaelektronik.data.model.OpnameStockItemDto
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitInput
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

const val KONDISI_LAYAK = "layak"
const val KONDISI_TIDAK_LAYAK = "tidak_layak"

/** Batas kolom `stock_opname_units.serial_number` di MySQL. */
const val SERIAL_MAX_LENGTH = 64

/**
 * Cermin persis `normalize_serial` di `inventory-service/src/opname.rs`: trim + huruf besar,
 * `null` bila kosong atau lebih dari 64 karakter. Aturan yang berbeda sedikit saja berarti
 * duplikat lolos di salah satu sisi — app menerima apa yang server tolak, atau sebaliknya.
 *
 * `uppercase()` (bukan `toUpperCase()`) memakai `Locale.ROOT`, jadi tak ikut aturan Turki
 * yang mengubah "i" jadi "İ" di HP berbahasa Turki; `codePointCount` (bukan `length`)
 * menghitung karakter seperti `chars().count()` Rust, bukan unit UTF-16.
 */
fun normalizeSerial(raw: String): String? {
    val serial = raw.trim().uppercase()
    if (serial.isEmpty()) return null
    if (serial.codePointCount(0, serial.length) > SERIAL_MAX_LENGTH) return null
    return serial
}

/** Alasan penolakan dari server dalam bahasa yang bisa dibaca petugas di lapangan. */
fun alasanTolakLabel(reason: String): String = when (reason) {
    "duplikat_dalam_sesi" -> "serial ini sudah discan di sesi ini"
    else -> reason
}

/**
 * Stock opname (hitung fisik) client. Penghitungan berbasis UNIT: satu baris per serial
 * number, bukan angka jumlah per SKU.
 *
 * Tiap scan disimpan ke Room DULU (cepat, tetap jalan tanpa sinyal) lalu langsung dikirim.
 * Push per-scan, bukan sekali batch saat sesi ditutup, karena duplikat hanya bisa divonis
 * server: beberapa petugas memakai HP berbeda pada sesi yang sama, dan menunda pengiriman
 * berarti tabrakan baru ketahuan saat penutupan.
 */
@Singleton
class OpnameRepository @Inject constructor(
    private val api: InventoryApi,
    private val unitDao: OpnameUnitDao
) {

    private val errorJson = Json { ignoreUnknownKeys = true }
    private val pushMutex = Mutex()

    suspend fun context(): AuthResult<OpnameContextDto> =
        call("Gagal memuat konteks opname") { api.opnameContext() }

    suspend fun list(status: String? = null): AuthResult<List<OpnameSessionDto>> =
        when (val result = call("Gagal memuat daftar opname") { api.listOpname(status) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            is AuthResult.Failure -> AuthResult.Failure(result.code, result.message)
        }

    suspend fun create(request: CreateOpnameRequest): AuthResult<OpnameDetailDto> =
        call("Gagal membuat sesi opname") { api.createOpname(request) }

    suspend fun detail(id: String): AuthResult<OpnameDetailDto> =
        call("Gagal memuat detail opname") { api.opnameDetail(id) }

    suspend fun stockList(id: String): AuthResult<List<OpnameStockItemDto>> =
        when (val result = call("Gagal memuat daftar barang opname") { api.opnameStock(id) }) {
            is AuthResult.Success -> AuthResult.Success(result.data.items)
            is AuthResult.Failure -> AuthResult.Failure(result.code, result.message)
        }

    // ---- Buffer unit lokal (offline-first) ----

    fun observeUnits(sessionId: String): Flow<List<OpnameUnitEntity>> = unitDao.observe(sessionId)

    suspend fun unitCount(sessionId: String): Int = unitDao.countAll(sessionId)

    sealed interface ScanResult {
        /** Tersimpan di server; [temuan] terisi bila serialnya janggal. */
        data class Accepted(val serialNumber: String, val temuan: String?) : ScanResult
        data class Rejected(val serialNumber: String, val reason: String) : ScanResult
        /** Tersimpan lokal, menunggu jaringan. */
        data class Queued(val serialNumber: String, val reason: String) : ScanResult
    }

    /**
     * Catat satu unit. Duplikat lokal ditolak seketika tanpa menunggu jaringan; server
     * tetap wasit terakhir untuk duplikat lintas-perangkat.
     */
    suspend fun scanUnit(
        sessionId: String,
        kodeBarang: String,
        namaBarang: String?,
        serialNumberRaw: String,
        kondisi: String = KONDISI_LAYAK,
        keterangan: String? = null
    ): ScanResult {
        val serial = normalizeSerial(serialNumberRaw)
            ?: return ScanResult.Rejected(
                serialNumberRaw.trim(),
                "serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter"
            )
        if (unitDao.countSerial(sessionId, serial) > 0) {
            return ScanResult.Rejected(serial, alasanTolakLabel("duplikat_dalam_sesi"))
        }
        val now = System.currentTimeMillis()
        unitDao.upsert(
            OpnameUnitEntity(
                sessionId = sessionId,
                serialNumber = serial,
                kodeBarang = kodeBarang,
                namaBarang = namaBarang,
                kondisi = kondisi,
                keterangan = keterangan?.takeIf { it.isNotBlank() },
                temuan = null,
                updatedAtMillis = now,
                syncedAtMillis = null
            )
        )
        return when (val pushed = pushPending(sessionId)) {
            is AuthResult.Success -> {
                val rejected = pushed.data.rejected.firstOrNull { it.serialNumber == serial }
                if (rejected != null) {
                    ScanResult.Rejected(serial, alasanTolakLabel(rejected.reason))
                } else {
                    ScanResult.Accepted(
                        serial,
                        pushed.data.accepted.firstOrNull { it.serialNumber == serial }?.temuan
                    )
                }
            }
            is AuthResult.Failure -> ScanResult.Queued(serial, pushed.message)
        }
    }

    /**
     * Kirim semua baris yang belum tersinkron untuk sesi ini.
     *
     * Digembok satu-per-satu (pola `CrmRepository.syncPendingLeads`): scan berikutnya dan
     * tombol "Kirim ulang" bisa memanggil ini bersamaan, dan dua pengiriman paralel akan
     * membawa baris yang SAMA — yang kedua dijawab `duplikat_dalam_sesi` lalu barisnya
     * dihapus dari buffer, padahal itu unit sah milik petugas ini.
     */
    suspend fun pushPending(sessionId: String): AuthResult<CreateOpnameUnitsData> =
        pushMutex.withLock { pushPendingLocked(sessionId) }

    private suspend fun pushPendingLocked(sessionId: String): AuthResult<CreateOpnameUnitsData> {
        val pending = unitDao.pending(sessionId)
        if (pending.isEmpty()) return AuthResult.Success(CreateOpnameUnitsData())
        val request = CreateOpnameUnitsRequest(
            items = pending.map {
                OpnameUnitInput(
                    kodeBarang = it.kodeBarang,
                    serialNumber = it.serialNumber,
                    kondisi = it.kondisi,
                    keterangan = it.keterangan
                )
            }
        )
        val result = call("Gagal mengirim hasil scan") { api.createOpnameUnits(sessionId, request) }
        if (result is AuthResult.Success) {
            val now = System.currentTimeMillis()
            result.data.accepted.forEach { unitDao.markSynced(sessionId, it.serialNumber, now, it.temuan) }
            // Ditolak server (mis. petugas lain sudah scan serial itu) — jangan
            // tinggalkan baris hantu di buffer lokal.
            result.data.rejected.forEach { unitDao.delete(sessionId, it.serialNumber) }
        }
        return result
    }

    /**
     * Hapus satu unit (salah scan). Baris yang sudah tersinkron dihapus di server dulu —
     * kalau tidak, hitungan server tetap menghitungnya.
     */
    suspend fun deleteUnit(sessionId: String, unit: OpnameUnitEntity): AuthResult<Unit> {
        if (unit.syncedAtMillis != null) {
            val serverId = serverUnitId(sessionId, unit.serialNumber)
            if (serverId != null) {
                val removed = call<OpnameDetailDto>("Gagal menghapus unit") {
                    api.deleteOpnameUnit(sessionId, serverId)
                }
                if (removed is AuthResult.Failure) {
                    return AuthResult.Failure(removed.code, removed.message)
                }
            }
        }
        unitDao.delete(sessionId, unit.serialNumber)
        return AuthResult.Success(Unit)
    }

    /** Id unit di server — dibutuhkan untuk menghapus baris yang sudah tersinkron. */
    private suspend fun serverUnitId(sessionId: String, serialNumber: String): String? {
        val listed = call("Gagal memuat unit") { api.listOpnameUnits(sessionId) }
        return (listed as? AuthResult.Success)
            ?.data
            ?.items
            ?.firstOrNull { it.serialNumber.equals(serialNumber, ignoreCase = true) }
            ?.id
    }

    /**
     * Tutup sesi: pastikan antrean terkirim dulu, baru complete. Buffer dibuang hanya
     * setelah server benar-benar menutup sesi.
     */
    suspend fun finalize(sessionId: String): AuthResult<OpnameDetailDto> {
        when (val pushed = pushPending(sessionId)) {
            is AuthResult.Failure -> return AuthResult.Failure(pushed.code, pushed.message)
            is AuthResult.Success -> Unit
        }
        val completed = call<OpnameDetailDto>("Gagal menyelesaikan sesi") { api.completeOpname(sessionId) }
        if (completed is AuthResult.Success) {
            unitDao.clearSession(sessionId)
        }
        return completed
    }

    suspend fun cancel(id: String): AuthResult<OpnameDetailDto> {
        val result = call<OpnameDetailDto>("Gagal membatalkan sesi") { api.cancelOpname(id) }
        if (result is AuthResult.Success) {
            unitDao.clearSession(id)
        }
        return result
    }

    /** Hapus permanen sesi yang sudah dibatalkan. Server menegakkan status; klien cuma menampilkan tombolnya. */
    suspend fun deleteSession(id: String): AuthResult<Unit> {
        val result = call<OpnameDeleteData>("Gagal menghapus sesi") { api.deleteOpname(id) }
        if (result is AuthResult.Success) {
            unitDao.clearSession(id)
        }
        return when (result) {
            is AuthResult.Success -> AuthResult.Success(Unit)
            is AuthResult.Failure -> result
        }
    }

    private suspend fun <T> call(
        fallback: String,
        block: suspend () -> Response<ApiResponse<T>>
    ): AuthResult<T> {
        return try {
            val response = block()
            val data = response.body()?.data
            if (response.isSuccessful && data != null) {
                AuthResult.Success(data)
            } else {
                parseError(response, fallback)
            }
        } catch (e: Exception) {
            AuthResult.Failure("network_error", e.message ?: "Tidak bisa terhubung ke server")
        }
    }

    /** Same shape as InventoryRepository.parseError: validation detail in errors[0] wins. */
    private fun <T> parseError(response: Response<*>, fallback: String): AuthResult<T> {
        val raw = response.errorBody()?.string()
        val parsed = raw?.let {
            runCatching { errorJson.decodeFromString(ApiErrorResponse.serializer(), it) }.getOrNull()
        }
        val detail = parsed?.errors?.firstOrNull() ?: parsed?.message
        return AuthResult.Failure(
            parsed?.code ?: "http_${response.code()}",
            detail ?: "$fallback (${response.code()})"
        )
    }
}

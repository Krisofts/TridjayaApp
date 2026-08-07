package com.krisoft.tridjayaelektronik.data

import com.krisoft.tridjayaelektronik.data.local.OpnameUnitDao
import com.krisoft.tridjayaelektronik.data.local.OpnameUnitEntity
import com.krisoft.tridjayaelektronik.data.model.ApiResponse
import com.krisoft.tridjayaelektronik.data.model.CreateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameRequest
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsData
import com.krisoft.tridjayaelektronik.data.model.CreateOpnameUnitsRequest
import com.krisoft.tridjayaelektronik.data.model.IndentDto
import com.krisoft.tridjayaelektronik.data.model.IndentListData
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriDetailListDto
import com.krisoft.tridjayaelektronik.data.model.MutasiHistoriListDto
import com.krisoft.tridjayaelektronik.data.model.OpnameContextDto
import com.krisoft.tridjayaelektronik.data.model.OpnameDeleteData
import com.krisoft.tridjayaelektronik.data.model.OpnameDetailDto
import com.krisoft.tridjayaelektronik.data.model.OpnameListData
import com.krisoft.tridjayaelektronik.data.model.OpnameStockData
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitAccepted
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitListData
import com.krisoft.tridjayaelektronik.data.model.OpnameUnitRejected
import com.krisoft.tridjayaelektronik.data.model.StokCabangPageDto
import com.krisoft.tridjayaelektronik.data.model.UpdateIndentRequest
import com.krisoft.tridjayaelektronik.data.model.UploadProofResponseDto
import com.krisoft.tridjayaelektronik.data.remote.InventoryApi
import com.krisoft.tridjayaelektronik.ui.opname.temuanLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Antrean offline opname: satu unit yang discan tanpa sinyal harus TETAP terhitung di HP dan
 * ikut terkirim begitu jaringan pulih. Ini bagian yang tak boleh diam-diam rusak — kalau
 * antrean hilang, hitungan fisik yang sudah dilakukan petugas di gudang ikut hilang, dan
 * tak ada cara mengetahuinya selain menghitung ulang seisi gudang.
 *
 * Sengaja memakai DAO/API palsu di JVM: yang diuji keputusan repositorinya, bukan Room/Retrofit.
 */
class OpnameOfflineQueueTest {

    private val sessionId = "sesi-1"

    // ---- Test doubles -----------------------------------------------------------------

    private class FakeUnitDao : OpnameUnitDao {
        val rows = mutableListOf<OpnameUnitEntity>()
        private val stream = MutableStateFlow<List<OpnameUnitEntity>>(emptyList())

        private fun publish() {
            stream.value = rows.toList()
        }

        override fun observe(sessionId: String): Flow<List<OpnameUnitEntity>> = stream

        override suspend fun pending(sessionId: String): List<OpnameUnitEntity> =
            rows.filter { it.sessionId == sessionId && it.syncedAtMillis == null }

        // Cermin query DAO asli: baris REJECTED tak menghalangi serial dikirim ulang.
        override suspend fun countSerial(sessionId: String, serialNumber: String): Int =
            rows.count {
                it.sessionId == sessionId && it.serialNumber.equals(serialNumber, true) &&
                    it.validationStatus != "rejected"
            }

        override suspend fun updateValidation(
            sessionId: String,
            serialNumber: String,
            status: String?,
            reason: String?
        ) {
            rows.forEachIndexed { index, row ->
                if (row.sessionId == sessionId && row.serialNumber.equals(serialNumber, true)) {
                    rows[index] = row.copy(validationStatus = status, rejectReason = reason)
                }
            }
            publish()
        }

        override suspend fun countAll(sessionId: String): Int = rows.count { it.sessionId == sessionId }

        override suspend fun upsert(entity: OpnameUnitEntity) {
            rows.removeAll { it.sessionId == entity.sessionId && it.serialNumber == entity.serialNumber }
            rows += entity
            publish()
        }

        override suspend fun markSynced(sessionId: String, serialNumber: String, now: Long, temuan: String?) {
            rows.forEachIndexed { index, row ->
                if (row.sessionId == sessionId && row.serialNumber == serialNumber) {
                    rows[index] = row.copy(syncedAtMillis = now, temuan = temuan)
                }
            }
            publish()
        }

        override suspend fun delete(sessionId: String, serialNumber: String) {
            rows.removeAll { it.sessionId == sessionId && it.serialNumber == serialNumber }
            publish()
        }

        override suspend fun clearSession(sessionId: String) {
            rows.removeAll { it.sessionId == sessionId }
            publish()
        }
    }

    /** `null` = offline (lempar IOException, persis mode pesawat). */
    private class FakeApi(var response: CreateOpnameUnitsData?) : StubInventoryApi() {
        var pushCount = 0
        var lastSent: List<String> = emptyList()

        override suspend fun createOpnameUnits(
            id: String,
            body: CreateOpnameUnitsRequest
        ): Response<ApiResponse<CreateOpnameUnitsData>> {
            pushCount += 1
            lastSent = body.items.map { it.serialNumber }
            val data = response ?: throw IOException("tidak ada jaringan")
            return Response.success(ApiResponse("ok", data))
        }
    }

    private fun repo(dao: OpnameUnitDao, api: InventoryApi) = OpnameRepository(api, dao)

    // ---- Tests ------------------------------------------------------------------------

    @Test
    fun `scan tanpa sinyal tetap tersimpan dan masuk antrean`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")

        assertTrue("harus dilaporkan sebagai antre, bukan gagal", hasil is OpnameRepository.ScanResult.Queued)
        assertEquals(1, dao.rows.size)
        assertEquals("SN-001", dao.rows.first().serialNumber)
        assertNull("belum tersinkron", dao.rows.first().syncedAtMillis)
        assertEquals(1, dao.pending(sessionId).size)
    }

    @Test
    fun `antrean terkirim saat jaringan pulih lalu hilang dari antrean`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-002")
        assertEquals(2, dao.pending(sessionId).size)

        api.response = CreateOpnameUnitsData(
            accepted = listOf(OpnameUnitAccepted("SN-001"), OpnameUnitAccepted("SN-002"))
        )
        repository.pushPending(sessionId)

        assertEquals("dua unit tetap terhitung", 2, dao.rows.size)
        assertEquals("antrean kosong", 0, dao.pending(sessionId).size)
        assertEquals(listOf("SN-001", "SN-002"), api.lastSent)
    }

    @Test
    fun `pengiriman gagal tidak menghapus apa pun dari antrean`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        val ulang = repository.pushPending(sessionId)

        assertTrue(ulang is AuthResult.Failure)
        assertEquals(1, dao.pending(sessionId).size)
    }

    @Test
    fun `duplikat lokal ditolak tanpa menyentuh jaringan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("SN-001"))))
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        val pushSetelahScanPertama = api.pushCount

        // Huruf kecil + spasi: normalisasi harus menyamakannya dengan yang sudah discan.
        val kedua = repository.scanUnit(sessionId, "BRG-2", "Mesin Cuci", " sn-001 ")

        assertTrue(kedua is OpnameRepository.ScanResult.Rejected)
        assertEquals("serial ini sudah discan di sesi ini", (kedua as OpnameRepository.ScanResult.Rejected).reason)
        assertEquals("tak boleh ada request kedua", pushSetelahScanPertama, api.pushCount)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `serial kosong ditolak sebelum menyentuh jaringan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = CreateOpnameUnitsData())

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "   ")

        assertTrue(hasil is OpnameRepository.ScanResult.Rejected)
        assertEquals(0, api.pushCount)
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `serial asing diterima dan penandanya ikut tersimpan`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("SN-ASING", temuan = "tidak_terdaftar"))
            )
        )

        val hasil = repo(dao, api).scanUnit(sessionId, "BRG-1", "Kulkas", "sn-asing")

        // Temuan BUKAN error: unit tetap terhitung, cuma diberi penanda buat manager.
        assertTrue(hasil is OpnameRepository.ScanResult.Accepted)
        assertEquals("tidak_terdaftar", (hasil as OpnameRepository.ScanResult.Accepted).temuan)
        assertEquals(1, dao.rows.size)
        assertEquals("tidak_terdaftar", dao.rows.first().temuan)
        assertEquals("baris tersinkron, bukan tertinggal di antrean", 0, dao.pending(sessionId).size)
    }

    @Test
    fun `tiga jenis temuan punya label yang bisa dibaca petugas`() {
        assertEquals("belum terdaftar di registry", temuanLabel("tidak_terdaftar"))
        assertEquals("terdaftar di cabang lain", temuanLabel("cabang_lain"))
        assertEquals("tercatat sudah terjual", temuanLabel("sudah_terjual"))
        // Penanda baru dari server tampil apa adanya, bukan hilang.
        assertEquals("penanda_baru", temuanLabel("penanda_baru"))
    }

    @Test
    fun `ditolak server karena petugas lain sudah scan maka baris hantu dibuang`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        api.response = CreateOpnameUnitsData(
            rejected = listOf(OpnameUnitRejected("SN-001", "duplikat_dalam_sesi"))
        )
        repository.pushPending(sessionId)

        // Server pemilik kebenaran: unit itu sudah tercatat atas nama petugas lain, jadi
        // menyimpannya di HP ini cuma bikin hitungan lokal lebih besar dari hitungan server.
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `antrean sesi lain tidak ikut terkirim`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)
        val repository = repo(dao, api)

        repository.scanUnit(sessionId, "BRG-1", "Kulkas", "sn-001")
        repository.scanUnit("sesi-2", "BRG-9", "TV", "sn-999")

        api.response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("SN-001")))
        repository.pushPending(sessionId)

        assertEquals(listOf("SN-001"), api.lastSent)
        assertEquals(1, dao.pending("sesi-2").size)
    }

    // ---- Unit ketik-manual (wajib foto + validasi admin-stok) -------------------------

    @Test
    fun `unit manual diterima masuk pending dan tak pernah mengantre offline`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("MAN-1", validationStatus = "pending"))
            )
        )

        val hasil = repo(dao, api).manualUnit(
            sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "sn.jpg", "barang.jpg"
        )

        assertTrue(hasil is OpnameRepository.ScanResult.Accepted)
        assertEquals("pending", (hasil as OpnameRepository.ScanResult.Accepted).validationStatus)
        val row = dao.rows.single()
        assertEquals("manual", row.inputMethod)
        assertEquals("pending", row.validationStatus)
        assertEquals("langsung synced — pushPending tak boleh mengirim ulang tanpa foto", 0, dao.pending(sessionId).size)
        assertEquals(listOf("MAN-1"), api.lastSent)
    }

    @Test
    fun `unit manual saat offline DITOLAK jelas tanpa baris hantu`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(response = null)

        val hasil = repo(dao, api).manualUnit(
            sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "sn.jpg", "barang.jpg"
        )

        assertTrue("offline harus penolakan jelas, bukan antre", hasil is OpnameRepository.ScanResult.Rejected)
        assertTrue((hasil as OpnameRepository.ScanResult.Rejected).reason.contains("Butuh koneksi"))
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `serial yang ditolak admin-stok boleh discan ulang`() = runBlocking {
        val dao = FakeUnitDao()
        val api = FakeApi(
            response = CreateOpnameUnitsData(
                accepted = listOf(OpnameUnitAccepted("MAN-1", validationStatus = "pending"))
            )
        )
        val repository = repo(dao, api)

        repository.manualUnit(sessionId, "BRG-1", "Kulkas", "man-1", KONDISI_LAYAK, "a.jpg", "b.jpg")
        // Admin-stok menolak (vonis ditarik refreshValidationStatuses di alur nyata).
        dao.updateValidation(sessionId, "MAN-1", "rejected", "foto buram")

        api.response = CreateOpnameUnitsData(accepted = listOf(OpnameUnitAccepted("MAN-1")))
        val ulang = repository.scanUnit(sessionId, "BRG-1", "Kulkas", "man-1")

        assertTrue("baris rejected tak boleh memblokir scan ulang", ulang is OpnameRepository.ScanResult.Accepted)
        val row = dao.rows.single()
        assertEquals("scan", row.inputMethod)
        assertNull("jejak reject bersih setelah tertimpa", row.validationStatus)
    }

    @Test
    fun `alasan foto wajib punya label terbaca`() {
        assertEquals(
            "input manual wajib menyertakan dua foto bukti",
            alasanTolakLabel("foto_wajib_untuk_manual")
        )
    }
}

/**
 * Hanya endpoint opname unit yang dipakai tes ini; sisanya sengaja meledak supaya tes yang
 * diam-diam menyentuh jaringan lain langsung ketahuan.
 */
private open class StubInventoryApi : InventoryApi {
    private fun nope(): Nothing = error("endpoint ini tidak dipakai di tes")

    override suspend fun stokCabang(page: Int?, limit: Int?, refresh: Boolean?, inStock: Boolean?):
        Response<ApiResponse<StokCabangPageDto>> = nope()

    override suspend fun listIndent(status: String?): Response<ApiResponse<IndentListData>> = nope()

    override suspend fun createIndent(body: CreateIndentRequest): Response<ApiResponse<IndentDto>> = nope()

    override suspend fun updateIndentStatus(id: String, body: UpdateIndentRequest):
        Response<ApiResponse<IndentDto>> = nope()

    override suspend fun uploadIndentProof(file: MultipartBody.Part):
        Response<ApiResponse<UploadProofResponseDto>> = nope()

    override suspend fun opnameContext(): Response<ApiResponse<OpnameContextDto>> = nope()

    override suspend fun listOpname(status: String?): Response<ApiResponse<OpnameListData>> = nope()

    override suspend fun createOpname(body: CreateOpnameRequest): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun opnameDetail(id: String): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun opnameStock(id: String): Response<ApiResponse<OpnameStockData>> = nope()

    override suspend fun createOpnameUnits(id: String, body: CreateOpnameUnitsRequest):
        Response<ApiResponse<CreateOpnameUnitsData>> = nope()

    override suspend fun listOpnameUnits(id: String): Response<ApiResponse<OpnameUnitListData>> = nope()

    override suspend fun deleteOpnameUnit(id: String, unitId: String):
        Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun completeOpname(id: String): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun cancelOpname(id: String): Response<ApiResponse<OpnameDetailDto>> = nope()

    override suspend fun deleteOpname(id: String): Response<ApiResponse<OpnameDeleteData>> = nope()

    override suspend fun mutasiHistori(dealer: String?, arah: String?, from: String?, limit: Int?):
        Response<ApiResponse<MutasiHistoriListDto>> = nope()

    override suspend fun mutasiHistoriDetail(noTransaksi: String, arah: String):
        Response<ApiResponse<MutasiHistoriDetailListDto>> = nope()
}

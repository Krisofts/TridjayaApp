package com.krisoft.tridjayaelektronik.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paritas muatan `POST /api/home-service` antara app dan web.
 *
 * Kelas kegagalan yang dijaga di sini SENYAP semuanya — tak satu pun
 * memunculkan galat saat terjadi:
 *  - `encodeDefaults = false` membuang field yang nilainya sama dengan
 *    default-nya, jadi `items` bisa hilang dari JSON tanpa jejak;
 *  - server memakai `#[serde(default)]`, jadi field yang hilang diterima
 *    diam-diam lalu jatuh ke perilaku default "barang pertama transaksi";
 *  - `kodeBarang: ""` bukan diabaikan server melainkan dicari sebagai kode
 *    barang sungguhan, dan selalu gagal 400.
 */
class HsCreateTicketParityTest {

    /** SALINAN PERSIS config `NetworkModule.json` (`encodeDefaults` memang tak
     *  disebut di sana — default-nya `false`, dan itulah jebakannya). */
    private val jsonJaringan = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private fun encode(body: HsCreateTicketBody) =
        jsonJaringan.encodeToString(HsCreateTicketBody.serializer(), body)

    private fun terverifikasi(
        items: List<HsCreateTicketItem> = listOf(HsCreateTicketItem(1), HsCreateTicketItem(3)),
    ) = HsCreateTicketBody(
        noTransaksi = "D-01/PJB20260811/aEeL3tBxsr",
        fotoKwitansiUrl = "/uploads/home-service/a.jpg",
        deskripsi = "mesin cuci tidak berputar",
        items = items,
        barisTransaksi = 1,
        kodeBarang = "KB001",
        prioritas = "normal",
        sumber = "android",
        customerNama = "BUDI",
        customerHp = "0812",
        customerAlamat = "Jl. Mawar 1",
    )

    @Test
    fun `multi-barang terkirim sebagai items camelCase`() {
        val s = encode(terverifikasi())
        assertTrue("key items hilang: $s", s.contains("\"items\""))
        assertTrue("baris 1 hilang: $s", s.contains("\"barisTransaksi\":1"))
        assertTrue("baris 3 hilang: $s", s.contains("\"barisTransaksi\":3"))
    }

    @Test
    fun `barisTransaksi dan kodeBarang tunggal TETAP ikut demi server versi lama`() {
        // Server yang belum mengenal `items` mengabaikannya diam-diam; tanpa
        // pasangan tunggal ini ia lalu memilih barang PERTAMA transaksi sendiri.
        val s = encode(terverifikasi())
        assertTrue("barisTransaksi tunggal hilang: $s", s.contains("\"barisTransaksi\":1"))
        assertTrue("kodeBarang hilang: $s", s.contains("\"kodeBarang\":\"KB001\""))
    }

    @Test
    fun `baris 0 tetap terkirim — bukan sentinel, bukan objek kosong`() {
        // `baris` di data GS memang bisa 0 (`sync_sales.rs` menulis
        // `unwrap_or(0)`). Kalau field-nya diberi default, kotlinx membuangnya
        // dan server menerima `{}` yang dibacanya sebagai baris 0 juga — benar
        // secara kebetulan, dan berhenti benar begitu default-nya diubah.
        val s = encode(terverifikasi(items = listOf(HsCreateTicketItem(0))))
        assertTrue("baris 0 tak terkirim: $s", s.contains("\"barisTransaksi\":0"))
        assertFalse("item terkirim sebagai objek kosong: $s", s.contains("{}"))
    }

    @Test
    fun `jalur tanpa verifikasi tidak mengirim items, baris, maupun kodeBarang`() {
        // `noTransaksi` kosong = sinyal tiket belum-terverifikasi. Di jalur ini
        // server MEMBUANG items/barisTransaksi diam-diam tapi menolak 400 kalau
        // `kodeBarang` ikut terkirim — termasuk string kosong.
        val s = encode(
            HsCreateTicketBody(
                noTransaksi = "",
                fotoKwitansiUrl = "/uploads/home-service/a.jpg",
                deskripsi = "mesin cuci tidak berputar",
                items = emptyList(),
                barisTransaksi = null,
                kodeBarang = null,
                prioritas = "mendesak",
                sumber = "android",
                customerNama = "BUDI",
                customerHp = "0812",
                customerAlamat = "Jl. Mawar 1",
            ),
        )
        assertTrue("noTransaksi kosong wajib tetap dikirim: $s", s.contains("\"noTransaksi\":\"\""))
        assertFalse("items tak boleh ikut: $s", s.contains("\"items\""))
        assertFalse("barisTransaksi tak boleh ikut: $s", s.contains("\"barisTransaksi\""))
        assertFalse("kodeBarang tak boleh ikut: $s", s.contains("\"kodeBarang\""))
    }

    @Test
    fun `kontak kosong dihilangkan, bukan dikirim sebagai string kosong`() {
        // Server men-trim lalu mengganti nilai kosong dengan hasil pengayaan
        // SPK — mengirim "" dan tidak mengirim sama-sama berarti "pakai punyamu".
        val s = encode(
            terverifikasi().copy(customerNama = null, customerHp = null, customerAlamat = null),
        )
        assertFalse("customerNama null tak boleh muncul: $s", s.contains("\"customerNama\""))
        assertFalse("customerHp null tak boleh muncul: $s", s.contains("\"customerHp\""))
        assertFalse("customerAlamat null tak boleh muncul: $s", s.contains("\"customerAlamat\""))
    }

    @Test
    fun `respons tiket multi-barang terbaca, dan tiket lama tanpa items tetap terbaca`() {
        val baru = jsonJaringan.decodeFromString(
            HsTicketDto.serializer(),
            """{"id":"1","nomorTiket":"HS-1","terverifikasi":true,
               "items":[{"urutan":1,"barisTransaksi":1,"namaBarang":"AC","dalamGaransi":true},
                        {"urutan":2,"barisTransaksi":3,"namaBarang":"Kulkas","dalamGaransi":false}]}""",
        )
        assertTrue("items tiket tak terbaca", baru.items.size == 2)
        assertTrue("garansi per barang tak terbaca", baru.items[1].dalamGaransi.not())

        // Server MENGHILANGKAN kunci `items` (bukan mengirim []) saat kosong.
        val lama = jsonJaringan.decodeFromString(
            HsTicketDto.serializer(),
            """{"id":"1","nomorTiket":"HS-1","terverifikasi":false}""",
        )
        assertTrue("tiket tanpa items harus jadi daftar kosong", lama.items.isEmpty())
    }

    @Test
    fun `kontak lookup terbaca — nama properti HARUS sama dengan kunci kabel server`() {
        // Repo ini nol @SerialName, jadi nama properti Kotlin ADALAH nama di
        // kabel. Sebelum 2026-08-18 DTO ini bernama nama/hp/alamat/mapUrl dan
        // `ignoreUnknownKeys = true` menelan seluruh isinya tanpa galat: prefill
        // kontak MATI sejak lahir dan hanya terlihat sebagai "kolomnya kosong".
        val kontak = jsonJaringan.decodeFromString(
            HsLookupData.serializer(),
            """{"noTransaksi":"D-01/PJB20260811/x","items":[],
               "kontak":{"deliveryJobId":"dj-1","customerNama":"BUDI","customerHp":"0812",
                         "customerAlamat":"Jl. Mawar 1","customerMapUrl":"https://maps",
                         "serialNumber":"SN-9"}}""",
        ).kontak
        assertTrue("nama konsumen tak terbaca", kontak.customerNama == "BUDI")
        assertTrue("HP tak terbaca", kontak.customerHp == "0812")
        assertTrue("alamat tak terbaca — teknisi akan didatangkan ke alamat ketikan", kontak.customerAlamat == "Jl. Mawar 1")
        assertTrue("mapUrl tak terbaca", kontak.customerMapUrl == "https://maps")
        assertTrue("serial tak terbaca", kontak.serialNumber == "SN-9")
    }

    @Test
    fun `detail tiket membawa terverifikasi dan daftar barang`() {
        val detail = jsonJaringan.decodeFromString(
            HsTicketDetailDto.serializer(),
            """{"id":"1","nomorTiket":"HS-1","terverifikasi":false,
               "items":[{"urutan":1,"barisTransaksi":2,"namaBarang":"AC","dalamGaransi":true}]}""",
        )
        assertFalse("terverifikasi tak terbaca — detail akan memvonis garansi palsu", detail.terverifikasi)
        assertTrue("items detail tak terbaca", detail.items.size == 1)
    }
}

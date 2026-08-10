package com.krisoft.tridjayaelektronik.ui.serials

import com.krisoft.tridjayaelektronik.data.SERIAL_MAX_LENGTH
import com.krisoft.tridjayaelektronik.data.model.SerialRegistryRow
import com.krisoft.tridjayaelektronik.data.normalizeSerial

/**
 * Satu unit yang sudah discan/diketik dan menunggu disimpan.
 *
 * [kondisi] `null` = **belum ditetapkan**, dan itu BUKAN sinonim `layak`. Server
 * memperlakukan `stock_serial_numbers.kondisi` NULL sebagai "tak ada pembanding"
 * saat membandingkan registry dengan temuan opname (`kondisi_registry:
 * Option<String>`), jadi mengisi `layak` otomatis berarti mengarang vonis yang
 * tak pernah diucapkan siapa pun — dan selisih registry-vs-lapangan yang jadi
 * gunanya modul itu ikut hilang.
 */
data class UnitEntri(
    val serial: String,
    val kondisi: String? = null,
    val keterangan: String? = null
)

/**
 * Kondisi yang pantas dijelaskan. `null` (belum ditetapkan) dan `layak` tidak:
 * tak ada yang perlu diterangkan dari unit yang belum divonis atau yang baik-baik
 * saja. Dipakai UI untuk memutuskan kapan kolom keterangan tampil, DAN dipakai
 * ViewModel untuk memutuskan kapan isinya boleh ikut terkirim — dua keputusan itu
 * WAJIB memakai aturan yang sama, kalau tidak ada catatan yang tersimpan ke unit
 * tanpa pernah terlihat oleh orang yang menetapkannya.
 */
fun kondisiPakaiKeterangan(kondisi: String?): Boolean =
    kondisi != null && kondisi != com.krisoft.tridjayaelektronik.data.KONDISI_LAYAK

/** Satu panggilan `POST /inventory/serial-numbers/kondisi`. */
data class KondisiBatch(
    val kondisi: String,
    val keterangan: String?,
    val serials: List<String>
)

/**
 * Kelompokkan unit jadi panggilan-panggilan kondisi.
 *
 * Endpoint-nya menerima SATU `kondisi` + SATU `keterangan` untuk sekumpulan
 * serial, jadi pengelompokannya harus per **pasangan** (kondisi, keterangan) —
 * bukan per kondisi saja. Menggabungkan dua keterangan berbeda ke satu panggilan
 * berarti salah satunya ditulis ke unit yang bukan miliknya: "layar retak"
 * menempel di unit yang dusnya cuma sobek, dan tak ada error apa pun yang
 * menandainya.
 *
 * Unit tanpa kondisi TIDAK ikut — lihat [UnitEntri.kondisi]. Urutan pemasukan
 * dipertahankan supaya laporan hasilnya bisa dibaca berdampingan dengan daftar
 * di layar.
 */
fun kelompokkanKondisi(units: List<UnitEntri>): List<KondisiBatch> {
    val urutan = LinkedHashMap<Pair<String, String?>, MutableList<String>>()
    for (unit in units) {
        val kondisi = unit.kondisi ?: continue
        val keterangan = unit.keterangan?.trim()?.takeIf { it.isNotEmpty() }
        urutan.getOrPut(kondisi to keterangan) { mutableListOf() }.add(unit.serial)
    }
    return urutan.map { (kunci, serials) -> KondisiBatch(kunci.first, kunci.second, serials) }
}

/** Hasil satu percobaan memasukkan serial — hasil scan maupun ketikan. */
sealed interface HasilTambahSerial {
    data class Diterima(val serial: String) : HasilTambahSerial
    data class Ditolak(val alasan: String) : HasilTambahSerial
}

/**
 * Satu unit masuk daftar, atau ditolak dengan alasan yang bisa dibaca petugas.
 *
 * Dipisah jadi fungsi murni karena inilah titik yang paling sering dijalankan
 * di layar ini — sekali per unit fisik, puluhan kali per produk — dan tiga
 * kegagalannya sama-sama tak memunculkan error kalau salah:
 *
 * - **Normalisasi berbeda dari server** → app menerima serial yang server tolak
 *   (atau menganggap dua serial berbeda padahal server menganggapnya sama).
 *   Karena itu dipakai [normalizeSerial] yang SAMA dengan scan opname, bukan
 *   `trim()` sendiri.
 * - **Duplikat dalam satu daftar** → unit yang sama discan dua kali (gampang:
 *   barcode kecil, gudang gelap) lalu terhitung dua unit. Server menolaknya
 *   sebagai duplikat, tapi laporannya baru muncul sesudah simpan.
 * - **Sudah terdaftar sebelumnya** → petugas mengulang pekerjaan yang sudah
 *   selesai tanpa tahu. Ini kasus yang paling sering di gudang yang sebagian
 *   besar barangnya SUDAH bernomor pabrik dan sedang didata belakangan.
 *
 * [sudahTerdaftar] boleh TIDAK lengkap (server memotong daftar di 500 baris) —
 * itu fail-open yang disengaja: yang lolos di sini tetap ditolak server saat
 * simpan dan dilaporkan di `skipped`.
 */
fun tambahSerial(
    raw: String,
    daftar: List<String>,
    sudahTerdaftar: Set<String>
): HasilTambahSerial {
    val serial = normalizeSerial(raw)
        ?: return HasilTambahSerial.Ditolak(
            "Serial kosong atau lebih dari $SERIAL_MAX_LENGTH karakter."
        )
    if (serial in daftar) {
        return HasilTambahSerial.Ditolak("$serial sudah ada di daftar ini.")
    }
    if (serial in sudahTerdaftar) {
        return HasilTambahSerial.Ditolak("$serial sudah terdaftar untuk produk ini.")
    }
    return HasilTambahSerial.Diterima(serial)
}

/**
 * Satu baris jejak: siapa menetapkan kondisi & kapan, lalu asal-usul barisnya.
 * Kondisi yang belum pernah ditetapkan dikatakan APA ADANYA — menuliskannya
 * sebagai "-" saja membuat unit tanpa vonis tak bisa dibedakan dari unit yang
 * datanya sekadar tak terbaca.
 */
fun jejakUnit(baris: SerialRegistryRow): String {
    val vonis = when {
        baris.kondisi == null -> "Kondisi belum pernah ditetapkan"
        else -> buildString {
            append("Kondisi oleh ")
            append(baris.kondisiByName?.takeIf { it.isNotBlank() } ?: "tak diketahui")
            baris.kondisiAt?.takeIf { it.isNotBlank() }?.let { append(" · ").append(waktuSingkat(it)) }
        }
    }
    val asal = when (baris.sourceFile) {
        "manual-input" -> "diketik admin-stok"
        "manager-generated" -> "kode GEN- dibuat sistem"
        "usulan-cabang" -> "dari usulan cabang"
        "" -> "asal tak diketahui"
        else -> "impor ${baris.sourceFile}"
    }
    val pendaftar = baris.createdByName?.takeIf { it.isNotBlank() }?.let { " oleh $it" }.orEmpty()
    return "$vonis\n$asal$pendaftar"
}

/** `2026-08-10T17:05:00` → `10 Agu 17:05`. Tanpa `java.time` (haram di minSdk 24). */
fun waktuSingkat(iso: String): String {
    val bagian = iso.split("T")
    if (bagian.size < 2) return iso
    val tgl = bagian[0].split("-")
    if (tgl.size < 3) return iso
    val bulan = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    val idx = tgl[1].toIntOrNull()?.minus(1) ?: return iso
    val namaBulan = bulan.getOrNull(idx) ?: return iso
    val jam = bagian[1].take(5)
    return "${tgl[2].trimStart('0')} $namaBulan $jam"
}

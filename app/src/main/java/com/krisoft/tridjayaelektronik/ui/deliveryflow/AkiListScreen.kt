package com.krisoft.tridjayaelektronik.ui.deliveryflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.AkiFormDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveOutlinedButton
import com.krisoft.tridjayaelektronik.ui.theme.ScrollableCenter
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaPullRefresh

/** Menu "Pengambilan Aki" — riwayat form (082) + approval + tandai aki bekas dikembalikan.
 *  Bahasa visual kartunya sengaja MENIRU [DiscountSpkCard]: header kode-SPK + badge status,
 *  isi keputusan disorot, keterangan SPK menyusul, aksi berlabel teks di bawah. Dua layar ini
 *  dibuka orang yang sama dalam menit yang sama; dua tata letak berbeda = dua kali belajar.
 *  RBAC (approver pusat / pdi) tetap ditegakkan backend. */
@Composable
fun AkiListScreen(
    onBack: () -> Unit,
    onDetailSpk: (String) -> Unit,
    viewModel: DeliveryFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var periode by remember { mutableStateOf(PeriodeSpk.HARI_INI) }
    // Muat ulang tiap periode berubah — `LaunchedEffect(periode)` sekaligus jadi
    // muatan awal, jadi tak ada jalur yang memanggil `loadAkiForms()` tanpa rentang
    // (itu akan menarik SELURUH riwayat sementara chip menunjukkan "Hari ini").
    LaunchedEffect(periode) {
        val r = rentangPeriode(periode)
        viewModel.loadAkiForms(r.dari, r.sampai)
    }
    val muatUlang = { val r = rentangPeriode(periode); viewModel.loadAkiForms(r.dari, r.sampai) }
    var confirmId by remember { mutableStateOf<String?>(null) }
    var rejectId by remember { mutableStateOf<String?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    TridjayaCollapsibleHeader(title = "Pengambilan Aki", onBack = onBack) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Baris filter di LUAR `when` keadaan: kalau ia ikut hilang saat daftar
        // kosong/error, orang yang menyaring ke "Hari ini" lalu tak menemukan apa
        // pun kehilangan satu-satunya jalan kembali ke "Semua" — dan membacanya
        // sebagai data yang hilang, bukan sebagai saringan yang sedang aktif.
        Column(contentModifier.fillMaxSize()) {
            PeriodeFilterRow(dipilih = periode, onPilih = { periode = it })
            TridjayaPullRefresh(
                isRefreshing = state.loading && state.akiList.isNotEmpty(),
                onRefresh = muatUlang,
                modifier = Modifier.weight(1f)
            ) {
                when {
                    state.loading && state.akiList.isEmpty() ->
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    state.error != null && state.akiList.isEmpty() ->
                        ScrollableCenter {
                            ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = muatUlang)
                        }
                    state.akiList.isEmpty() ->
                        ScrollableCenter {
                            ExpressiveEmptyState(
                                icon = { Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) },
                                title = "Belum ada form aki",
                                // Sebut periodenya: daftar kosong pada saringan "Hari ini"
                                // bukan berita yang sama dengan daftar kosong pada "Semua".
                                subtitle = "Tidak ada pengambilan aki untuk ${periode.keterangan}."
                            )
                        }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.actionError?.let { item { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) } }
                        items(state.akiList, key = { it.id }) { form ->
                            AkiCard(
                                form, state.submitting, state.akiPhotos[form.id],
                                // Tombol approve/reject HANYA utk approver pusat (page-grant
                                // aki-approval) / admin/manager — redesain 2026-07-24 (dulu
                                // juga kepala-cabang/admin-penjualan/kasir, 3-pihak/089).
                                // Pembaca lain (PDI) jangan lihat tombol yang pasti 403.
                                canApprove = viewModel.canApproveAki,
                                // Tandai-dikembalikan: backend pdi (cabang form) / admin saja.
                                canReturn = viewModel.access.pdi,
                                onApprove = { viewModel.approveAki(form.id) },
                                onReject = { rejectId = form.id; rejectReason = "" },
                                onMarkReturned = { confirmId = form.id },
                                onDetailSpk = { onDetailSpk(form.deliveryJobId) }
                            )
                        }
                    }
                }
            }
        }
    }

    confirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmId = null },
            title = { Text("Tandai dikembalikan?", fontWeight = FontWeight.Bold) },
            text = { Text("Aki bekas untuk form ini akan ditandai sudah dikembalikan.") },
            confirmButton = { TextButton(onClick = { viewModel.markAkiReturned(id); confirmId = null }) { Text("Tandai") } },
            dismissButton = { TextButton(onClick = { confirmId = null }) { Text("Batal") } }
        )
    }

    rejectId?.let { id ->
        AlertDialog(
            onDismissRequest = { rejectId = null },
            title = { Text("Tolak form aki?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Alasan penolakan wajib diisi.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text("Alasan…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = rejectReason.isNotBlank(),
                    onClick = { viewModel.rejectAki(id, rejectReason.trim()); rejectId = null }
                ) { Text("Tolak") }
            },
            dismissButton = { TextButton(onClick = { rejectId = null }) { Text("Batal") } }
        )
    }
}

/** `null`/kosong/"null" → `null`. Field `job*` diisi server lewat JOIN yang tak selalu
 *  ada (respons create/approve tak di-join), dan pernah ada yang tiba sebagai string
 *  literal "null" — merendernya apa adanya menaruh kata "null" di layar approver. */
private fun String?.isiAtauNull(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

/** Baris keterangan pengganti gambar — dibuat menonjol seukuran satu baris teks
 *  supaya kartu tidak terlihat "kosong biasa" saat fotonya bermasalah. */
@Composable
private fun AkiPhotoNotice(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

/**
 * Satu form aki = satu kartu.
 *
 * Urutannya mengikuti urutan pertanyaan approver, bukan urutan kolom tabel:
 * APA yang diambil (blok tersorot, paling menonjol — ini isi keputusannya),
 * UNTUK APA (tujuan), UNTUK SPK MANA (unit + konsumen + cabang), SIAPA yang
 * mengambil, lalu bukti & aksi. Sebelum redesain 2026-08-07 kartu ini membuka
 * dengan "tanggal · pengambil" dan barangnya jadi baris abu-abu kecil di bawah
 * tujuan — approver menyetujui pengambilan barang tanpa barangnya menonjol.
 */
@Composable
private fun AkiCard(
    form: AkiFormDto,
    submitting: Boolean,
    photo: AkiPhotoState?,
    canApprove: Boolean,
    canReturn: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onMarkReturned: () -> Unit,
    onDetailSpk: () -> Unit,
) {
    val sudah = form.akiBekasStatus == "sudah"
    val approved = form.approvalStatus == "approved"
    val rejected = form.approvalStatus == "rejected"
    val kode = form.jobKodePengiriman.isiAtauNull()
    val cabang = form.cabangNama.isiAtauNull() ?: form.kodeDealer.isiAtauNull()
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // Form lama / respons tak di-JOIN tak punya kode SPK. "Form aki"
                    // lebih jujur daripada baris kosong yang terbaca sebagai bug.
                    kode ?: "Form aki",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                ApprovalBadge(approved, rejected)
            }
            Spacer(Modifier.height(8.dp))
            // ── Blok keputusan: APA yang diambil ────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        listOfNotNull(form.merkTipe.isiAtauNull() ?: "Aki", form.kapasitas.isiAtauNull())
                            .joinToString(" ") + " · ${form.jumlahPcs} pcs",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    // "1 set (4 pcs)" dsb — 1 set baterai = 4 pcs fisik, jadi angka
                    // pcs saja bisa terbaca sebagai 4 unit terpisah.
                    form.jumlahKeterangan.isiAtauNull()?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Charger & kaca spion ikut keluar gudang bersama akinya, dan
                    // sampai 2026-08-06 tak pernah terlihat di HP sama sekali.
                    listOfNotNull(
                        "Charger".takeIf { form.ambilCharger },
                        "Kaca spion".takeIf { form.ambilKacaSpion },
                    ).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            "Ikut diambil: ${it.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tujuan: " + akiTujuanLabel(form.tujuan) +
                            (form.tujuanLainnya.isiAtauNull()?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    form.keterangan.isiAtauNull()?.let {
                        Text("“$it”", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // ── SPK di baliknya: untuk unit & konsumen yang mana ────────────
            Spacer(Modifier.height(8.dp))
            Text(
                form.jobCustomerNama.isiAtauNull() ?: "Konsumen tidak tercatat di form ini",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            listOfNotNull(
                form.jobNamaBarang.isiAtauNull(),
                form.jobKategori.isiAtauNull(),
                cabang,
            ).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Diambil " + (form.pengambilNama.isiAtauNull() ?: "-") +
                    (form.pengambilJabatan.isiAtauNull()?.let { " ($it)" } ?: "") +
                    listOfNotNull(form.tanggal.isiAtauNull(), form.jam.isiAtauNull())
                        .takeIf { it.isNotEmpty() }?.joinToString(" ", prefix = " · ").orEmpty(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            // Foto bukti aki (2026-07-24). TIGA keadaan sengaja dibedakan: ada,
            // gagal dimuat, dan memang tak ada. Sebelum 2026-07-29 ketiganya
            // sama-sama menghasilkan kartu tanpa gambar, sehingga approver tak
            // bisa tahu apakah PDI lalai memotret atau filenya hilang dari
            // server — dan 5 foto memang benar-benar hilang.
            Spacer(Modifier.height(8.dp))
            when (photo) {
                // Bisa ditekan untuk melihat ukuran penuh — thumbnail 140dp
                // ber-Crop tak cukup untuk membaca nomor seri aki.
                is AkiPhotoState.Ada ->
                    BuktiFotoThumbnail(
                        bitmap = photo.bitmap,
                        deskripsi = "Foto bukti aki",
                        modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp))
                    )
                is AkiPhotoState.Memuat ->
                    AkiPhotoNotice("Memuat foto bukti…", MaterialTheme.colorScheme.onSurfaceVariant)
                is AkiPhotoState.Gagal ->
                    AkiPhotoNotice(
                        "Foto bukti gagal dimuat — file tidak ada di server atau jaringan putus.",
                        MaterialTheme.colorScheme.error
                    )
                // Tak ada entri = form ini memang tanpa photoUrl.
                null -> AkiPhotoNotice(
                    "Tanpa foto bukti (form dibuat sebelum foto diwajibkan).",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Status approver pusat (redesain 2026-07-24, dulu 3 slot).
            if (!rejected) {
                Spacer(Modifier.height(8.dp))
                SlotChip("Approver", form.akiApproverApprovedNama, Modifier.fillMaxWidth())
            }
            // Aki bekas: jejak logistik yang menentukan perlu-tidaknya tombol di
            // bawah — approver sering hanya ingin tahu ini sudah beres atau belum.
            if (sudah || form.akiBekasJumlah != null || form.akiBekasKeterangan.isiAtauNull() != null) {
                Text(
                    listOfNotNull(
                        "Aki bekas: " + if (sudah) "sudah dikembalikan" else "belum dikembalikan",
                        form.akiBekasJumlah?.let { "$it pcs" },
                        form.akiBekasKeterangan.isiAtauNull(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sudah) Color(0xFF12B76A) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            // Ditolak → tampilkan alasan, tanpa aksi.
            if (rejected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ditolak" + (form.rejectedByNama.isiAtauNull()?.let { " oleh $it" } ?: "") +
                        (form.rejectedReason.isiAtauNull()?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            }
            // Belum disetujui (dan belum ditolak) → tombol SETUJUI / TOLAK HANYA
            // utk approver (canApprove — SpkAccessPolicy); pembaca lain (PDI)
            // cuma lihat status. Berlabel teks + ikon dan bertarget sentuh lega,
            // meniru baris keputusan diskon: dua tombol ikon tanpa label pernah
            // membuat "tolak" dan "setujui" beda beberapa milimeter saja.
            else if (!approved && canApprove) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExpressiveOutlinedButton(
                        onClick = onReject, enabled = !submitting, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Tolak", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    ExpressiveFilledButton(
                        onClick = onApprove, enabled = !submitting, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Setujui", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Aksi logistik TERPISAH dari chain approval: tandai aki bekas
            // dikembalikan — berlaku utk form approved MAUPUN rejected (backend
            // mark_return tak mensyaratkan approved; aki yang terlanjur diambil
            // pada form rejected tetap harus tercatat kembalinya — review 2026-07-23).
            if ((approved || rejected) && !sudah && canReturn) {
                Spacer(Modifier.height(10.dp))
                ExpressiveFilledButton(onClick = onMarkReturned, enabled = !submitting, modifier = Modifier.fillMaxWidth()) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Tandai Dikembalikan")
                }
            }
            // Form aki cuma memuat potongan SPK-nya (unit + konsumen). Detail utuh
            // dimuat on-demand — pola sama kartu diskon. Disembunyikan kalau
            // deliveryJobId kosong: tombol yang membuka layar kosong lebih buruk
            // daripada tombol yang tak ada.
            if (form.deliveryJobId.isNotBlank()) {
                TextButton(onClick = onDetailSpk, modifier = Modifier.align(Alignment.Start)) {
                    Text("Lihat detail SPK", style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** Chip status approval (approver pusat tunggal): nama approver bila sudah, "menunggu" bila belum. */
@Composable
private fun SlotChip(label: String, approvedBy: String?, modifier: Modifier = Modifier) {
    val done = !approvedBy.isNullOrBlank()
    val color = if (done) Color(0xFF12B76A) else Color(0xFFB5670C)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (done) "✓ ${approvedBy!!.trim()}" else "menunggu",
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ApprovalBadge(approved: Boolean, rejected: Boolean = false) {
    val color = when {
        rejected -> Color(0xFFD92D20)
        approved -> Color(0xFF12B76A)
        else -> Color(0xFFB5670C)
    }
    val label = when {
        rejected -> "Ditolak"
        approved -> "Disetujui"
        else -> "Menunggu approval"
    }
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

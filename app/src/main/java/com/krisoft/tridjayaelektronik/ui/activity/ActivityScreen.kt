package com.krisoft.tridjayaelektronik.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.krisoft.tridjayaelektronik.ui.home.NotificationPermissionBanner
import com.krisoft.tridjayaelektronik.ui.home.findLifecycle
import com.krisoft.tridjayaelektronik.ui.notifications.NotificationCenterViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledIconButton
import com.krisoft.tridjayaelektronik.ui.theme.SkeletonBox
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Layar pertama app — menjawab satu pertanyaan: "hari ini aku harus ngapain?".
 *
 * Tak ada layar error global di sini: seksi HARI INI & BUAT BARU tak butuh
 * jaringan (absensi dari cache VM, prospek dari Room, SPK dari SharedPreferences),
 * jadi kegagalan jaringan hanya membuat kartu antrian yang bersangkutan
 * bertanda "—" dan bisa ditap untuk memuat ulang. Pull-to-refresh (spec §5)
 * membuang cache 60 detik dengan `refresh(force = true)`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    onOpen: (navKey: String) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAllMenus: () -> Unit,
    // Sisa I1 audit merge-gate: dinaikkan MainScreen tiap tab BERUBAH JADI Activity murni
    // (Activity↔Ringkasan tanpa apa pun lainnya) — kasus yang dulu tak memicu apa pun karena
    // kedua tab tetap ter-compose (`MainScreen` menjaganya hidup, cuma alpha yang berubah,
    // lihat MainActivity.kt). Nilai awal 0 SENGAJA tak memicu apa pun (guard `> 0` di bawah)
    // supaya komposisi pertama layar ini tak fetch dobel dgn `LaunchedEffect(Unit)` di bawah.
    tabSelectedSignal: Int = 0,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val bottomClearance =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    val notifViewModel: NotificationCenterViewModel = hiltViewModel()
    val notifState by notifViewModel.state.collectAsState()
    LaunchedEffect(Unit) { notifViewModel.refreshUnreadCount() }

    // I1 audit 2026-07-28: masuk layar ini = "refresh saat masuk layar" (spec §5),
    // jadi paksa lewati cache 60 detik. `LaunchedEffect(Unit)` cuma jalan sekali per
    // masuknya komposisi ini — tak cukup sendirian karena `MainScreen` (tab
    // kept-alive) bisa membuatnya tak pernah dikomposisi ulang.
    LaunchedEffect(Unit) { viewModel.refresh(force = true) }
    // ON_RESUME menyusul: app di-resume dari background ATAU layar ini kembali
    // jadi top-of-backstack setelah pop dari layar anak (absen/PDI/kasir/dst,
    // semuanya berbagi NavHost yang sama — lihat `ActivityNavHost`). Di sinilah
    // cache 60 detik (`ActivityViewModel.CACHE_WINDOW_MS`) akhirnya berguna
    // (mencegah badai request saat bolak-balik cepat), bukan mati tak terpakai
    // seperti sebelumnya. Pola sama `DeliveryFlowScreens.kt` (`GpsStatusRow`).
    //
    // F1 audit final-fix-3 (2026-07-28): WAJIB lifecycle ACTIVITY, BUKAN
    // `LocalLifecycleOwner.current` — ActivityScreen duduk di posisi komposisi
    // yang persis sama dengan bug yang sudah terdokumentasi di
    // `NotificationPermissionBanner.findLifecycle()`: di dalam NavHost + tab
    // kept-alive, LocalLifecycleOwner di sini = NavBackStackEntry yang bisa
    // mandek di STARTED dan tak pernah kirim ON_RESUME lagi → observer diam
    // permanen (absen/PDI/kasir yang baru selesai tak lagi memicu refresh).
    // Reuse helper yang sama, jangan duplikasi.
    val context = LocalContext.current
    val activityLifecycle = remember(context) { context.findLifecycle() }
    DisposableEffect(activityLifecycle) {
        if (activityLifecycle == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh(force = false)
        }
        activityLifecycle.addObserver(observer)
        onDispose { activityLifecycle.removeObserver(observer) }
    }
    // Sisa I1: tukar tab murni Activity↔Ringkasan tak memicu ON_RESUME (Activity tak pernah
    // di-pause, cuma tabnya disembunyikan) — `tabSelectedSignal` menutup celah itu. `force =
    // false` (bukan true) SENGAJA: bolak-balik tab cepat memakai cache 60 detik yang sama
    // dengan ON_RESUME, bukan memicu badai fetch/fan-out tiap tap. Guard `> 0` mencegah efek
    // ini ikut jalan di komposisi pertama (nilai awal signal = 0) — sudah ditangani
    // `LaunchedEffect(Unit)` di atas dengan force=true.
    LaunchedEffect(tabSelectedSignal) { if (tabSelectedSignal > 0) viewModel.refresh(force = false) }

    TridjayaCollapsibleHeader(
        title = "Activity",
        actions = {
            ExpressiveFilledIconButton(onClick = onOpenNotifications) {
                BadgedBox(badge = {
                    if (notifState.unreadCount > 0) {
                        Badge { Text(if (notifState.unreadCount > 99) "99+" else "${notifState.unreadCount}") }
                    }
                }) { Icon(Icons.Rounded.Notifications, contentDescription = "Notifikasi") }
            }
            Spacer(Modifier.size(8.dp))
            ExpressiveFilledIconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, contentDescription = "Pengaturan")
            }
        }
    ) { contentModifier ->
        val pullState = rememberPullToRefreshState()
        // Minor 4 audit final-fix-2: `state.isLoading` menutup load PERTAMA maupun
        // refresh susulan sekaligus — memakainya langsung bikin indikator tarik-turun
        // muncul menumpuk DI ATAS skeleton saat load pertama (dua penanda "sedang
        // memuat" sekaligus). `userName` baru terisi setelah minimal satu load sukses,
        // jadi "isLoading DAN sudah pernah punya data" = refresh beneran, bukan load
        // pertama (skeleton sudah cukup mewakili itu).
        val isRefreshing = state.isLoading && state.userName.isNotBlank()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh(force = true) },
            state = pullState,
            modifier = contentModifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullState,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { NotificationPermissionBanner() }
                item { GreetingRow(state.userName, state.cabangName) }

                item { SectionTitle("HARI INI", trailing = state.progress) }
                items(state.tasks, key = { it.item.id }) { task ->
                    DailyTaskRow(task) { if (!task.item.comingSoon) onOpen(task.item.navKey) }
                }

                // I4 audit 2026-07-28: judul seksi tanpa isi menggantung permanen buat
                // sebagian persona (mis. manager/owner tak pernah punya BUAT BARU).
                // Sembunyikan HANYA saat benar-benar kosong DAN sudah selesai memuat —
                // render pertama & skeleton tetap butuh judulnya supaya tak melompat
                // begitu data datang.
                if (state.isLoading || state.queueCards.isNotEmpty()) {
                    item { SectionTitle("PERLU TINDAKAN") }
                    if (state.isLoading && state.queueCards.isEmpty()) {
                        items(3) {
                            SkeletonBox(
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                shape = RoundedCornerShape(20.dp)
                            )
                        }
                    } else {
                        items(state.queueCards, key = { it.item.id }) { card ->
                            QueueRow(card) {
                                if (card.failed) viewModel.refresh(force = true) else onOpen(card.item.navKey)
                            }
                        }
                    }
                }

                if (state.isLoading || state.actions.isNotEmpty()) {
                    item { SectionTitle("BUAT BARU") }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.actions.forEach { action ->
                                AssistChip(
                                    onClick = { onOpen(action.navKey) },
                                    label = {
                                        val extra = if (action.id == "buat_spk" && state.spkToday > 0) {
                                            " · ${state.spkToday} hari ini"
                                        } else ""
                                        Text(action.label + extra)
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Semua menu →",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenAllMenus)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingRow(name: String, cabang: String) {
    // Minor (review): dulu `Calendar.getInstance()`/`SimpleDateFormat` dibuat
    // ulang tiap recomposition (state ini sering berubah tiap tick load) —
    // `remember` pola sama `GreetingCard` di `HomeScreen.kt`.
    val cal = remember { Calendar.getInstance() }
    val jam = remember { cal.get(Calendar.HOUR_OF_DAY) }
    val sapaan = when {
        jam < 11 -> "Selamat pagi"
        jam < 15 -> "Selamat siang"
        jam < 18 -> "Selamat sore"
        else -> "Selamat malam"
    }
    val tanggal = remember {
        SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID")).format(cal.time)
    }
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            "$sapaan${if (name.isNotBlank()) ", $name" else ""}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            listOf(tanggal, cabang).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String, trailing: String = "") {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DailyTaskRow(task: DailyTask, onClick: () -> Unit) {
    // Item "SEGERA" tetap tampil supaya rutinitas yang akan datang terlihat,
    // tapi redup & tak bisa ditap — belum ada layarnya.
    val redup = task.item.comingSoon
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (redup) 0.5f else 1f)
            .then(if (redup) Modifier else Modifier.clickable(onClick = onClick))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (task.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (task.done) Color(0xFF12B76A) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(task.item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    task.item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                task.detail,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QueueRow(card: ActivityCard, onClick: () -> Unit) {
    // Kartu bernilai 0 SENGAJA tetap tampil (redup): menyembunyikannya bikin
    // "menuku hilang ke mana" dan menghapus rasa "semua beres".
    val kosong = !card.failed && (card.count ?: 0) == 0
    ClayCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (kosong) 0.55f else 1f)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (card.failed) "Gagal memuat — ketuk untuk coba lagi" else card.item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = CircleShape,
                color = if (kosong || card.failed) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.defaultMinSize(minWidth = 32.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        card.count?.toString() ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (kosong || card.failed) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

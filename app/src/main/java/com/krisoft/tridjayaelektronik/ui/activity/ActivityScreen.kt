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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.ui.home.NotificationPermissionBanner
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
 * bertanda "—" dan bisa ditap untuk memuat ulang.
 */
@Composable
fun ActivityScreen(
    onOpen: (navKey: String) -> Unit,
    onSettingsClick: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAllMenus: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val bottomClearance =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 104.dp

    val notifViewModel: NotificationCenterViewModel = hiltViewModel()
    val notifState by notifViewModel.state.collectAsState()
    LaunchedEffect(Unit) { notifViewModel.refreshUnreadCount() }
    // Tab tetap hidup; ini menyegarkan angka saat user kembali ke Activity.
    LaunchedEffect(Unit) { viewModel.refresh(force = false) }

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
        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { NotificationPermissionBanner() }
            item { GreetingRow(state.userName, state.cabangName) }

            item { SectionTitle("HARI INI", trailing = state.progress) }
            items(state.tasks, key = { it.item.id }) { task ->
                DailyTaskRow(task) { if (!task.item.comingSoon) onOpen(task.item.navKey) }
            }

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

@Composable
private fun GreetingRow(name: String, cabang: String) {
    val jam = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val sapaan = when {
        jam < 11 -> "Selamat pagi"
        jam < 15 -> "Selamat siang"
        jam < 18 -> "Selamat sore"
        else -> "Selamat malam"
    }
    val tanggal = SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID"))
        .format(Calendar.getInstance().time)
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

package com.krisoft.tridjayaelektronik.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.data.model.EventDto

/** Tinggi tetap: halaman pager harus seukuran satu sama lain, jadi tak boleh ikut panjang deskripsi. */
private val TINGGI_KARTU = 132.dp

private val GRADIEN_EVENT = listOf(Color(0xFF7C4DFF), Color(0xFFD81B60))

/**
 * Kartu event yang MENGGANTIKAN kartu sapaan di puncak tab Operasional. Dipanggil hanya
 * saat `EventUiState.kartuEvent` tidak kosong — keputusan tampil/tidak ada di ViewModel,
 * bukan di sini, supaya cuma ada satu tempat yang menjawabnya.
 *
 * >1 event → pager horizontal + indikator titik (pola [com.krisoft.tridjayaelektronik.ui.birthday]
 * `BirthdayPopup`); 1 event → satu kartu tanpa pager, karena pager berisi satu halaman hanya
 * menambah gestur yang tak melakukan apa-apa.
 */
@Composable
fun EventCarousel(events: List<EventDto>, onOpen: (EventDto) -> Unit, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    if (events.size == 1) {
        EventCard(events.first(), onOpen, modifier.fillMaxWidth().padding(top = 6.dp))
        return
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        val pager = rememberPagerState { events.size }
        HorizontalPager(state = pager, pageSpacing = 10.dp) { halaman ->
            EventCard(events[halaman], onOpen, Modifier.fillMaxWidth())
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            repeat(events.size) { i ->
                val aktif = i == pager.currentPage
                Box(
                    Modifier
                        .size(if (aktif) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (aktif) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                )
            }
        }
    }
}

@Composable
private fun EventCard(event: EventDto, onOpen: (EventDto) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .height(TINGGI_KARTU)
            .clip(shape)
            .background(Brush.linearGradient(GRADIEN_EVENT))
            .clickable { onOpen(event) }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 14.dp)
                    .background(Color.White.copy(alpha = 0.94f), CircleShape)
                    .size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Celebration,
                    contentDescription = null,
                    tint = Color(0xFF7C4DFF),
                    modifier = Modifier.size(27.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EVENT BERLANGSUNG",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.nama,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                event.deskripsi?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ketuk untuk catat calon konsumen",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

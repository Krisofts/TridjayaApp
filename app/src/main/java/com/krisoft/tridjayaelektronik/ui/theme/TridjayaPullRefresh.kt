package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tarik-turun (pull-to-refresh) standar app. Membungkus [PullToRefreshBox] dengan indikator
 * berwarna tema, supaya 20+ layar tak menyalin blok yang sama.
 *
 * Dua hal yang mudah salah dan karena itu dipusatkan di sini:
 *
 * 1. **[isRefreshing] harus "sedang memuat DAN sudah punya data".** Kebanyakan ViewModel di app
 *    ini memakai satu flag `isLoading` untuk load PERTAMA maupun refresh susulan. Memakainya
 *    mentah membuat indikator tarik-turun muncul menumpuk di atas skeleton saat layar pertama
 *    dibuka — dua penanda "sedang memuat" sekaligus. Pemanggil wajib menghitung sendiri
 *    (mis. `state.isLoading && state.items.isNotEmpty()`); asalnya dari `ActivityScreen`.
 *
 * 2. **Gestur hanya tertangkap kalau anaknya bisa di-scroll.** Membungkus `Box` polos (keadaan
 *    kosong/error) tidak menghasilkan apa-apa — persis di keadaan itulah orang paling ingin
 *    menarik ("kok antrian saya belum masuk?"). Pakai [ScrollableCenter] untuk isi non-daftar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TridjayaPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = isRefreshing,
                state = pullState,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.primary
            )
        },
        content = { content() }
    )
}

/**
 * Wadah scrollable satu-isi untuk keadaan KOSONG / ERROR di dalam [TridjayaPullRefresh].
 *
 * `Box` biasa tidak bisa di-scroll, jadi nested-scroll tak pernah sampai ke indikator dan
 * tarik-turun mati diam-diam. `LazyColumn` berisi satu item selalu menerima gestur walau isinya
 * tak cukup panjang untuk digulir, jadi "antrian kosong" tetap bisa ditarik-segarkan.
 */
@Composable
fun ScrollableCenter(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillParentMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) { content() }
        }
    }
}

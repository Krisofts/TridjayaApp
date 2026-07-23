package com.krisoft.tridjayaelektronik.ui.notifications

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.NotificationDto
import com.krisoft.tridjayaelektronik.data.model.relativeTimeId
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/** Pusat Notifikasi — daftar penuh (judul, pesan, waktu relatif, dot unread), tap → tandai dibaca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    onBack: () -> Unit,
    viewModel: NotificationCenterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    TridjayaCollapsibleHeader(
        title = "Notifikasi",
        onBack = onBack,
        actions = {
            if (state.unreadCount > 0) {
                TextButton(onClick = viewModel::markAllRead) { Text("Tandai semua dibaca") }
            }
        }
    ) { contentModifier ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        when {
            state.loading && state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveErrorState(message = state.error ?: "Gagal memuat", onRetry = viewModel::load)
                }
            state.items.isEmpty() ->
                Box(contentModifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    ExpressiveEmptyState(
                        icon = {
                            Icon(
                                Icons.Rounded.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = "Belum ada notifikasi",
                        subtitle = "Notifikasi baru akan tampil di sini."
                    )
                }
            else -> {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = viewModel::load,
                    state = pullState,
                    modifier = contentModifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            modifier = Modifier.align(Alignment.TopCenter),
                            isRefreshing = state.loading,
                            state = pullState,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + navBottom),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.items, key = { it.id }) { notif ->
                            NotificationRow(notif, onClick = { viewModel.markRead(notif.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notif: NotificationDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            if (!notif.isRead) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp).size(8.dp)
                ) {}
                Spacer(modifier = Modifier.size(10.dp))
            } else {
                Spacer(modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notif.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold
                )
                notif.message?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = relativeTimeId(notif.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveEmptyState
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

@Composable
fun RankingListScreen(
    onBack: () -> Unit,
    onBranchClick: (LeaderboardBranchItemDto) -> Unit = {},
    onSalesClick: (LeaderboardSalesItemDto) -> Unit = {},
    viewModel: RankingListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val title = if (viewModel.kind == RankingKind.BRANCH) "Semua Cabang" else "Semua Sales"

    TridjayaCollapsibleHeader(title = title, onBack = onBack) { contentModifier ->
        Box(modifier = contentModifier) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.errorMessage != null && state.branches.isEmpty() && state.sales.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat peringkat.",
                            onRetry = viewModel::load
                        )
                    }
                }
                (if (viewModel.kind == RankingKind.BRANCH) state.branches else state.sales).isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        ExpressiveEmptyState(
                            icon = { Icon(Icons.Rounded.Leaderboard, contentDescription = null) },
                            title = "Belum ada peringkat",
                            subtitle = "Data peringkat belum tersedia untuk periode ini."
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                    ) {
                        if (viewModel.kind == RankingKind.BRANCH) {
                            itemsIndexed(state.branches, key = { _, branch -> branch.kodeDealer }) { index, branch ->
                                BranchRow(rank = index + 1, branch = branch, onClick = { onBranchClick(branch) })
                            }
                        } else {
                            itemsIndexed(state.sales, key = { _, sales -> sales.sourceCode }) { index, sales ->
                                SalesRow(rank = index + 1, sales = sales, onClick = { onSalesClick(sales) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = CircleShape, modifier = Modifier.padding(end = 12.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Text(text = "$rank", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BranchRow(rank: Int, branch: LeaderboardBranchItemDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankBadge(rank)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = branch.cabang, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${branch.totalTransaksi} transaksi", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = remember(branch.omset) { formatRupiah(branch.omset) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SalesRow(rank: Int, sales: LeaderboardSalesItemDto, onClick: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RankBadge(rank)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = sales.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = "${sales.totalQty} unit · ${sales.totalTransaksi} transaksi", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = remember(sales.revenue) { formatRupiah(sales.revenue) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatRupiah(value: Long): String {
    val text = value.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $text"
}

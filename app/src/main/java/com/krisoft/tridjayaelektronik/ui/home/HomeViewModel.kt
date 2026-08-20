package com.krisoft.tridjayaelektronik.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.model.ExecutiveKpiDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardBranchItemDto
import com.krisoft.tridjayaelektronik.data.model.LeaderboardSalesItemDto
import com.krisoft.tridjayaelektronik.data.model.MonthlyTargetDto
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.LeadSummary
import com.krisoft.tridjayaelektronik.domain.home.GetCrmSummaryUseCase
import com.krisoft.tridjayaelektronik.domain.home.GetHomeDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val user: UserDto? = null,
    val kpi: ExecutiveKpiDto? = null,
    val target: MonthlyTargetDto? = null,
    val topBranches: List<LeaderboardBranchItemDto> = emptyList(),
    val topSales: List<LeaderboardSalesItemDto> = emptyList(),
    val crmSummary: LeadSummary? = null,
    /** Kemampuan dari server (`GET /api/me/capabilities`) — sumber TUNGGAL gate
     *  menu. `null` = belum termuat / server lama / offline → gate jatuh ke
     *  daftar role lokal di registri (lihat `visibleQuickAccessMenus`). */
    val capabilities: Map<String, Boolean>? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeDashboardUseCase: GetHomeDashboardUseCase,
    private val getCrmSummaryUseCase: GetCrmSummaryUseCase,
    private val authRepository: com.krisoft.tridjayaelektronik.data.AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Lihat [segarkanKemampuan]. */
    private val penyegarKemampuan = PenyegarKemampuan(
        identitasToken = { authRepository.sidikTokenAkses },
        ambil = { authRepository.capabilities() },
    )

    init {
        // CRM summary is observed live from the same leads cache the Prospek tab uses, so the Home
        // "Ringkasan CRM" widget and the Prospek list always show the same numbers.
        viewModelScope.launch {
            getCrmSummaryUseCase.observe().collect { summary ->
                _uiState.update { it.copy(crmSummary = summary) }
            }
        }
        // Peta kemampuan TIDAK lagi diambil di sini: [loadDashboard] memintanya
        // begitu profil tiba, lewat [segarkanKemampuan]. Pengambilan pertama
        // tetap terjadi, bedanya ia kini terjadi LAGI saat hak akses berubah.
        loadDashboard()
    }

    fun loadDashboard(forceRefresh: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = getHomeDashboardUseCase(forceRefresh)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    user = result.user,
                    kpi = result.kpi,
                    target = result.target,
                    topBranches = result.topBranches,
                    topSales = result.topSales,
                    errorMessage = result.errorMessage
                )
            }
            // Profil di tangan → sidik akses bisa dihitung.
            //
            // Urutannya TIDAK load-bearing, dan pernah diberi alasan yang keliru
            // di sini ("supaya tak ada render dengan pasangan silang"). Alasan
            // itu tak berlaku: [segarkanKemampuan] menghitung sidiknya sinkron
            // lalu melempar `viewModelScope.launch` berisi panggilan jaringan,
            // jadi petanya mendarat ratusan milidetik kemudian pada urutan MANA
            // PUN — dan `it.copy(...)` di atas tak menyentuh `capabilities`,
            // jadi tak ada yang bisa saling menimpa. Boleh dipindah ke atas
            // tanpa mengubah apa pun; dibiarkan di sini karena tak ada untungnya
            // mengubah kode yang sudah benar.
            segarkanKemampuan(result.user)
        }
        // Refresh the leads cache (flush pending + network sync) so the reactive CRM summary above —
        // and the Prospek list — reflect the latest. Independent so a slow leads sync never blocks
        // the sales dashboard.
        viewModelScope.launch { runCatching { getCrmSummaryUseCase.sync(forceRefresh) } }
    }

    /**
     * Ambil ulang peta kemampuan bila kunci latch berubah — SIDIK AKSES [user]
     * berubah, ATAU token berotasi (lihat [kunciLatchKemampuan]).
     *
     * **Kenapa perlu.** `visibleQuickAccessMenus` menilai gerbangnya lewat
     * `gateAllows`, yang MENDAHULUKAN peta kemampuan dan fail-closed. Peta itu
     * dulu diambil sekali di `init`, sementara ViewModel ini di-scope ke
     * `NavBackStackEntry` tab kept-alive (`MainActivity.MainScreen` menjaga tiap
     * tab yang pernah dikunjungi tetap ter-compose) — jadi petanya hidup sampai
     * proses app mati. Akses yang baru diberi tak pernah membuka menunya, dan
     * akses yang dicabut tetap menampilkan menu yang lalu dijawab 403.
     *
     * Tak ada muat-ulang dashboard di sini (beda dari `ActivityViewModel`):
     * gerbang grid dihitung di composable dari `state`, jadi menulis peta baru ke
     * `_uiState` sudah cukup untuk merendernya ulang — angka KPI/ranking tak
     * dipengaruhi hak akses. Karena itu pula tak perlu perbandingan `peta ==`
     * seperti di sana: pengambilan ulang per rotasi token yang jawabannya sama
     * menghasilkan `HomeUiState` yang SAMA, dan `StateFlow` menelan emisi yang
     * nilainya tak berubah — nol recomposition.
     *
     * Penjaga badai-request dan penjaga peta-baik ada di [PenyegarKemampuan] —
     * baca KDoc-nya sebelum mengubah pemicu di sini.
     */
    private fun segarkanKemampuan(user: UserDto?) {
        val sidik = sidikAkses(user)
        viewModelScope.launch {
            val peta = penyegarKemampuan.segarkan(sidik) ?: return@launch
            _uiState.update { it.copy(capabilities = peta) }
        }
    }
}

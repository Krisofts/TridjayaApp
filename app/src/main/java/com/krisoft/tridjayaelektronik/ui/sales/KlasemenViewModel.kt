package com.krisoft.tridjayaelektronik.ui.sales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.SalesRepository
import com.krisoft.tridjayaelektronik.data.model.OmsetRowDto
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenEntity
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenMetric
import com.krisoft.tridjayaelektronik.domain.sales.KlasemenStandings
import com.krisoft.tridjayaelektronik.domain.sales.StandingRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KlasemenUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val rows: List<OmsetRowDto> = emptyList(),
    val entity: KlasemenEntity = KlasemenEntity.SALES,
    val metric: KlasemenMetric = KlasemenMetric.UNIT,
    val cutoffIso: String = KlasemenStandings.todayIso(),
    val search: String = "",
    /** Hasil agregasi standings — dihitung di Dispatchers.Default, bukan saat komposisi. */
    val standings: List<StandingRow> = emptyList()
)

/**
 * Interactive Klasemen (web /dashboard/klasemen parity): raw omset rows for the cutoff's month,
 * aggregated in the UI layer via [KlasemenStandings]. Entity switch resets the metric to the
 * web's default (sales → unit, cabang → omset); moving the cutoff across a month boundary
 * reloads that month's rows.
 */
@HiltViewModel
class KlasemenViewModel @Inject constructor(
    private val salesRepository: SalesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KlasemenUiState())
    val uiState: StateFlow<KlasemenUiState> = _uiState.asStateFlow()

    private var loadedPeriode: String? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        val periode = KlasemenStandings.periodeOf(_uiState.value.cutoffIso)
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = salesRepository.klasemenRows(periode, forceRefresh)) {
                is AuthResult.Success -> {
                    loadedPeriode = periode
                    _uiState.update { it.copy(isLoading = false, rows = result.data) }
                    recomputeStandings()
                }
                is AuthResult.Failure -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    /** Agregasi standings (dua pass penuh atas semua baris omset + sort) di Dispatchers.Default —
     *  sebelumnya dihitung dalam remember{} saat komposisi dan bisa memakan satu frame. Hasil basi
     *  tidak diterapkan bila input sudah berubah lagi sebelum perhitungan selesai. */
    private fun recomputeStandings() {
        val snapshot = _uiState.value
        viewModelScope.launch(Dispatchers.Default) {
            val standings = KlasemenStandings.standingsFor(
                snapshot.rows, snapshot.entity, snapshot.metric, snapshot.cutoffIso
            )
            _uiState.update { current ->
                if (current.rows === snapshot.rows && current.entity == snapshot.entity &&
                    current.metric == snapshot.metric && current.cutoffIso == snapshot.cutoffIso
                ) current.copy(standings = standings) else current
            }
        }
    }

    fun setEntity(entity: KlasemenEntity) {
        _uiState.update {
            it.copy(
                entity = entity,
                metric = if (entity == KlasemenEntity.SALES) KlasemenMetric.UNIT else KlasemenMetric.OMSET
            )
        }
        recomputeStandings()
    }

    fun setMetric(metric: KlasemenMetric) {
        _uiState.update { it.copy(metric = metric) }
        recomputeStandings()
    }

    fun setCutoff(cutoffIso: String) {
        val periodeChanged = KlasemenStandings.periodeOf(cutoffIso) != loadedPeriode
        _uiState.update { it.copy(cutoffIso = cutoffIso) }
        if (periodeChanged) load() else recomputeStandings()
    }

    fun setSearch(value: String) {
        _uiState.update { it.copy(search = value) }
    }
}

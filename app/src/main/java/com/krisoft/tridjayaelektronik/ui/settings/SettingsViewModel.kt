package com.krisoft.tridjayaelektronik.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.DeliveryFlowRepository
import com.krisoft.tridjayaelektronik.data.model.UserDto
import com.krisoft.tridjayaelektronik.data.update.UpdateManager
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import com.krisoft.tridjayaelektronik.domain.auth.GetProfileUseCase
import com.krisoft.tridjayaelektronik.domain.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val user: UserDto? = null,
    val errorMessage: String? = null,
    val checkingUpdate: Boolean = false,
    /** Non-null while an update-available dialog should be shown (from a manual "Cek Pembaruan"). */
    val updateAvailable: UpdateStatus.Available? = null,
    /** Transient toast text (e.g. "sudah versi terbaru"), consumed once shown. */
    val updateMessage: String? = null,
    /** Preferensi WA alur SPK: true = user matikan WA (dapat push app saja). */
    val spkWaOptout: Boolean = false,
    /** true selagi menyimpan toggle WA (cegah tap ganda). */
    val savingWaPref: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val updateManager: UpdateManager,
    private val deliveryFlowRepository: DeliveryFlowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val versionName: String get() = updateManager.currentVersionName
    val versionCode: Int get() = updateManager.currentVersionCode

    init {
        loadProfile()
        loadWaPref()
    }

    private fun loadWaPref() {
        viewModelScope.launch {
            val pref = deliveryFlowRepository.getWaPref()
            _uiState.update { it.copy(spkWaOptout = pref.spkWaOptout) }
        }
    }

    /** Toggle "Terima pesan WhatsApp (alur SPK)". `receive=true` = mau terima WA → optout=false. */
    fun setReceiveWa(receive: Boolean) {
        val optout = !receive
        // Optimistik: UI langsung berubah; rollback bila server gagal.
        val prev = _uiState.value.spkWaOptout
        _uiState.update { it.copy(spkWaOptout = optout, savingWaPref = true) }
        viewModelScope.launch {
            val ok = deliveryFlowRepository.setWaPref(optout)
            _uiState.update { it.copy(savingWaPref = false, spkWaOptout = if (ok) optout else prev) }
        }
    }

    fun checkUpdate() {
        _uiState.update { it.copy(checkingUpdate = true, updateAvailable = null, updateMessage = null) }
        viewModelScope.launch {
            when (val status = updateManager.check()) {
                is UpdateStatus.Available -> _uiState.update { it.copy(checkingUpdate = false, updateAvailable = status) }
                else -> _uiState.update { it.copy(checkingUpdate = false, updateMessage = "Aplikasi sudah versi terbaru") }
            }
        }
    }

    fun dismissUpdateDialog() = _uiState.update { it.copy(updateAvailable = null) }
    fun consumeUpdateMessage() = _uiState.update { it.copy(updateMessage = null) }

    fun loadProfile() {
        // Stale-while-revalidate: profil dari cache sesi tampil SEKETIKA (tersedia sinkron di
        // TokenStore) — tidak ada spinner saat buka Settings; network hanya menyegarkan
        // diam-diam di belakang. Spinner tinggal untuk kasus langka cache kosong.
        val cached = getProfileUseCase.cached()
        _uiState.update { it.copy(isLoading = cached == null && it.user == null, user = cached ?: it.user, errorMessage = null) }
        viewModelScope.launch {
            when (val result = getProfileUseCase()) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, user = result.data) }
                }
                is AuthResult.Failure -> {
                    // Profil cache sudah tampil — kegagalan refresh tidak perlu mengganggu layar;
                    // error hanya ditampilkan bila benar-benar tidak ada apa pun untuk dirender.
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = if (it.user == null) result.message else null)
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }
}

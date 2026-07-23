package com.krisoft.tridjayaelektronik.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.AuthResult
import com.krisoft.tridjayaelektronik.data.NotificationsRepository
import com.krisoft.tridjayaelektronik.data.model.NotificationDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationCenterUiState(
    val loading: Boolean = false,
    val items: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val error: String? = null
)

/**
 * Pusat Notifikasi — dipakai baik layar daftar penuh ([NotificationCenterScreen]) maupun badge unread
 * di Home (instance terpisah per pemakai; keduanya ringan lewat [NotificationsRepository]).
 */
@HiltViewModel
class NotificationCenterViewModel @Inject constructor(
    private val repository: NotificationsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationCenterUiState())
    val state: StateFlow<NotificationCenterUiState> = _state.asStateFlow()

    /** Layar daftar penuh. */
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.list()) {
                is AuthResult.Success -> _state.update {
                    it.copy(loading = false, items = res.data.first, unreadCount = res.data.second, error = null)
                }
                is AuthResult.Failure -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** Badge Home — hanya jumlah, tak mengganggu `items`/`loading` layar daftar. Fail-soft (biarkan 0 lama). */
    fun refreshUnreadCount() {
        viewModelScope.launch {
            (repository.unreadCount() as? AuthResult.Success)?.let { r ->
                _state.update { it.copy(unreadCount = r.data) }
            }
        }
    }

    /** Tandai satu dibaca (optimistic); no-op kalau sudah dibaca. */
    fun markRead(id: String) {
        val target = _state.value.items.find { it.id == id } ?: return
        if (target.isRead) return
        _state.update { st ->
            st.copy(
                items = st.items.map { if (it.id == id) it.copy(isRead = true) else it },
                unreadCount = (st.unreadCount - 1).coerceAtLeast(0)
            )
        }
        // Gagal → resync dari server (state optimistic di atas mungkin sudah salah).
        viewModelScope.launch { if (repository.markRead(id) is AuthResult.Failure) load() }
    }

    /** Tandai semua dibaca (optimistic). */
    fun markAllRead() {
        if (_state.value.unreadCount == 0) return
        _state.update { st -> st.copy(items = st.items.map { it.copy(isRead = true) }, unreadCount = 0) }
        // Gagal → resync dari server (state optimistic di atas mungkin sudah salah).
        viewModelScope.launch { if (repository.markAllRead() is AuthResult.Failure) load() }
    }
}

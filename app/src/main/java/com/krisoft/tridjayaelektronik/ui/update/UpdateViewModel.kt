package com.krisoft.tridjayaelektronik.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krisoft.tridjayaelektronik.data.update.UpdateDownloadState
import com.krisoft.tridjayaelektronik.data.update.UpdateManager
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Checks for an app update once at startup; the root UI gates a force update on the result.
 *  Mandatory updates auto-download the moment they're detected (see [check]'s call site in
 *  [init]) — optional ones wait for [startDownload] (the dialog's "Perbarui Sekarang" tap). */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    /** Set once an optional-update prompt has been dismissed so it doesn't reappear this session. */
    private val _optionalDismissed = MutableStateFlow(false)
    val optionalDismissed: StateFlow<Boolean> = _optionalDismissed.asStateFlow()

    private val _download = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val download: StateFlow<UpdateDownloadState> = _download.asStateFlow()

    init {
        viewModelScope.launch {
            val result = updateManager.check()
            _status.value = result
            // Mandatory update: don't wait for a tap, start pulling it down right away.
            if (result is UpdateStatus.Available && result.force) {
                startDownload()
            }
        }
    }

    fun dismissOptional() {
        _optionalDismissed.value = true
    }

    /** Starts (or re-fires the install prompt for) the update download. Safe to call while a
     *  download is already in flight — it's a no-op then. */
    fun startDownload() {
        val current = _download.value
        if (current is UpdateDownloadState.Downloading) return
        if (current is UpdateDownloadState.ReadyToInstall) {
            updateManager.promptInstall(current.fileUri)
            return
        }
        viewModelScope.launch {
            _download.value = UpdateDownloadState.Downloading(null)
            updateManager.downloadApk { progress -> _download.value = UpdateDownloadState.Downloading(progress) }
                .onSuccess { uri ->
                    _download.value = UpdateDownloadState.ReadyToInstall(uri)
                    updateManager.promptInstall(uri)
                }
                .onFailure { error ->
                    _download.value = UpdateDownloadState.Failed(error.message ?: "Gagal mengunduh pembaruan")
                }
        }
    }
}

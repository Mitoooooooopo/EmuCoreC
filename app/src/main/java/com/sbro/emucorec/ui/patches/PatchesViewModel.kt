package com.sbro.emucorec.ui.patches

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbro.emucorec.data.InstalledGameRepository
import com.sbro.emucorec.data.InstalledPs3Game
import com.sbro.emucorec.data.PatchRepository
import com.sbro.emucorec.data.Ps3PatchInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PatchesUiState(
    val games: List<InstalledPs3Game> = emptyList(),
    val selectedTitleId: String? = null,
    val patches: List<Ps3PatchInfo> = emptyList(),
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val patchesLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadResult: PatchRepository.DownloadResult? = null,
    val errorMessage: String? = null,
) {
    val selectedGame: InstalledPs3Game?
        get() = games.firstOrNull { it.titleId == selectedTitleId }
}

class PatchesViewModel(application: Application) : AndroidViewModel(application) {
    private val gameRepository = InstalledGameRepository()
    private val patchRepository = PatchRepository(application)

    private val _uiState = MutableStateFlow(PatchesUiState())
    val uiState: StateFlow<PatchesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(preferredTitleId: String? = null) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val games = withContext(Dispatchers.IO) { gameRepository.loadInstalledGames(context) }
            val titleId = preferredTitleId
                ?: _uiState.value.selectedTitleId
                ?: games.firstOrNull()?.titleId

            _uiState.update {
                it.copy(
                    games = games,
                    selectedTitleId = titleId,
                    isLoading = false,
                    hasLoadedOnce = true,
                    patchesLoading = true,
                    downloadResult = null,
                    errorMessage = null,
                )
            }

            if (titleId != null) {
                loadPatches(titleId)
            } else {
                _uiState.update { it.copy(patchesLoading = false) }
            }
        }
    }

    fun selectGame(titleId: String) {
        if (titleId == _uiState.value.selectedTitleId) return
        _uiState.update { it.copy(selectedTitleId = titleId, patches = emptyList(), patchesLoading = true) }
        loadPatches(titleId)
    }

    private fun loadPatches(serial: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(patchesLoading = true) }
            val patches = withContext(Dispatchers.IO) { patchRepository.listPatches(serial) }
            _uiState.update { it.copy(patches = patches, patchesLoading = false) }
        }
    }

    fun downloadPatches() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadResult = null) }
            val result = withContext(Dispatchers.IO) { patchRepository.downloadPatches() }
            _uiState.update { it.copy(isDownloading = false, downloadResult = result) }
            val currentSerial = _uiState.value.selectedTitleId
            if (currentSerial != null && result is PatchRepository.DownloadResult.Success) {
                loadPatches(currentSerial)
            }
        }
    }

    fun togglePatch(patch: Ps3PatchInfo) {
        val serial = _uiState.value.selectedTitleId ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                patchRepository.togglePatch(
                    hash = patch.hash,
                    name = patch.name,
                    serial = serial,
                    appVersion = patch.appVersion,
                    enabled = !patch.enabled
                )
            }
            if (success) {
                _uiState.update { state ->
                    state.copy(
                        patches = state.patches.map {
                            if (it.hash == patch.hash && it.name == patch.name) {
                                it.copy(enabled = !it.enabled)
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }
    }

    fun clearDownloadResult() {
        _uiState.update { it.copy(downloadResult = null) }
    }
}

package com.sbro.emucorec.ui.saves

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbro.emucorec.data.SaveDataBulkImportResult
import com.sbro.emucorec.data.SaveDataImportResult
import com.sbro.emucorec.data.SaveDataRepository
import com.sbro.emucorec.data.Ps3SaveDataEntry
import com.sbro.emucorec.data.Ps3SaveDataTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SaveDataUiState(
    val saves: List<Ps3SaveDataEntry> = emptyList(),
    val totalSaveCount: Int = 0,
    val query: String = "",
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false,
    val busySaveId: String? = null,
    val bulkBusy: Boolean = false,
    val focusTarget: Ps3SaveDataTarget? = null
)

class SaveDataViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SaveDataRepository()
    private var allSaves: List<Ps3SaveDataEntry> = emptyList()

    private val _uiState = MutableStateFlow(SaveDataUiState())
    val uiState: StateFlow<SaveDataUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(focusTitleId: String? = null) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val loaded = runCatching {
                val focusTarget = focusTitleId?.let { repository.targetForTitleId(context, it) }
                val saves = repository.list(context)
                saves to focusTarget
            }.getOrElse { error ->
                Log.e("SaveDataViewModel", "Save data scan failed", error)
                allSaves to _uiState.value.focusTarget
            }
            allSaves = loaded.first
            _uiState.value = _uiState.value.copy(focusTarget = loaded.second, hasLoadedOnce = true)
            publishState()
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        publishState()
    }

    fun delete(saveId: String, onComplete: (Boolean) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busySaveId = saveId)
            val deleted = runCatching { repository.delete(context, saveId) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data deletion failed", error)
                    false
                }
            if (deleted) {
                allSaves = runCatching { repository.list(context) }
                    .getOrElse { error ->
                        Log.e("SaveDataViewModel", "Save data scan failed", error)
                        allSaves
                    }
                publishState()
            }
            _uiState.value = _uiState.value.copy(busySaveId = null)
            withContext(Dispatchers.Main) { onComplete(deleted) }
        }
    }

    fun exportSave(saveId: String, destination: Uri, onComplete: (Result<Unit>) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busySaveId = saveId)
            val result = runCatching { repository.exportToZip(context, saveId, destination) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data export failed", error)
                    Result.failure(error)
                }
            _uiState.value = _uiState.value.copy(busySaveId = null)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    fun importSave(source: Uri, targetSaveId: String?, onComplete: (SaveDataImportResult) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(busySaveId = targetSaveId)
            val result = runCatching { repository.importFromZip(context, source, targetSaveId) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data import failed", error)
                    SaveDataImportResult.Failure(error)
                }
            allSaves = runCatching { repository.list(context) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data scan failed", error)
                    allSaves
                }
            publishState()
            _uiState.value = _uiState.value.copy(busySaveId = null)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    fun exportAllSaves(destination: Uri, onComplete: (Result<Int>) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(bulkBusy = true)
            val result = runCatching { repository.exportAllToZip(context, destination) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data bulk export failed", error)
                    Result.failure(error)
                }
            _uiState.value = _uiState.value.copy(bulkBusy = false)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    fun restoreAllSaves(source: Uri, onComplete: (SaveDataBulkImportResult) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(bulkBusy = true)
            val result = runCatching { repository.importAllFromZip(context, source) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data bulk restore failed", error)
                    SaveDataBulkImportResult.Failure(error)
                }
            allSaves = runCatching { repository.list(context) }
                .getOrElse { error ->
                    Log.e("SaveDataViewModel", "Save data scan failed", error)
                    allSaves
                }
            publishState()
            _uiState.value = _uiState.value.copy(bulkBusy = false)
            withContext(Dispatchers.Main) { onComplete(result) }
        }
    }

    private fun publishState() {
        val query = _uiState.value.query.trim()
        val focusTarget = _uiState.value.focusTarget
        val scopedSaves = if (focusTarget != null) {
            allSaves.filter { it.saveId == focusTarget.saveId || it.titleId == focusTarget.titleId }
        } else {
            allSaves
        }
        val filtered = scopedSaves.filter { save ->
            query.isBlank() ||
                save.title.contains(query, ignoreCase = true) ||
                save.saveId.contains(query, ignoreCase = true) ||
                save.titleId?.contains(query, ignoreCase = true) == true
        }
        _uiState.value = _uiState.value.copy(
            saves = filtered,
            totalSaveCount = allSaves.size,
            isLoading = false
        )
    }
}


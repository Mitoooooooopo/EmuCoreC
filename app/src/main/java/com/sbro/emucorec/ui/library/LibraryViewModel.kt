package com.sbro.emucorec.ui.library

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbro.emucorec.R
import com.sbro.emucorec.core.InstallStateBus
import com.sbro.emucorec.data.InstalledGameRepository
import com.sbro.emucorec.data.InstalledPs3Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val items: List<InstalledPs3Game> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val hasLoadedOnce: Boolean = false
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InstalledGameRepository()
    private var allItems: List<InstalledPs3Game> = emptyList()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            InstallStateBus.events.collect {
                refresh()
            }
        }
    }

    fun refresh() {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            allItems = runCatching { repository.loadInstalledGames(context) }
                .getOrElse { error ->
                    Log.e("LibraryViewModel", "Game library scan failed", error)
                    allItems
                }
            publishState()
        }
    }

    fun deleteInstalledGame(titleId: String, onComplete: (Boolean) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = runCatching { repository.deleteByTitleId(context, titleId) }
                .getOrElse { error ->
                    Log.e("LibraryViewModel", "Game deletion failed", error)
                    false
                }
            if (deleted) {
                allItems = runCatching { repository.loadInstalledGames(context) }
                    .getOrElse { error ->
                        Log.e("LibraryViewModel", "Game library scan failed", error)
                        allItems
                    }
                publishState()
                InstallStateBus.notifyCompleted()
            }
            withContext(Dispatchers.Main) {
                onComplete(deleted)
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        publishState()
    }

    private fun publishState() {
        val query = _uiState.value.query.trim()
        val filteredItems = allItems.filter {
            query.isBlank() ||
                it.title.contains(query, ignoreCase = true) ||
                it.titleId.contains(query, ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(
            items = filteredItems,
            isLoading = false,
            hasLoadedOnce = true
        )
    }

    fun addGameDirectory(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val preferences = com.sbro.emucorec.data.AppPreferences(context)
            preferences.addGameDirectory(uri.toString())

            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            allItems = runCatching { repository.loadInstalledGames(context) }
                .getOrElse { error ->
                    Log.e("LibraryViewModel", "Game library scan failed", error)
                    allItems
                }
            publishState()
            InstallStateBus.notifyCompleted()

            val displayName = com.sbro.emucorec.core.DocumentPathResolver.getDisplayName(context, uri.toString())
            withContext(Dispatchers.Main) {
                onResult(true, context.getString(R.string.direct_boot_added_success, displayName))
            }
        }
    }
}

package com.sbro.emucorec.ui.profile

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sbro.emucorec.R
import com.sbro.emucorec.data.ProfileCatalogGame
import com.sbro.emucorec.data.ProfileGameListRepository
import com.sbro.emucorec.data.ProfileGameStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProfileLayoutMode { GRID, LIST }

data class ProfileIdentityState(
    val displayName: String? = null,
    val avatarUri: String? = null,
)

data class ProfileUiState(
    val isLoading: Boolean = true,
    val layoutMode: ProfileLayoutMode = ProfileLayoutMode.GRID,
    val gamesByStatus: Map<ProfileGameStatus, List<ProfileCatalogGame>> = emptyMap(),
    val favoriteGames: List<ProfileCatalogGame> = emptyList(),
    val identity: ProfileIdentityState = ProfileIdentityState(),
) {
    val totalCount: Int
        get() = (gamesByStatus.values.flatten() + favoriteGames)
            .distinctBy { it.catalog.igdbId }
            .size

    val visibleStatuses: List<ProfileGameStatus>
        get() = ProfileGameStatus.entries.filter { gamesByStatus[it].orEmpty().isNotEmpty() }
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val repository = ProfileGameListRepository(application)
    private val _uiState = MutableStateFlow(
        ProfileUiState(identity = readIdentity())
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val games = repository.loadCatalogGames()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                gamesByStatus = games.byStatus(),
                favoriteGames = games.favorites(),
                identity = readIdentity(),
            )
        }
    }

    fun setLayoutMode(mode: ProfileLayoutMode) {
        _uiState.value = _uiState.value.copy(layoutMode = mode)
    }

    fun removeGame(igdbId: Long) = updateGames { repository.remove(igdbId) }
    fun setGameStatus(igdbId: Long, status: ProfileGameStatus) =
        updateGames { repository.setStatus(igdbId, status) }
    fun clearGameStatus(igdbId: Long) = updateGames { repository.clearStatus(igdbId) }
    fun setFavorite(igdbId: Long, favorite: Boolean) =
        updateGames { repository.setFavorite(igdbId, favorite) }

    fun updateLocalIdentity(displayName: String) {
        val name = displayName.trim().ifBlank { defaultName() }
        preferences.edit()
            .putString(KEY_DISPLAY_NAME, name)
            .apply()
        _uiState.value = _uiState.value.copy(
            identity = readIdentity(),
        )
    }

    fun setLocalAvatar(uri: String) {
        preferences.edit().putString(KEY_PROFILE_AVATAR_URI, uri).apply()
        _uiState.value = _uiState.value.copy(identity = readIdentity())
    }

    private fun updateGames(action: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            action()
            val games = repository.loadCatalogGames()
            _uiState.value = _uiState.value.copy(
                gamesByStatus = games.byStatus(),
                favoriteGames = games.favorites(),
            )
        }
    }

    private fun readIdentity(): ProfileIdentityState {
        val avatar = preferences.getString(KEY_PROFILE_AVATAR_URI, null)
        return ProfileIdentityState(
            displayName = preferences.getString(KEY_DISPLAY_NAME, defaultName()),
            avatarUri = avatar,
        )
    }

    private fun defaultName(): String = getApplication<Application>().getString(R.string.profile_default_name)

    private fun List<ProfileCatalogGame>.byStatus(): Map<ProfileGameStatus, List<ProfileCatalogGame>> =
        mapNotNull { game -> game.profile.status?.let { it to game } }
            .groupBy({ it.first }, { it.second })

    private fun List<ProfileCatalogGame>.favorites(): List<ProfileCatalogGame> =
        filter { it.profile.isFavorite }

    private companion object {
        const val PREFERENCES = "profile_preferences"
        const val KEY_PROFILE_AVATAR_URI = "profile_avatar_uri"
        const val KEY_DISPLAY_NAME = "profile_display_name"
    }
}

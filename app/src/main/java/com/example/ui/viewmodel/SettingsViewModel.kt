package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupImportPreview
import com.example.data.backup.BackupSerializer
import com.example.data.repository.TichuRepository
import com.example.domain.GameMode
import com.example.domain.ScoringEngine
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
  val isGuestMode: Boolean = false,
  val userDisplayName: String = "",
  val userEmail: String? = null,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val winThreshold: Int = ScoringEngine.DEFAULT_WIN_THRESHOLD,
  val hasActiveGameInProgress: Boolean = false,
  val isThreeTeamWinnerPending: Boolean = false,
  val statusMessage: String? = null,
  val isStatusError: Boolean = false,
)

class SettingsViewModel(
  private val repository: TichuRepository,
) : ViewModel() {

  private val feedbackState = MutableStateFlow<Pair<String?, Boolean>>(null to false)

  val uiState: StateFlow<SettingsUiState> = combine(
    repository.preferencesFlow,
    repository.currentSessionGameFlow,
    repository.activeQuickGameFlow,
    feedbackState,
  ) { prefs, sessionGame, quickGame, feedback ->
    val threeTeamPending = sessionGame?.mode == GameMode.THREE_TEAMS && sessionGame.winner != null
    val hasActiveQuick = quickGame != null && quickGame.rounds.isNotEmpty()
    val isHostSessionGame = sessionGame != null &&
      (sessionGame.ownerUid.isBlank() || sessionGame.ownerUid == repository.currentUserId)
    val hasActiveGame = isHostSessionGame || hasActiveQuick
    SettingsUiState(
      isGuestMode = repository.isGuestMode,
      userDisplayName = if (repository.isGuestMode) {
        "Guest mode"
      } else {
        repository.currentUserDisplayName.ifBlank {
          repository.currentUserEmail?.substringBefore("@") ?: "Signed-in Player"
        }
      },
      userEmail = repository.currentUserEmail,
      themeMode = prefs.themeMode,
      winThreshold = prefs.winThreshold,
      hasActiveGameInProgress = hasActiveGame,
      isThreeTeamWinnerPending = threeTeamPending && isHostSessionGame,
      statusMessage = feedback.first,
      isStatusError = feedback.second,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SettingsUiState(
      isGuestMode = repository.isGuestMode,
      userDisplayName = if (repository.isGuestMode) {
        "Guest mode"
      } else {
        repository.currentUserDisplayName.ifBlank {
          repository.currentUserEmail?.substringBefore("@") ?: "Signed-in Player"
        }
      },
      userEmail = repository.currentUserEmail,
    ),
  )

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch {
      runCatching { repository.setThemeMode(mode) }
    }
  }

  fun saveThreshold(newThreshold: Int) {
    val state = uiState.value
    if (newThreshold <= 0) {
      feedbackState.value = "Enter a positive whole number for the target." to true
      return
    }
    if (newThreshold == state.winThreshold) {
      feedbackState.value = "The target is already $newThreshold points." to false
      return
    }
    if (state.isThreeTeamWinnerPending) {
      feedbackState.value =
        "Start the next game or undo the winning round before changing the target." to true
      return
    }
    viewModelScope.launch {
      runCatching { repository.setWinThreshold(newThreshold) }
        .onSuccess { triggeredWinner ->
          feedbackState.value = if (triggeredWinner) {
            "Target saved. The game has a winner." to false
          } else {
            "Target saved: $newThreshold points." to false
          }
        }
        .onFailure { err ->
          feedbackState.value = (err.message ?: "Failed to save target.") to true
        }
    }
  }

  suspend fun exportBackupJson(): String = repository.exportBackupJson()

  fun parseImportPreview(json: String): Result<BackupImportPreview> =
    runCatching { BackupSerializer.decodeWithPreview(json) }

  fun confirmImport(preview: BackupImportPreview) {
    viewModelScope.launch {
      runCatching { repository.importDecodedBackup(preview.decoded) }
        .onSuccess {
          val label = if (preview.isPwaExport) "PWA data migrated" else "Backup restored"
          feedbackState.value = "$label successfully. ${preview.summaryLine()}" to false
        }
        .onFailure { err ->
          feedbackState.value = (err.message ?: "Failed to import data.") to true
        }
    }
  }

  fun importBackupJson(json: String) {
    viewModelScope.launch {
      runCatching { repository.importBackupJson(json) }
        .onSuccess {
          feedbackState.value = "Backup restored successfully." to false
        }
        .onFailure { err ->
          feedbackState.value = (err.message ?: "Failed to import backup JSON.") to true
        }
    }
  }

  fun showFeedback(message: String, isError: Boolean = false) {
    feedbackState.update { message to isError }
  }

  fun consumeStatus() {
    feedbackState.update { null to false }
  }
}

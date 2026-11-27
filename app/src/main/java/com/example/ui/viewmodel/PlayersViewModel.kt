package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.repository.TichuRepository
import com.example.domain.Game
import com.example.domain.Player
import com.example.domain.PlayerInvite
import com.example.domain.PlayerStats
import com.example.domain.RankedPlayerEntry
import com.example.domain.RankingMetric
import com.example.domain.Session
import com.example.domain.StatsCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayersUiState(
  val isLoading: Boolean = true,
  val isGuestMode: Boolean = false,
  val currentUserId: String = "",
  val rankingSort: RankingMetric = RankingMetric.TOTAL_POINTS,
  val rankedActivePlayers: List<RankedPlayerEntry> = emptyList(),
  val archivedPlayers: List<Player> = emptyList(),
  val playersById: Map<String, Player> = emptyMap(),
  val selfLinkedPlayerId: String? = null,
  val selectedPlayer: Player? = null,
  val selectedPlayerStats: PlayerStats? = null,
  val generatedInvite: PlayerInvite? = null,
  val isInviteBusy: Boolean = false,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val archivedSuccessNavigateBack: Boolean = false,
)

private data class LocalPlayersUiState(
  val generatedInvite: PlayerInvite? = null,
  val isInviteBusy: Boolean = false,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val archivedNavigateBack: Boolean = false,
)

class PlayersViewModel(
  private val repository: TichuRepository,
  private val selectedPlayerId: String? = null,
) : ViewModel() {

  private val localState = MutableStateFlow(LocalPlayersUiState())

  private val repoBundle = combine(
    repository.playersFlow,
    repository.sessionsFlow,
    repository.gamesFlow,
    repository.preferencesFlow,
  ) { players, sessions, games, prefs ->
    RepoBundle(players, sessions, games, prefs)
  }

  val uiState: StateFlow<PlayersUiState> = combine(
    repoBundle,
    localState,
  ) { bundle, local ->
    buildUiState(bundle, local)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = PlayersUiState(
      isGuestMode = repository.isGuestMode,
      currentUserId = repository.currentUserId,
    ),
  )

  private data class RepoBundle(
    val players: List<Player>,
    val sessions: List<Session>,
    val games: List<Game>,
    val prefs: AppPreferences,
  )

  private fun buildUiState(
    bundle: RepoBundle,
    local: LocalPlayersUiState,
  ): PlayersUiState {
    val gamesBySession = bundle.games.filter { it.sessionId != null }.groupBy { it.sessionId!! }

    val activeWithStats = bundle.players
      .filter { it.active }
      .map { p ->
        p to StatsCalculator.computePlayerAllTimeStats(
          player = p,
          playerId = p.id,
          allSessions = bundle.sessions,
          gamesBySessionId = gamesBySession,
        )
      }

    val ranked = StatsCalculator.rankPlayers(activeWithStats, bundle.prefs.playersRankingSort)
    val archived = bundle.players.filter { !it.active }.sortedBy { it.name.lowercase() }
    val playersById = bundle.players.associateBy { it.id }

    val detailPlayer = selectedPlayerId?.let { id -> playersById[id] }
    val detailStats = detailPlayer?.let { p ->
      StatsCalculator.computePlayerAllTimeStats(
        player = p,
        playerId = p.id,
        allSessions = bundle.sessions,
        gamesBySessionId = gamesBySession,
      )
    }

    val selfLinkedPlayerId = if (repository.isGuestMode) {
      null
    } else {
      bundle.players.firstOrNull {
        it.ownerUid == repository.currentUserId && it.linkedUid == repository.currentUserId
      }?.id
    }

    return PlayersUiState(
      isLoading = false,
      isGuestMode = repository.isGuestMode,
      currentUserId = repository.currentUserId,
      rankingSort = bundle.prefs.playersRankingSort,
      rankedActivePlayers = ranked,
      archivedPlayers = archived,
      playersById = playersById,
      selfLinkedPlayerId = selfLinkedPlayerId,
      selectedPlayer = detailPlayer,
      selectedPlayerStats = detailStats,
      generatedInvite = local.generatedInvite,
      isInviteBusy = local.isInviteBusy,
      statusMessage = local.statusMessage,
      errorMessage = local.errorMessage,
      archivedSuccessNavigateBack = local.archivedNavigateBack,
    )
  }

  fun addPlayer(name: String, onAdded: () -> Unit = {}) {
    viewModelScope.launch {
      runCatching { repository.addPlayer(name) }
        .onSuccess { onAdded() }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message ?: "Could not add player.") } }
    }
  }

  fun claimPlayerInvite(code: String, onClaimed: () -> Unit = {}) {
    if (localState.value.isInviteBusy) return
    localState.update { it.copy(isInviteBusy = true, errorMessage = null) }
    viewModelScope.launch {
      repository.claimPlayerInvite(code)
        .onSuccess { playerName ->
          localState.update {
            it.copy(
              isInviteBusy = false,
              statusMessage = "Linked to $playerName! Their games and stats are now synced.",
            )
          }
          onClaimed()
        }
        .onFailure { err ->
          localState.update {
            it.copy(
              isInviteBusy = false,
              errorMessage = err.message ?: "Could not claim invite code.",
            )
          }
        }
    }
  }

  fun generateInviteForSelectedPlayer() {
    val id = selectedPlayerId ?: return
    if (localState.value.isInviteBusy) return
    localState.update { it.copy(isInviteBusy = true, errorMessage = null) }
    viewModelScope.launch {
      repository.createPlayerInvite(id)
        .onSuccess { invite ->
          localState.update { it.copy(isInviteBusy = false, generatedInvite = invite) }
        }
        .onFailure { err ->
          localState.update {
            it.copy(
              isInviteBusy = false,
              errorMessage = err.message ?: "Could not create invite code.",
            )
          }
        }
    }
  }

  fun dismissGeneratedInvite() {
    localState.update { it.copy(generatedInvite = null) }
  }

  fun linkSelfToSelectedPlayer() {
    val id = selectedPlayerId ?: return
    if (localState.value.isInviteBusy) return
    val playerName = uiState.value.selectedPlayer?.name ?: "this player"
    localState.update { it.copy(isInviteBusy = true, errorMessage = null) }
    viewModelScope.launch {
      repository.linkSelfToPlayer(id)
        .onSuccess {
          localState.update {
            it.copy(
              isInviteBusy = false,
              statusMessage = "Linked your account to $playerName.",
            )
          }
        }
        .onFailure { err ->
          localState.update {
            it.copy(
              isInviteBusy = false,
              errorMessage = err.message ?: "Could not link your account.",
            )
          }
        }
    }
  }

  fun unlinkSelectedPlayer() {
    val id = selectedPlayerId ?: return
    if (localState.value.isInviteBusy) return
    localState.update { it.copy(isInviteBusy = true) }
    viewModelScope.launch {
      repository.unlinkPlayer(id)
        .onSuccess {
          val isHost = uiState.value.selectedPlayer?.ownerUid == repository.currentUserId
          localState.update {
            it.copy(
              isInviteBusy = false,
              statusMessage = if (isHost) "Account unlinked from player." else "Unlinked from player.",
              archivedNavigateBack = !isHost,
            )
          }
        }
        .onFailure { err ->
          localState.update {
            it.copy(isInviteBusy = false, errorMessage = err.message ?: "Could not unlink account.")
          }
        }
    }
  }

  fun unarchivePlayer(id: String) {
    viewModelScope.launch {
      runCatching { repository.setPlayerActive(id, true) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun deleteArchivedPlayer(id: String) {
    viewModelScope.launch {
      runCatching { repository.deleteArchivedPlayer(id) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message ?: "Could not delete player.") } }
    }
  }

  fun setRankingSort(metric: RankingMetric) {
    viewModelScope.launch {
      repository.setPlayersRankingSort(metric)
    }
  }

  fun renameSelectedPlayer(newName: String) {
    val id = selectedPlayerId ?: return
    viewModelScope.launch {
      runCatching { repository.renamePlayer(id, newName) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message ?: "Could not rename player.") } }
    }
  }

  fun resetSelectedPlayerStats() {
    val id = selectedPlayerId ?: return
    viewModelScope.launch {
      runCatching { repository.resetPlayerStatistics(id) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun archiveSelectedPlayer() {
    val id = selectedPlayerId ?: return
    viewModelScope.launch {
      runCatching { repository.setPlayerActive(id, false) }
        .onSuccess { localState.update { it.copy(archivedNavigateBack = true) } }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun consumeStatus() {
    localState.update { it.copy(statusMessage = null) }
  }

  fun consumeError() {
    localState.update { it.copy(errorMessage = null) }
  }
}

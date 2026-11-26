package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.repository.TichuRepository
import com.example.domain.Game
import com.example.domain.Player
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
  val rankingSort: RankingMetric = RankingMetric.TOTAL_POINTS,
  val rankedActivePlayers: List<RankedPlayerEntry> = emptyList(),
  val archivedPlayers: List<Player> = emptyList(),
  val selectedPlayer: Player? = null,
  val selectedPlayerStats: PlayerStats? = null,
  val errorMessage: String? = null,
  val archivedSuccessNavigateBack: Boolean = false,
)

class PlayersViewModel(
  private val repository: TichuRepository,
  private val selectedPlayerId: String? = null,
) : ViewModel() {

  private val errorState = MutableStateFlow<String?>(null)
  private val archivedNavigateBack = MutableStateFlow(false)

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
    errorState,
    archivedNavigateBack,
  ) { bundle, error, navBack ->
    buildUiState(bundle, error, navBack)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = PlayersUiState(),
  )

  private data class RepoBundle(
    val players: List<Player>,
    val sessions: List<Session>,
    val games: List<Game>,
    val prefs: AppPreferences,
  )

  private fun buildUiState(
    bundle: RepoBundle,
    error: String?,
    navBack: Boolean,
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

    val detailPlayer = selectedPlayerId?.let { id -> bundle.players.firstOrNull { it.id == id } }
    val detailStats = detailPlayer?.let { p ->
      StatsCalculator.computePlayerAllTimeStats(
        player = p,
        playerId = p.id,
        allSessions = bundle.sessions,
        gamesBySessionId = gamesBySession,
      )
    }

    return PlayersUiState(
      isLoading = false,
      rankingSort = bundle.prefs.playersRankingSort,
      rankedActivePlayers = ranked,
      archivedPlayers = archived,
      selectedPlayer = detailPlayer,
      selectedPlayerStats = detailStats,
      errorMessage = error,
      archivedSuccessNavigateBack = navBack,
    )
  }

  fun addPlayer(name: String, onAdded: () -> Unit = {}) {
    viewModelScope.launch {
      runCatching { repository.addPlayer(name) }
        .onSuccess { onAdded() }
        .onFailure { err -> errorState.value = err.message ?: "Could not add player." }
    }
  }

  fun unarchivePlayer(id: String) {
    viewModelScope.launch {
      runCatching { repository.setPlayerActive(id, true) }
        .onFailure { err -> errorState.value = err.message }
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
        .onFailure { err -> errorState.value = err.message ?: "Could not rename player." }
    }
  }

  fun resetSelectedPlayerStats() {
    val id = selectedPlayerId ?: return
    viewModelScope.launch {
      runCatching { repository.resetPlayerStatistics(id) }
        .onFailure { err -> errorState.value = err.message }
    }
  }

  fun archiveSelectedPlayer() {
    val id = selectedPlayerId ?: return
    viewModelScope.launch {
      runCatching { repository.setPlayerActive(id, false) }
        .onSuccess { archivedNavigateBack.value = true }
        .onFailure { err -> errorState.value = err.message }
    }
  }

  fun consumeError() {
    errorState.update { null }
  }
}

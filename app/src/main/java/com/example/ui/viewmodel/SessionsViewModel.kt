package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.repository.TichuRepository
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.Lineup
import com.example.domain.LineupGenerator
import com.example.domain.Player
import com.example.domain.RankedPlayerEntry
import com.example.domain.RankingMetric
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.StatsCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionGameRowItem(
  val gameId: String,
  val gameNumber: Int,
  val summaryText: String,
  val canViewRounds: Boolean,
  val canDelete: Boolean,
)

data class SessionDetailUiModel(
  val sessionId: String,
  val status: SessionStatus,
  val isOwnedByCurrentUser: Boolean,
  val startedAt: Long,
  val playerNamesLine: String,
  val gamesPlayedCount: Int,
  val durationText: String,
  val rankingSort: RankingMetric,
  val rankings: List<RankedPlayerEntry>,
  val games: List<SessionGameRowItem>,
)

data class SessionsUiState(
  val isLoading: Boolean = true,
  val currentUserId: String = "",
  val activePlayers: List<Player> = emptyList(),
  val selectedPlayerIds: Set<String> = emptySet(),
  val activeSession: Session? = null,
  val isActiveSessionHost: Boolean = true,
  val currentSessionGame: Game? = null,
  val currentGameSummary: String? = null,
  val previewLineup: Lineup? = null,
  val swapFirstPlayerId: String? = null,
  val hasPriorGamesInActiveSession: Boolean = false,
  val activeSessionDetail: SessionDetailUiModel? = null,
  val historySessions: List<SessionDetailUiModel> = emptyList(),
  val expandedHistorySessionIds: Set<String> = emptySet(),
  val playersById: Map<String, Player> = emptyMap(),
  val navigateToScoreboardTrigger: Int = 0,
  val errorMessage: String? = null,
)

private data class LocalSessionsUiState(
  val selectedPlayerIds: Set<String> = emptySet(),
  val previewLineup: Lineup? = null,
  val previewSignature: String? = null,
  val swapFirstPlayerId: String? = null,
  val expandedHistoryIds: Set<String> = emptySet(),
  val navigateToScoreboardTrigger: Int = 0,
  val errorMessage: String? = null,
)

class SessionsViewModel(
  private val repository: TichuRepository,
  private val focusedSessionId: String? = null,
) : ViewModel() {

  private val localState = MutableStateFlow(LocalSessionsUiState())

  private val tickerFlow = flow {
    while (true) {
      emit(System.currentTimeMillis())
      delay(15_000L)
    }
  }

  private val repoBundleFlow = combine(
    repository.playersFlow,
    repository.sessionsFlow,
    repository.gamesFlow,
    repository.activeOrPausedSessionFlow,
    repository.currentSessionGameFlow,
    repository.preferencesFlow,
  ) { args ->
    @Suppress("UNCHECKED_CAST")
    RepoBundle(
      players = args[0] as List<Player>,
      sessions = args[1] as List<Session>,
      games = args[2] as List<Game>,
      activeSession = args[3] as Session?,
      currentGame = args[4] as Game?,
      prefs = args[5] as AppPreferences,
    )
  }

  val uiState: StateFlow<SessionsUiState> = combine(
    repoBundleFlow,
    localState,
    tickerFlow,
  ) { bundle, local, nowMs ->
    buildUiState(bundle, local, nowMs)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = SessionsUiState(currentUserId = repository.currentUserId),
  )

  private data class RepoBundle(
    val players: List<Player>,
    val sessions: List<Session>,
    val games: List<Game>,
    val activeSession: Session?,
    val currentGame: Game?,
    val prefs: AppPreferences,
  )

  private fun buildUiState(
    bundle: RepoBundle,
    local: LocalSessionsUiState,
    nowMs: Long,
  ): SessionsUiState {
    val uid = repository.currentUserId
    val playersById = bundle.players.associateBy { it.id }
    val activePlayers = bundle.players.filter { it.active && (it.ownerUid.isBlank() || it.ownerUid == uid) }
    val activeSession = bundle.activeSession
    val isActiveHost = activeSession == null || activeSession.ownerUid.isBlank() || activeSession.ownerUid == uid

    fun playerName(id: String): String = playersById[id]?.name ?: "(removed player)"

    fun lineupSummary(game: Game): String = when (game.mode) {
      GameMode.THREE_TEAMS -> game.teams.joinToString(" vs ") { team -> team.joinToString(" & ", transform = ::playerName) }
      GameMode.ROTATION -> "Fixed: ${game.fixedTeam.joinToString(" & ", transform = ::playerName)} · Pool: ${game.pool.joinToString(", ", transform = ::playerName)}"
      GameMode.FIXED, GameMode.QUICK -> "${game.teams.getOrNull(0).orEmpty().joinToString(" & ", transform = ::playerName)} vs ${game.teams.getOrNull(1).orEmpty().joinToString(" & ", transform = ::playerName)}"
    }

    fun buildSessionDetail(session: Session): SessionDetailUiModel {
      val sGames = bundle.games.filter { it.sessionId == session.id }
      val stats = StatsCalculator.computeSessionStats(session, sGames, playersById, nowMs)
      val isHost = session.ownerUid.isBlank() || session.ownerUid == uid
      val ranked = StatsCalculator.rankPlayers(
        playersWithStats = session.playerIds.map { pid ->
          val p = playersById[pid] ?: Player(id = pid, name = "(removed player)", createdAt = 0L)
          p to StatsCalculator.computePlayerSessionStats(playersById[pid], pid, sGames)
        },
        metric = bundle.prefs.sessionRankingSort,
      )
      val gameItems = sGames.mapIndexed { idx, g ->
        val label = buildString {
          append("Game ${idx + 1}")
          when {
            g.mode == GameMode.THREE_TEAMS && g.winner in listOf("A", "B", "C") -> {
              val wIdx = listOf("A", "B", "C").indexOf(g.winner)
              append(" · won by ${g.teams.getOrElse(wIdx) { emptyList() }.joinToString(" & ", transform = ::playerName)}")
              g.finalScores?.let { append(" (${it.teamA} - ${it.teamB} - ${it.teamC ?: 0})") }
            }
            g.winner == "A" || g.winner == "B" -> {
              val winnerNames = if (g.mode == GameMode.ROTATION) {
                if (g.winner == "A") g.fixedTeam.joinToString(" & ", transform = ::playerName)
                else g.pool.joinToString(", ", transform = ::playerName)
              } else {
                g.teams.getOrElse(if (g.winner == "A") 0 else 1) { emptyList() }
                  .joinToString(" & ", transform = ::playerName)
              }
              append(" · won by $winnerNames")
              g.finalScores?.let { append(" (${it.teamA} - ${it.teamB})") }
            }
            g.winner == "tie" -> {
              append(" · tied")
              g.finalScores?.let { append(" (${it.teamA} - ${it.teamB})") }
            }
            else -> append(" · in progress")
          }
        }
        SessionGameRowItem(
          gameId = g.id,
          gameNumber = idx + 1,
          summaryText = label,
          canViewRounds = g.winner != null && g.rounds.isNotEmpty(),
          canDelete = g.winner != null && isHost,
        )
      }

      return SessionDetailUiModel(
        sessionId = session.id,
        status = session.status,
        isOwnedByCurrentUser = isHost,
        startedAt = session.startedAt,
        playerNamesLine = session.playerIds.joinToString(" · ", transform = ::playerName),
        gamesPlayedCount = stats.gamesPlayed,
        durationText = StatsCalculator.formatDuration(stats.durationMs),
        rankingSort = bundle.prefs.sessionRankingSort,
        rankings = ranked,
        games = gameItems,
      )
    }

    val activeSessionGames = activeSession?.let { s -> bundle.games.filter { it.sessionId == s.id } }.orEmpty()
    val hasPriorGames = activeSessionGames.any { it.winner != null }
    val currentGameInPlay = bundle.currentGame?.takeIf { it.winner == null }

    // Ensure valid lineup preview for host only (either next-game in active session or new session setup)
    val targetIds: List<String> = when {
      activeSession != null && isActiveHost && currentGameInPlay == null -> activeSession.playerIds
      activeSession == null -> local.selectedPlayerIds.filter { id -> activePlayers.any { it.id == id } }
      else -> emptyList()
    }
    val signature = if (activeSession != null) {
      "S:${activeSession.id}:${activeSessionGames.count { it.winner != null }}:${activeSession.nextPoolStart}"
    } else {
      "N:${targetIds.sorted().joinToString(",")}"
    }

    val validPreview = if (targetIds.size in 4..6) {
      val existing = local.previewLineup
      if (existing != null && local.previewSignature == signature && LineupGenerator.isValidLineupFor(targetIds, existing)) {
        existing
      } else {
        val generated = if (activeSession != null) {
          LineupGenerator.previewLineupForSession(activeSession, activeSessionGames)
        } else {
          LineupGenerator.previewRandomLineup(targetIds)
        }
        localState.update {
          it.copy(previewLineup = generated, previewSignature = signature, swapFirstPlayerId = null)
        }
        generated
      }
    } else {
      null
    }

    val historySessions = bundle.sessions
      .filter { it.status == SessionStatus.ENDED }
      .sortedByDescending { it.endedAt ?: it.startedAt }
      .map(::buildSessionDetail)

    val detailSession = if (focusedSessionId != null) {
      bundle.sessions.firstOrNull { it.id == focusedSessionId }
    } else {
      activeSession
    }

    return SessionsUiState(
      isLoading = false,
      currentUserId = uid,
      activePlayers = activePlayers,
      selectedPlayerIds = local.selectedPlayerIds,
      activeSession = activeSession,
      isActiveSessionHost = isActiveHost,
      currentSessionGame = currentGameInPlay,
      currentGameSummary = currentGameInPlay?.let(::lineupSummary),
      previewLineup = validPreview,
      swapFirstPlayerId = local.swapFirstPlayerId,
      hasPriorGamesInActiveSession = hasPriorGames,
      activeSessionDetail = detailSession?.let(::buildSessionDetail),
      historySessions = historySessions,
      expandedHistorySessionIds = local.expandedHistoryIds,
      playersById = playersById,
      navigateToScoreboardTrigger = local.navigateToScoreboardTrigger,
      errorMessage = local.errorMessage,
    )
  }

  fun togglePlayerSelection(playerId: String) {
    localState.update { state ->
      val next = if (playerId in state.selectedPlayerIds) {
        state.selectedPlayerIds - playerId
      } else {
        state.selectedPlayerIds + playerId
      }
      state.copy(selectedPlayerIds = next, swapFirstPlayerId = null)
    }
  }

  fun reshuffleLineup() {
    val state = uiState.value
    val activeSession = state.activeSession
    viewModelScope.launch {
      if (activeSession != null) {
        val games = firstOrNullSafe(repository.gamesFlow).orEmpty()
          .filter { it.sessionId == activeSession.id }
        val next = LineupGenerator.previewLineupForSession(activeSession, games)
        val sig = "S:${activeSession.id}:${games.count { it.winner != null }}:${activeSession.nextPoolStart}"
        localState.update { it.copy(previewLineup = next, previewSignature = sig, swapFirstPlayerId = null) }
      } else {
        val ids = state.selectedPlayerIds.filter { id -> state.activePlayers.any { it.id == id } }
        if (ids.size in 4..6) {
          val next = LineupGenerator.previewRandomLineup(ids)
          val sig = "N:${ids.sorted().joinToString(",")}"
          localState.update { it.copy(previewLineup = next, previewSignature = sig, swapFirstPlayerId = null) }
        }
      }
    }
  }

  fun tapPlayerForSwap(playerId: String) {
    localState.update { state ->
      val first = state.swapFirstPlayerId
      val lineup = state.previewLineup ?: return@update state
      when {
        first == null -> state.copy(swapFirstPlayerId = playerId)
        first == playerId -> state.copy(swapFirstPlayerId = null)
        else -> state.copy(
          previewLineup = LineupGenerator.swapPlayers(lineup, first, playerId),
          swapFirstPlayerId = null,
        )
      }
    }
  }

  fun startSession() {
    val state = uiState.value
    val ids = state.activePlayers.map { it.id }.filter { it in state.selectedPlayerIds }
    val lineup = state.previewLineup ?: return
    if (ids.size !in 4..6) return
    viewModelScope.launch {
      runCatching {
        repository.startSession(ids, lineup)
      }.onSuccess {
        localState.update {
          it.copy(
            selectedPlayerIds = emptySet(),
            previewLineup = null,
            previewSignature = null,
            swapFirstPlayerId = null,
            navigateToScoreboardTrigger = it.navigateToScoreboardTrigger + 1,
          )
        }
      }.onFailure { err ->
        localState.update { it.copy(errorMessage = err.message ?: "Could not start session.") }
      }
    }
  }

  fun startNextGame() {
    val state = uiState.value
    val session = state.activeSession ?: return
    if (!state.isActiveSessionHost) return
    val lineup = state.previewLineup
    viewModelScope.launch {
      runCatching {
        repository.startNewGameInSession(session.id, lineup)
      }.onSuccess {
        localState.update {
          it.copy(
            previewLineup = null,
            previewSignature = null,
            swapFirstPlayerId = null,
            navigateToScoreboardTrigger = it.navigateToScoreboardTrigger + 1,
          )
        }
      }.onFailure { err ->
        localState.update { it.copy(errorMessage = err.message ?: "Could not start next game.") }
      }
    }
  }

  fun pauseSession() {
    val session = uiState.value.activeSession ?: return
    viewModelScope.launch {
      runCatching { repository.pauseSession(session.id) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun resumeSession() {
    val session = uiState.value.activeSession ?: return
    viewModelScope.launch {
      runCatching { repository.resumeSession(session.id) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun endSession() {
    val session = uiState.value.activeSession ?: return
    viewModelScope.launch {
      runCatching { repository.endSession(session.id) }
        .onSuccess {
          localState.update { it.copy(previewLineup = null, previewSignature = null, swapFirstPlayerId = null) }
        }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun deleteGame(gameId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteGame(gameId) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun deleteSession(sessionId: String) {
    viewModelScope.launch {
      runCatching { repository.deleteSession(sessionId) }
        .onFailure { err -> localState.update { it.copy(errorMessage = err.message) } }
    }
  }

  fun setSessionRankingSort(metric: RankingMetric) {
    viewModelScope.launch {
      repository.setSessionRankingSort(metric)
    }
  }

  fun toggleHistoryExpanded(sessionId: String) {
    localState.update { state ->
      val next = if (sessionId in state.expandedHistoryIds) {
        state.expandedHistoryIds - sessionId
      } else {
        state.expandedHistoryIds + sessionId
      }
      state.copy(expandedHistoryIds = next)
    }
  }

  fun consumeError() {
    localState.update { it.copy(errorMessage = null) }
  }
}

private suspend fun <T> firstOrNullSafe(flow: Flow<T>): T? =
  runCatching { flow.first() }.getOrNull()

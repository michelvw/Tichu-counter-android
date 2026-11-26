package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.repository.TichuRepository
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.LineupGenerator
import com.example.domain.Player
import com.example.domain.RoundRecord
import com.example.domain.ScoreboardDraft
import com.example.domain.ScoringEngine
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.TichuCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ThreeTeamSummaryItem(
  val teamIndex: Int,
  val teamName: String,
  val score: Int,
  val remaining: Int,
  val progressFraction: Float,
  val isResting: Boolean,
  val isGameFinished: Boolean,
)

data class ScoreboardSnapshot(
  val gameId: String,
  val draft: ScoreboardDraft,
  val isReset: Boolean = false,
  val roundsBeforeReset: List<RoundRecord> = emptyList(),
  val dealerBeforeReset: String? = null,
)

data class ScoreboardUiState(
  val isLoading: Boolean = true,
  val isSessionMode: Boolean = false,
  val isSessionPaused: Boolean = false,
  val sessionId: String? = null,
  val gameId: String? = null,
  val gameMode: GameMode = GameMode.QUICK,
  val isThreeTeamMode: Boolean = false,
  val isThreeTeamFinished: Boolean = false,
  val roundNumber: Int = 1,
  val winThreshold: Int = ScoringEngine.DEFAULT_WIN_THRESHOLD,
  val teamAName: String = "Team A",
  val teamBName: String = "Team B",
  val teamAScore: Int = 0,
  val teamBScore: Int = 0,
  val activeIndices: List<Int> = listOf(0, 1),
  val threeTeamCards: List<ThreeTeamSummaryItem> = emptyList(),
  val sessionTagText: String? = null,
  val dealerText: String? = null,
  val teamBSittingOutText: String? = null,
  val showQuickGameHint: Boolean = false,
  val winBannerVisible: Boolean = false,
  val winBannerMessage: String? = null,
  val showWinBannerFinishAction: Boolean = false,
  val showWinBannerCloseAction: Boolean = true,
  val draft: ScoreboardDraft = ScoreboardDraft(),
  val previewDeltaA: Int = 50,
  val previewDeltaB: Int = 50,
  val canUndo: Boolean = false,
  val canReset: Boolean = false,
  val isFreshBoard: Boolean = true,
  val nextRoundEnabled: Boolean = true,
  val toastMessage: String? = null,
)

private data class LocalScoreboardState(
  val draft: ScoreboardDraft = ScoreboardDraft(),
  val undoHistory: List<ScoreboardSnapshot> = emptyList(),
  val winBannerDismissed: Boolean = false,
  val isSubmitting: Boolean = false,
  val toastMessage: String? = null,
)

class ScoreboardViewModel(
  private val repository: TichuRepository,
) : ViewModel() {

  private val localState = MutableStateFlow(LocalScoreboardState())

  init {
    viewModelScope.launch {
      repository.ensureQuickGame()
    }
  }

  private val repoDataFlow = combine(
    repository.activeOrPausedSessionFlow,
    repository.currentSessionGameFlow,
    repository.activeQuickGameFlow,
    repository.playersFlow,
    repository.gamesFlow,
  ) { activeSession, sessionGame, quickGame, players, allGames ->
    RepoScoreboardBundle(activeSession, sessionGame, quickGame, players, allGames)
  }

  val uiState: StateFlow<ScoreboardUiState> = combine(
    repoDataFlow,
    repository.preferencesFlow,
    localState,
  ) { bundle, prefs, local ->
    buildUiState(bundle, prefs, local)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = ScoreboardUiState(),
  )

  private data class RepoScoreboardBundle(
    val activeSession: Session?,
    val sessionGame: Game?,
    val quickGame: Game?,
    val players: List<Player>,
    val allGames: List<Game>,
  )

  private fun buildUiState(
    bundle: RepoScoreboardBundle,
    prefs: AppPreferences,
    local: LocalScoreboardState,
  ): ScoreboardUiState {
    val sessionGame = bundle.sessionGame
    val isSessionMode = sessionGame != null
    val activeGame = sessionGame ?: bundle.quickGame
    val winThreshold = prefs.winThreshold
    val playersById = bundle.players.associateBy { it.id }

    fun playerName(id: String): String = playersById[id]?.name ?: "?"
    fun teamNames(ids: List<String>): String = ids.joinToString(" & ") { playerName(it) }

    val (deltaA, deltaB) = ScoringEngine.computeDraftDeltas(local.draft)

    if (activeGame == null) {
      return ScoreboardUiState(
        isLoading = false,
        winThreshold = winThreshold,
        teamAName = prefs.quickTeamAName,
        teamBName = prefs.quickTeamBName,
        draft = local.draft,
        previewDeltaA = deltaA,
        previewDeltaB = deltaB,
        toastMessage = local.toastMessage,
      )
    }

    val isSessionPaused = isSessionMode && bundle.activeSession?.status == SessionStatus.PAUSED
    val isThreeTeam = activeGame.mode == GameMode.THREE_TEAMS
    val isThreeTeamFinished = isThreeTeam && activeGame.winner != null
    val roundNumber = activeGame.rounds.size + 1

    val undoForGame = local.undoHistory.filter { it.gameId == activeGame.id }

    if (isThreeTeam) {
      val displayRound = if (isThreeTeamFinished) (roundNumber - 1).coerceAtLeast(1) else roundNumber
      val lineup = LineupGenerator.getRoundLineup(activeGame, displayRound)
      val scores = activeGame.rounds.lastOrNull()?.scores ?: listOf(0, 0, 0)
      val teamAName = teamNames(lineup.teamA)
      val teamBName = teamNames(lineup.teamB)
      val scoreA = scores.getOrElse(lineup.activeIndices.getOrElse(0) { 0 }) { 0 }
      val scoreB = scores.getOrElse(lineup.activeIndices.getOrElse(1) { 1 }) { 0 }

      val goal = winThreshold.coerceAtLeast(1)
      val cards = activeGame.teams.mapIndexed { index, team ->
        val score = scores.getOrElse(index) { 0 }
        val rem = (winThreshold - score).coerceAtLeast(0)
        val frac = (score.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
        ThreeTeamSummaryItem(
          teamIndex = index,
          teamName = teamNames(team),
          score = score,
          remaining = rem,
          progressFraction = frac,
          isResting = index == lineup.restingTeamIndex,
          isGameFinished = isThreeTeamFinished,
        )
      }

      val sessionGamesCount = bundle.allGames.count { it.sessionId == activeGame.sessionId }
      val sessionTag = "Game $sessionGamesCount"
      val dealerName = if (!isThreeTeamFinished) {
        activeGame.nextDealerPlayerId?.let { playerName(it) }
      } else {
        null
      }

      val winnerIdx = listOf("A", "B", "C").indexOf(activeGame.winner)
      val winnerBannerText = if (winnerIdx in activeGame.teams.indices) {
        "${teamNames(activeGame.teams[winnerIdx])} wins!"
      } else {
        null
      }

      return ScoreboardUiState(
        isLoading = false,
        isSessionMode = true,
        isSessionPaused = isSessionPaused,
        sessionId = activeGame.sessionId,
        gameId = activeGame.id,
        gameMode = activeGame.mode,
        isThreeTeamMode = true,
        isThreeTeamFinished = isThreeTeamFinished,
        roundNumber = roundNumber,
        winThreshold = winThreshold,
        teamAName = teamAName,
        teamBName = teamBName,
        teamAScore = scoreA,
        teamBScore = scoreB,
        activeIndices = lineup.activeIndices,
        threeTeamCards = cards,
        sessionTagText = sessionTag,
        dealerText = dealerName?.let { "Dealer: $it" },
        showQuickGameHint = false,
        winBannerVisible = isThreeTeamFinished,
        winBannerMessage = winnerBannerText,
        showWinBannerFinishAction = true,
        showWinBannerCloseAction = false,
        draft = local.draft,
        previewDeltaA = deltaA,
        previewDeltaB = deltaB,
        canUndo = undoForGame.isNotEmpty() || activeGame.rounds.isNotEmpty(),
        canReset = activeGame.rounds.isNotEmpty() && !isThreeTeamFinished,
        isFreshBoard = false,
        nextRoundEnabled = !isSessionPaused && !isThreeTeamFinished && !local.isSubmitting,
        toastMessage = local.toastMessage,
      )
    }

    // 2-team mode (Quick, Fixed 4p, Rotation 5p)
    val latestScores = activeGame.rounds.lastOrNull()?.scores ?: listOf(0, 0)
    val scoreA = latestScores.getOrElse(0) { 0 }
    val scoreB = latestScores.getOrElse(1) { 0 }

    val teamAName: String
    val teamBName: String
    val sessionTag: String?
    val dealerText: String?
    val teamBSittingOut: String?

    if (isSessionMode) {
      val lineup = LineupGenerator.getRoundLineup(activeGame, roundNumber)
      teamAName = teamNames(lineup.teamA)
      teamBName = teamNames(lineup.teamB)
      val sessionGamesCount = bundle.allGames.count { it.sessionId == activeGame.sessionId }
      val sitOutName = lineup.sittingOutPlayerId?.let { playerName(it) }
      sessionTag = "Game $sessionGamesCount"
      teamBSittingOut = sitOutName?.takeIf { it.isNotBlank() }?.let { "Sitting out: $it" }
      dealerText = activeGame.nextDealerPlayerId?.let { "Dealer: ${playerName(it)}" }
    } else {
      teamAName = activeGame.teamAName
      teamBName = activeGame.teamBName
      sessionTag = null
      teamBSittingOut = null
      dealerText = null
    }

    val reachedIndices = ScoringEngine.thresholdReachedTwoTeamIndices(listOf(scoreA, scoreB), winThreshold)
    val bannerMsg = if (reachedIndices.isNotEmpty() && !local.winBannerDismissed) {
      val names = reachedIndices.map { idx -> if (idx == 0) teamAName else teamBName }
      ScoringEngine.formatTwoTeamWinMessage(names, winThreshold)
    } else {
      null
    }

    val isFreshBoard = !isSessionMode && roundNumber == 1 && activeGame.rounds.isEmpty() && scoreA == 0 && scoreB == 0
    val showHint = isFreshBoard && !prefs.modeHintDismissed

    return ScoreboardUiState(
      isLoading = false,
      isSessionMode = isSessionMode,
      isSessionPaused = isSessionPaused,
      sessionId = activeGame.sessionId,
      gameId = activeGame.id,
      gameMode = activeGame.mode,
      isThreeTeamMode = false,
      isThreeTeamFinished = false,
      roundNumber = roundNumber,
      winThreshold = winThreshold,
      teamAName = teamAName,
      teamBName = teamBName,
      teamAScore = scoreA,
      teamBScore = scoreB,
      activeIndices = listOf(0, 1),
      threeTeamCards = emptyList(),
      sessionTagText = sessionTag,
      dealerText = dealerText,
      teamBSittingOutText = teamBSittingOut,
      showQuickGameHint = showHint,
      winBannerVisible = bannerMsg != null,
      winBannerMessage = bannerMsg,
      showWinBannerFinishAction = isSessionMode,
      showWinBannerCloseAction = true,
      draft = local.draft,
      previewDeltaA = deltaA,
      previewDeltaB = deltaB,
      canUndo = undoForGame.isNotEmpty() || activeGame.rounds.isNotEmpty(),
      canReset = !isFreshBoard,
      isFreshBoard = isFreshBoard,
      nextRoundEnabled = !isSessionPaused && !local.isSubmitting,
      toastMessage = local.toastMessage,
    )
  }

  fun adjustPoints(isTeamA: Boolean, deltaSteps: Int) {
    localState.update { state ->
      state.copy(draft = ScoringEngine.adjustCardPoints(state.draft, isTeamA, deltaSteps))
    }
  }

  fun selectTichuCall(isTeamA: Boolean, call: TichuCall) {
    localState.update { state ->
      state.copy(draft = ScoringEngine.setTichuCall(state.draft, isTeamA, call))
    }
  }

  fun toggleDoubleWin(isTeamA: Boolean, checked: Boolean) {
    localState.update { state ->
      state.copy(draft = ScoringEngine.toggleDoubleWin(state.draft, isTeamA, checked))
    }
  }

  fun updateQuickTeamName(isTeamA: Boolean, name: String) {
    viewModelScope.launch {
      repository.updateQuickTeamName(isTeamA, name)
    }
  }

  fun submitNextRound() {
    val state = uiState.value
    val gameId = state.gameId ?: return
    if (!state.nextRoundEnabled || localState.value.isSubmitting) return
    val currentDraft = localState.value.draft
    val prevScores = listOf(state.teamAScore, state.teamBScore)

    localState.update { it.copy(isSubmitting = true) }
    viewModelScope.launch {
      try {
        val updatedGame = repository.recordRound(gameId, currentDraft, state.winThreshold)
        val newlyCrossed = if (updatedGame.mode != GameMode.THREE_TEAMS) {
          val nextScores = updatedGame.rounds.lastOrNull()?.scores ?: prevScores
          ScoringEngine.newlyCrossedTwoTeamIndices(prevScores, nextScores, state.winThreshold).isNotEmpty()
        } else {
          updatedGame.winner != null
        }
        localState.update { cur ->
          cur.copy(
            draft = ScoreboardDraft(),
            undoHistory = cur.undoHistory.filter { it.gameId == gameId } +
              ScoreboardSnapshot(gameId = gameId, draft = currentDraft),
            winBannerDismissed = if (newlyCrossed) false else cur.winBannerDismissed,
            isSubmitting = false,
          )
        }
      } catch (t: Throwable) {
        localState.update {
          it.copy(
            isSubmitting = false,
            toastMessage = t.message ?: "Could not save round.",
          )
        }
      }
    }
  }

  fun undo() {
    val state = uiState.value
    val gameId = state.gameId ?: return
    val gameSnapshots = localState.value.undoHistory.filter { it.gameId == gameId }
    val latest = gameSnapshots.lastOrNull()

    viewModelScope.launch {
      if (latest != null && latest.isReset) {
        repository.restoreGameSnapshot(gameId, latest.roundsBeforeReset, latest.dealerBeforeReset)
        localState.update { cur ->
          cur.copy(
            draft = latest.draft,
            undoHistory = cur.undoHistory.dropLast(1),
            winBannerDismissed = false,
            toastMessage = "Reset undone - previous game restored",
          )
        }
      } else if (latest != null || state.roundNumber > 1) {
        repository.undoLastRound(gameId)
        localState.update { cur ->
          cur.copy(
            draft = latest?.draft ?: ScoreboardDraft(),
            undoHistory = if (latest != null) cur.undoHistory.dropLast(1) else cur.undoHistory,
            winBannerDismissed = false,
            toastMessage = "Last round undone",
          )
        }
      }
    }
  }

  fun confirmReset() {
    val state = uiState.value
    val gameId = state.gameId ?: return
    if (state.isThreeTeamFinished) {
      localState.update { it.copy(toastMessage = "Undo the winning round before resetting this game.") }
      return
    }
    viewModelScope.launch {
      val allGames = firstOrNullSafe(repository.gamesFlow).orEmpty()
      val game = allGames.firstOrNull { it.id == gameId }
      val rounds = game?.rounds.orEmpty()
      val snapshot = ScoreboardSnapshot(
        gameId = gameId,
        draft = localState.value.draft,
        isReset = true,
        roundsBeforeReset = rounds,
        dealerBeforeReset = game?.nextDealerPlayerId,
      )
      repository.resetGameRounds(gameId, rounds.firstOrNull()?.dealerPlayerId)
      localState.update { cur ->
        cur.copy(
          draft = ScoreboardDraft(),
          undoHistory = if (state.isFreshBoard) emptyList() else cur.undoHistory.filter { it.gameId == gameId } + snapshot,
          winBannerDismissed = false,
          toastMessage = if (state.isFreshBoard) null else "Game reset - use Undo to restore it",
        )
      }
    }
  }

  fun finishGameAndStartNext() {
    val state = uiState.value
    val gameId = state.gameId ?: return
    val sessionId = state.sessionId ?: return
    if (!state.isSessionMode) return

    viewModelScope.launch {
      if (!state.isThreeTeamMode) {
        val winner = when {
          state.teamAScore == state.teamBScore -> "tie"
          state.teamAScore > state.teamBScore -> "A"
          else -> "B"
        }
        repository.finishSessionGame(
          gameId = gameId,
          winner = winner,
          finalScores = com.example.domain.FinalScores(state.teamAScore, state.teamBScore),
        )
      } else if (!state.isThreeTeamFinished) {
        return@launch
      }

      val session = firstOrNullSafe(repository.activeOrPausedSessionFlow)
      if (session != null && session.id == sessionId && session.status == SessionStatus.ACTIVE) {
        repository.startNewGameInSession(sessionId, null)
        localState.update {
          LocalScoreboardState(toastMessage = "Game finished — next game started")
        }
      } else {
        localState.update { it.copy(toastMessage = "Session is paused or ended.") }
      }
    }
  }

  fun resumePausedSession() {
    val sessionId = uiState.value.sessionId ?: return
    viewModelScope.launch {
      repository.resumeSession(sessionId)
    }
  }

  fun dismissWinBanner() {
    localState.update { it.copy(winBannerDismissed = true) }
  }

  fun dismissModeHint() {
    viewModelScope.launch {
      repository.dismissModeHint()
    }
  }

  fun consumeToast() {
    localState.update { it.copy(toastMessage = null) }
  }
}

private suspend fun <T> firstOrNullSafe(flow: Flow<T>): T? =
  runCatching { flow.first() }.getOrNull()

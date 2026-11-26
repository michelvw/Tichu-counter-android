package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.preferences.AppPreferences
import com.example.data.repository.TichuRepository
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.Player
import com.example.domain.TichuCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class TichuBadgeDisplay(
  val code: String,
  val doubleWin: Boolean,
  val isLost: Boolean,
) {
  val chartText: String
    get() = (if (doubleWin) "⇉ " else "") + (if (isLost) "✗$code" else code)
  val isEmpty: Boolean
    get() = code.isEmpty() && !doubleWin
}

data class RoundDisplayRow(
  val roundNumber: Int,
  val totalA: Int,
  val totalB: Int,
  val totalC: Int?,
  val deltaA: Int,
  val deltaB: Int,
  val deltaC: Int?,
  val activeTeams: List<Int>,
  val badgeA: TichuBadgeDisplay,
  val badgeB: TichuBadgeDisplay,
  val badgeC: TichuBadgeDisplay?,
  val dealerName: String?,
)

data class RoundScoresUiState(
  val isLoading: Boolean = true,
  val isThreeTeams: Boolean = false,
  val teamAName: String = "Team A",
  val teamBName: String = "Team B",
  val teamCName: String? = null,
  val roundCounterText: String = "Round 1",
  val thresholdOrFinalText: String = "Win: 500",
  val rows: List<RoundDisplayRow> = emptyList(),
  val anyDealers: Boolean = false,
  val chipsVisible: Boolean = true,
  val isGraphView: Boolean = true,
  val yMin: Int = 0,
  val yMax: Int = 100,
)

class RoundScoresViewModel(
  private val repository: TichuRepository,
  private val targetGameId: String?,
) : ViewModel() {

  private val isGraphView = MutableStateFlow(true)

  private val targetGameFlow = combine(
    repository.gamesFlow,
    repository.currentSessionGameFlow,
    repository.activeQuickGameFlow,
  ) { allGames, currentSessionGame, quickGame ->
    if (targetGameId != null) {
      allGames.firstOrNull { it.id == targetGameId }
    } else {
      currentSessionGame ?: quickGame
    }
  }

  val uiState: StateFlow<RoundScoresUiState> = combine(
    targetGameFlow,
    repository.playersFlow,
    repository.preferencesFlow,
    isGraphView,
  ) { game, players, prefs, graphActive ->
    buildUiState(game, players, prefs, graphActive)
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = RoundScoresUiState(),
  )

  private fun buildUiState(
    game: Game?,
    players: List<Player>,
    prefs: AppPreferences,
    graphActive: Boolean,
  ): RoundScoresUiState {
    if (game == null) {
      return RoundScoresUiState(
        isLoading = false,
        teamAName = prefs.quickTeamAName,
        teamBName = prefs.quickTeamBName,
        roundCounterText = "Round 1",
        thresholdOrFinalText = "Win: ${prefs.winThreshold}",
        chipsVisible = prefs.chipsVisible,
        isGraphView = graphActive,
      )
    }

    val playersById = players.associateBy { it.id }
    fun teamNames(ids: List<String>): String =
      ids.joinToString(" & ") { playersById[it]?.name ?: "?" }

    val isThreeTeams = game.mode == GameMode.THREE_TEAMS
    val teamAName: String
    val teamBName: String
    val teamCName: String?

    when (game.mode) {
      GameMode.THREE_TEAMS -> {
        teamAName = teamNames(game.teams.getOrElse(0) { emptyList() })
        teamBName = teamNames(game.teams.getOrElse(1) { emptyList() })
        teamCName = teamNames(game.teams.getOrElse(2) { emptyList() })
      }
      GameMode.ROTATION -> {
        teamAName = teamNames(game.fixedTeam)
        teamBName = teamNames(game.pool)
        teamCName = null
      }
      GameMode.FIXED -> {
        teamAName = teamNames(game.teams.getOrElse(0) { emptyList() })
        teamBName = teamNames(game.teams.getOrElse(1) { emptyList() })
        teamCName = null
      }
      GameMode.QUICK -> {
        teamAName = game.teamAName
        teamBName = game.teamBName
        teamCName = null
      }
    }

    fun makeBadge(mod: Int, dw: Boolean): TichuBadgeDisplay {
      val call = TichuCall.fromMod(mod)
      return TichuBadgeDisplay(
        code = call.shortBadge(),
        doubleWin = dw,
        isLost = call.isLost,
      )
    }

    val rounds = game.rounds.sortedBy { it.roundNumber }
    val rows = rounds.mapIndexed { idx, r ->
      val prev = if (idx == 0) null else rounds[idx - 1]
      val totalA = r.scores.getOrElse(0) { 0 }
      val totalB = r.scores.getOrElse(1) { 0 }
      val totalC = if (isThreeTeams) r.scores.getOrElse(2) { 0 } else null

      val deltaA = totalA - (prev?.scores?.getOrElse(0) { 0 } ?: 0)
      val deltaB = totalB - (prev?.scores?.getOrElse(1) { 0 } ?: 0)
      val deltaC = if (isThreeTeams) {
        (totalC ?: 0) - (prev?.scores?.getOrElse(2) { 0 } ?: 0)
      } else {
        null
      }

      val dealerName = r.dealerPlayerId?.let { id ->
        playersById[id]?.name ?: "(removed player)"
      }

      RoundDisplayRow(
        roundNumber = r.roundNumber,
        totalA = totalA,
        totalB = totalB,
        totalC = totalC,
        deltaA = deltaA,
        deltaB = deltaB,
        deltaC = deltaC,
        activeTeams = r.activeTeams,
        badgeA = makeBadge(r.tichuMods.getOrElse(0) { 0 }, r.doubleWins.getOrElse(0) { false }),
        badgeB = makeBadge(r.tichuMods.getOrElse(1) { 0 }, r.doubleWins.getOrElse(1) { false }),
        badgeC = if (isThreeTeams) {
          makeBadge(r.tichuMods.getOrElse(2) { 0 }, r.doubleWins.getOrElse(2) { false })
        } else {
          null
        },
        dealerName = dealerName,
      )
    }

    val anyDealers = rows.any { !it.dealerName.isNullOrBlank() }

    val allScores = buildList {
      rows.forEach {
        add(it.totalA)
        add(it.totalB)
        if (it.totalC != null) add(it.totalC)
      }
    }
    var yMin = 0
    var yMax = 100
    if (allScores.isNotEmpty()) {
      val rawMin = allScores.min()
      val rawMax = allScores.max()
      val range = rawMax - rawMin
      val padding = max(30, (range * 0.15).roundToInt())
      yMin = (floor((rawMin - padding) / 10.0) * 10).toInt()
      yMax = (ceil((rawMax + padding) / 10.0) * 10).toInt()
      if (yMax <= yMin) yMax = yMin + 100
    }

    val isHistoryView = targetGameId != null
    val roundCounterText = when {
      isHistoryView || game.winner != null ->
        "Final — ${rows.size} round${if (rows.size == 1) "" else "s"}"
      else -> "Round ${rows.size + 1}"
    }

    val thresholdOrFinalText = when {
      game.finalScores != null -> {
        val fs = game.finalScores
        if (fs.teamC != null) "Final: ${fs.teamA} - ${fs.teamB} - ${fs.teamC}"
        else "Final: ${fs.teamA} - ${fs.teamB}"
      }
      isHistoryView -> "Finished"
      else -> "Win: ${prefs.winThreshold}"
    }

    return RoundScoresUiState(
      isLoading = false,
      isThreeTeams = isThreeTeams,
      teamAName = teamAName,
      teamBName = teamBName,
      teamCName = teamCName,
      roundCounterText = roundCounterText,
      thresholdOrFinalText = thresholdOrFinalText,
      rows = rows,
      anyDealers = anyDealers,
      chipsVisible = prefs.chipsVisible,
      isGraphView = graphActive,
      yMin = yMin,
      yMax = yMax,
    )
  }

  fun toggleView() {
    isGraphView.update { !it }
  }

  fun toggleChips() {
    val current = uiState.value.chipsVisible
    viewModelScope.launch {
      repository.setChipsVisible(!current)
    }
  }
}

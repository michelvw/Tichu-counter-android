package com.example.domain

enum class ThemeMode(val key: String, val label: String) {
  SYSTEM("system", "System"),
  LIGHT("light", "Light"),
  DARK("dark", "Dark");

  companion object {
    fun fromKey(key: String?): ThemeMode =
      entries.firstOrNull { it.key == key } ?: SYSTEM
  }
}

enum class GameMode(val key: String) {
  QUICK("quick"),
  FIXED("fixed"),
  ROTATION("rotation"),
  THREE_TEAMS("threeTeams");

  companion object {
    fun fromKey(key: String?): GameMode =
      entries.firstOrNull { it.key == key } ?: QUICK
  }
}

enum class SessionStatus(val key: String) {
  ACTIVE("active"),
  PAUSED("paused"),
  ENDED("ended");

  companion object {
    fun fromKey(key: String?): SessionStatus =
      entries.firstOrNull { it.key == key } ?: ENDED
  }
}

enum class TichuCall(
  val mod: Int,
  val chartCode: String,
  val isLost: Boolean,
  val label: String,
) {
  NONE(0, "", false, "No Tichu"),
  TICHU_WON(100, "T", false, "Tichu won"),
  GRAND_TICHU_WON(200, "GT", false, "Grand Tichu won"),
  TICHU_LOST(-100, "✗T", true, "Tichu lost"),
  GRAND_TICHU_LOST(-200, "✗GT", true, "Grand Tichu lost");

  fun shortBadge(): String = when (this) {
    NONE -> ""
    TICHU_WON -> "T"
    GRAND_TICHU_WON -> "GT"
    TICHU_LOST -> "T"
    GRAND_TICHU_LOST -> "GT"
  }

  fun chartLabel(doubleWin: Boolean): String =
    (if (doubleWin) "⇉ " else "") + chartCode

  companion object {
    fun fromMod(mod: Int): TichuCall =
      entries.firstOrNull { it.mod == mod } ?: NONE
  }
}

data class Player(
  val id: String,
  val name: String,
  val active: Boolean = true,
  val createdAt: Long,
  val statsResetAt: Long? = null,
)

data class PauseInterval(
  val pausedAt: Long,
  val resumedAt: Long? = null,
)

data class Session(
  val id: String,
  val status: SessionStatus,
  val playerIds: List<String>,
  val nextPoolStart: Int = 0,
  val pauseLog: List<PauseInterval> = emptyList(),
  val startedAt: Long,
  val endedAt: Long? = null,
)

sealed class Lineup {
  abstract val mode: GameMode

  data class Fixed(val teams: List<List<String>>) : Lineup() {
    override val mode: GameMode = GameMode.FIXED
  }

  data class Rotation(val fixedTeam: List<String>, val pool: List<String>) : Lineup() {
    override val mode: GameMode = GameMode.ROTATION
  }

  data class ThreeTeams(val teams: List<List<String>>) : Lineup() {
    override val mode: GameMode = GameMode.THREE_TEAMS
  }
}

data class RoundLineup(
  val teamA: List<String>,
  val teamB: List<String>,
  val activeIndices: List<Int> = listOf(0, 1),
  val restingTeamIndex: Int? = null,
  val sittingOutPlayerId: String? = null,
  val sittingOutTeamIds: List<String>? = null,
)

data class RoundRecord(
  val id: Long = 0,
  val gameId: String,
  val roundNumber: Int,
  val scores: List<Int>,
  val activeTeams: List<Int> = listOf(0, 1),
  val tichuMods: List<Int>,
  val doubleWins: List<Boolean>,
  val sittingOutPlayerId: String? = null,
  val dealerPlayerId: String? = null,
)

data class FinalScores(
  val teamA: Int,
  val teamB: Int,
  val teamC: Int? = null,
)

data class Game(
  val id: String,
  val sessionId: String? = null,
  val mode: GameMode,
  val teams: List<List<String>> = emptyList(),
  val fixedTeam: List<String> = emptyList(),
  val pool: List<String> = emptyList(),
  val firstRestingTeam: Int? = null,
  val winThreshold: Int = 500,
  val teamAName: String = "Team A",
  val teamBName: String = "Team B",
  val winner: String? = null,
  val pendingNextGame: Boolean = false,
  val finalScores: FinalScores? = null,
  val nextDealerPlayerId: String? = null,
  val startedAt: Long,
  val endedAt: Long? = null,
  val rounds: List<RoundRecord> = emptyList(),
)

data class TeamRoundInput(
  val cardPoints: Int = 50,
  val tichuCall: TichuCall = TichuCall.NONE,
  val doubleWin: Boolean = false,
)

data class ScoreboardDraft(
  val teamA: TeamRoundInput = TeamRoundInput(cardPoints = 50),
  val teamB: TeamRoundInput = TeamRoundInput(cardPoints = 50),
)

enum class RankingMetric(val id: String, val label: String) {
  TOTAL_POINTS("totalPoints", "Total points"),
  BEST_GAME_SCORE("bestGameScore", "Best game points"),
  WIN_RATE("winRate", "Win rate"),
  AVERAGE_POINTS("averagePoints", "Point average"),
  DOUBLE_WINS("doubleWins", "Double wins"),
  GRAND_TICHU_NET("grandTichuNet", "Grand Tichu"),
  TICHU_NET("tichuNet", "Tichu");

  companion object {
    fun fromId(id: String?): RankingMetric =
      entries.firstOrNull { it.id == id } ?: TOTAL_POINTS
  }
}

data class PlayerStats(
  val sessionsAttended: Int = 0,
  val gamesPlayed: Int = 0,
  val gamesWon: Int = 0,
  val gamesLost: Int = 0,
  val winPct: Double = 0.0,
  val totalPoints: Int = 0,
  val avgPointsPerGame: Int = 0,
  val bestGameScore: Int? = null,
  val tichuWon: Int = 0,
  val tichuLost: Int = 0,
  val grandTichuWon: Int = 0,
  val grandTichuLost: Int = 0,
  val doubleWins: Int = 0,
)

data class SessionLeaderboardEntry(
  val playerId: String,
  val playerName: String,
  val gamesPlayed: Int,
  val gamesWon: Int,
  val winPct: Double,
  val totalPoints: Int,
)

data class SessionStats(
  val gamesPlayed: Int,
  val durationMs: Long,
  val leaderboard: List<SessionLeaderboardEntry>,
)

data class RankedPlayerEntry(
  val playerId: String,
  val playerName: String,
  val stats: PlayerStats,
  val sortValue: Double?,
  val formattedValue: String,
)

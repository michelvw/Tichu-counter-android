package com.example.domain

import kotlin.math.max
import kotlin.math.roundToInt

object StatsCalculator {

  fun computePlayerSessionStats(
    player: Player?,
    playerId: String,
    sessionGames: List<Game>,
  ): PlayerStats {
    val resetAt = player?.statsResetAt ?: 0L
    val eligibleGames = sessionGames.filter { g ->
      g.winner != null && (g.startedAt == 0L || g.startedAt > resetAt)
    }

    var gamesPlayed = 0
    var gamesWon = 0
    var gamesLost = 0
    var totalPoints = 0
    var bestGameScore: Int? = null
    var tichuWon = 0
    var tichuLost = 0
    var grandTichuWon = 0
    var grandTichuLost = 0
    var doubleWins = 0

    for (g in eligibleGames) {
      val side = DealerSelector.playerSide(g, playerId) ?: continue
      gamesPlayed++
      if (g.winner == side) gamesWon++
      else if (g.winner != "tie") gamesLost++

      val pts = when (side) {
        "A" -> g.finalScores?.teamA
        "B" -> g.finalScores?.teamB
        "C" -> g.finalScores?.teamC
        else -> null
      }
      if (pts != null) {
        totalPoints += pts
        if (bestGameScore == null || pts > bestGameScore) bestGameScore = pts
      }

      for (r in g.rounds) {
        if (g.mode == GameMode.THREE_TEAMS) {
          val teamIndex = when (side) {
            "A" -> 0
            "B" -> 1
            "C" -> 2
            else -> -1
          }
          if (teamIndex !in r.activeTeams) continue
          when (r.tichuMods.getOrElse(teamIndex) { 0 }) {
            200 -> grandTichuWon++
            100 -> tichuWon++
            -200 -> grandTichuLost++
            -100 -> tichuLost++
          }
          if (r.doubleWins.getOrElse(teamIndex) { false }) doubleWins++
        } else {
          val roundIdx = if (g.mode == GameMode.ROTATION && playerId in g.pool) {
            if (r.sittingOutPlayerId == playerId) continue
            1
          } else if (side == "A") {
            0
          } else {
            1
          }
          when (r.tichuMods.getOrElse(roundIdx) { 0 }) {
            200 -> grandTichuWon++
            100 -> tichuWon++
            -200 -> grandTichuLost++
            -100 -> tichuLost++
          }
          if (r.doubleWins.getOrElse(roundIdx) { false }) doubleWins++
        }
      }
    }

    val winPct = if (gamesPlayed > 0) {
      ((gamesWon.toDouble() / gamesPlayed) * 1000.0).roundToInt() / 10.0
    } else {
      0.0
    }
    val avgPoints = if (gamesPlayed > 0) {
      (totalPoints.toDouble() / gamesPlayed).roundToInt()
    } else {
      0
    }

    return PlayerStats(
      sessionsAttended = if (gamesPlayed > 0) 1 else 0,
      gamesPlayed = gamesPlayed,
      gamesWon = gamesWon,
      gamesLost = gamesLost,
      winPct = winPct,
      totalPoints = totalPoints,
      avgPointsPerGame = avgPoints,
      bestGameScore = bestGameScore,
      tichuWon = tichuWon,
      tichuLost = tichuLost,
      grandTichuWon = grandTichuWon,
      grandTichuLost = grandTichuLost,
      doubleWins = doubleWins,
    )
  }

  fun computeSessionDurationMs(session: Session, nowMs: Long = System.currentTimeMillis()): Long {
    var pausedMs = 0L
    for (entry in session.pauseLog) {
      val end = entry.resumedAt ?: session.endedAt ?: nowMs
      pausedMs += max(0L, end - entry.pausedAt)
    }
    val endPoint = session.endedAt ?: nowMs
    return max(0L, endPoint - session.startedAt - pausedMs)
  }

  fun computeSessionStats(
    session: Session,
    sessionGames: List<Game>,
    playersById: Map<String, Player>,
    nowMs: Long = System.currentTimeMillis(),
  ): SessionStats {
    val finishedGames = sessionGames.filter { it.winner != null }
    val leaderboard = session.playerIds.map { pid ->
      val p = playersById[pid]
      val s = computePlayerSessionStats(p, pid, sessionGames)
      SessionLeaderboardEntry(
        playerId = pid,
        playerName = p?.name ?: "(removed player)",
        gamesPlayed = s.gamesPlayed,
        gamesWon = s.gamesWon,
        winPct = s.winPct,
        totalPoints = s.totalPoints,
      )
    }.sortedByDescending { it.totalPoints }

    return SessionStats(
      gamesPlayed = finishedGames.size,
      durationMs = computeSessionDurationMs(session, nowMs),
      leaderboard = leaderboard,
    )
  }

  fun computePlayerAllTimeStats(
    player: Player?,
    playerId: String,
    allSessions: List<Session>,
    gamesBySessionId: Map<String, List<Game>>,
  ): PlayerStats {
    val resetAt = player?.statsResetAt ?: 0L
    val attendedSessions = allSessions.filter { s ->
      if (playerId !in s.playerIds) return@filter false
      val games = gamesBySessionId[s.id].orEmpty()
      games.any { g -> g.winner != null && (g.startedAt == 0L || g.startedAt > resetAt) }
    }

    var gamesPlayed = 0
    var gamesWon = 0
    var gamesLost = 0
    var totalPoints = 0
    var bestGameScore: Int? = null
    var tichuWon = 0
    var tichuLost = 0
    var grandTichuWon = 0
    var grandTichuLost = 0
    var doubleWins = 0

    for (s in attendedSessions) {
      val st = computePlayerSessionStats(player, playerId, gamesBySessionId[s.id].orEmpty())
      gamesPlayed += st.gamesPlayed
      gamesWon += st.gamesWon
      gamesLost += st.gamesLost
      totalPoints += st.totalPoints
      tichuWon += st.tichuWon
      tichuLost += st.tichuLost
      grandTichuWon += st.grandTichuWon
      grandTichuLost += st.grandTichuLost
      doubleWins += st.doubleWins
      val best = st.bestGameScore
      if (best != null && (bestGameScore == null || best > bestGameScore)) {
        bestGameScore = best
      }
    }

    val winPct = if (gamesPlayed > 0) {
      ((gamesWon.toDouble() / gamesPlayed) * 1000.0).roundToInt() / 10.0
    } else {
      0.0
    }
    val avgPoints = if (gamesPlayed > 0) {
      (totalPoints.toDouble() / gamesPlayed).roundToInt()
    } else {
      0
    }

    return PlayerStats(
      sessionsAttended = attendedSessions.size,
      gamesPlayed = gamesPlayed,
      gamesWon = gamesWon,
      gamesLost = gamesLost,
      winPct = winPct,
      totalPoints = totalPoints,
      avgPointsPerGame = avgPoints,
      bestGameScore = bestGameScore,
      tichuWon = tichuWon,
      tichuLost = tichuLost,
      grandTichuWon = grandTichuWon,
      grandTichuLost = grandTichuLost,
      doubleWins = doubleWins,
    )
  }

  fun metricValue(metric: RankingMetric, stats: PlayerStats): Double? {
    if (stats.gamesPlayed == 0) return null
    return when (metric) {
      RankingMetric.TOTAL_POINTS -> stats.totalPoints.toDouble()
      RankingMetric.BEST_GAME_SCORE -> stats.bestGameScore?.toDouble()
      RankingMetric.WIN_RATE -> stats.winPct
      RankingMetric.AVERAGE_POINTS -> stats.avgPointsPerGame.toDouble()
      RankingMetric.DOUBLE_WINS -> stats.doubleWins.toDouble()
      RankingMetric.GRAND_TICHU_NET -> (stats.grandTichuWon - stats.grandTichuLost).toDouble()
      RankingMetric.TICHU_NET -> (stats.tichuWon - stats.tichuLost).toDouble()
    }
  }

  fun formatMetricValue(metric: RankingMetric, stats: PlayerStats): String {
    val value = metricValue(metric, stats) ?: return "—"
    return when (metric) {
      RankingMetric.TOTAL_POINTS -> "${value.toInt()} pts"
      RankingMetric.BEST_GAME_SCORE -> "${value.toInt()} pts"
      RankingMetric.WIN_RATE -> {
        val pctStr = if (stats.winPct % 1.0 == 0.0) "${stats.winPct.toInt()}" else "${stats.winPct}"
        "$pctStr% (${stats.gamesWon}/${stats.gamesPlayed})"
      }
      RankingMetric.AVERAGE_POINTS -> "${value.toInt()} pts"
      RankingMetric.DOUBLE_WINS -> "${value.toInt()}"
      RankingMetric.GRAND_TICHU_NET ->
        "${value.toInt()} (${stats.grandTichuWon}W minus ${stats.grandTichuLost}L)"
      RankingMetric.TICHU_NET ->
        "${value.toInt()} (${stats.tichuWon}W minus ${stats.tichuLost}L)"
    }
  }

  fun rankPlayers(
    playersWithStats: List<Pair<Player, PlayerStats>>,
    metric: RankingMetric,
  ): List<RankedPlayerEntry> = playersWithStats.map { (player, stats) ->
    RankedPlayerEntry(
      playerId = player.id,
      playerName = player.name,
      stats = stats,
      sortValue = metricValue(metric, stats),
      formattedValue = formatMetricValue(metric, stats),
    )
  }.sortedWith { a, b ->
    when {
      a.sortValue == null && b.sortValue == null -> a.playerName.compareTo(b.playerName, ignoreCase = true)
      a.sortValue == null -> 1
      b.sortValue == null -> -1
      b.sortValue != a.sortValue -> b.sortValue.compareTo(a.sortValue)
      else -> a.playerName.compareTo(b.playerName, ignoreCase = true)
    }
  }

  fun formatDuration(ms: Long): String {
    if (ms < 60_000L) return "${max(0L, (ms / 1000.0).roundToInt().toLong())}s"
    val totalMin = (ms / 60_000.0).roundToInt()
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
  }
}

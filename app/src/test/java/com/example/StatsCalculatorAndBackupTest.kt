package com.example

import com.example.data.backup.BackupSerializer
import com.example.data.preferences.AppPreferences
import com.example.domain.FinalScores
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.PauseInterval
import com.example.domain.Player
import com.example.domain.RankingMetric
import com.example.domain.RoundRecord
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.StatsCalculator
import com.example.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsCalculatorAndBackupTest {

  @Test
  fun `computePlayerSessionStats excludes sitting-out rounds and respects statsResetAt`() {
    val player3 = Player(id = "p3", name = "Cara", createdAt = 10L, statsResetAt = 500L)
    val oldGame = Game(
      id = "old",
      sessionId = "s1",
      mode = GameMode.FIXED,
      teams = listOf(listOf("p1", "p2"), listOf("p3", "p4")),
      winner = "B",
      finalScores = FinalScores(200, 520),
      startedAt = 400L, // before resetAt=500 -> ignored for p3
    )
    val rotGame = Game(
      id = "g2",
      sessionId = "s1",
      mode = GameMode.ROTATION,
      fixedTeam = listOf("p1", "p2"),
      pool = listOf("p3", "p4", "p5"),
      winner = "B",
      finalScores = FinalScores(410, 535),
      startedAt = 1000L,
      rounds = listOf(
        RoundRecord(
          gameId = "g2",
          roundNumber = 1,
          scores = listOf(100, 200),
          tichuMods = listOf(0, 100),
          doubleWins = listOf(false, true),
          sittingOutPlayerId = "p3", // p3 sat out -> must NOT get Tichu or Double Win credit
        ),
        RoundRecord(
          gameId = "g2",
          roundNumber = 2,
          scores = listOf(410, 535),
          tichuMods = listOf(0, 200),
          doubleWins = listOf(false, true),
          sittingOutPlayerId = "p4", // p3 played -> gets GT won & double win credit
        ),
      ),
    )

    val stats = StatsCalculator.computePlayerSessionStats(player3, "p3", listOf(oldGame, rotGame))
    assertEquals(1, stats.gamesPlayed)
    assertEquals(1, stats.gamesWon)
    assertEquals(535, stats.totalPoints)
    assertEquals(0, stats.tichuWon)
    assertEquals(1, stats.grandTichuWon)
    assertEquals(1, stats.doubleWins)
  }

  @Test
  fun `computeSessionDurationMs subtracts closed and open pause intervals`() {
    val session = Session(
      id = "s1",
      status = SessionStatus.PAUSED,
      playerIds = listOf("p1", "p2", "p3", "p4"),
      pauseLog = listOf(
        PauseInterval(pausedAt = 1_000L, resumedAt = 4_000L), // 3s
        PauseInterval(pausedAt = 6_000L, resumedAt = null),   // open pause until nowMs=10_000 -> 4s
      ),
      startedAt = 0L,
      endedAt = null,
    )
    assertEquals(3_000L, StatsCalculator.computeSessionDurationMs(session, nowMs = 10_000L))
    assertEquals("3s", StatsCalculator.formatDuration(3_000L))
    assertEquals("1h 5m", StatsCalculator.formatDuration(65L * 60_000L))
  }

  @Test
  fun `rankPlayers sorts descending with nulls last across all metrics`() {
    val pA = Player(id = "a", name = "Alice", createdAt = 1L)
    val pB = Player(id = "b", name = "Bob", createdAt = 1L)
    val pC = Player(id = "c", name = "Cara", createdAt = 1L)

    val sA = com.example.domain.PlayerStats(gamesPlayed = 2, gamesWon = 1, winPct = 50.0, tichuWon = 1, tichuLost = 2)
    val sB = com.example.domain.PlayerStats(gamesPlayed = 2, gamesWon = 2, winPct = 100.0, tichuWon = 2, tichuLost = 0)
    val sC = com.example.domain.PlayerStats(gamesPlayed = 0)

    val ranked = StatsCalculator.rankPlayers(
      listOf(pA to sA, pB to sB, pC to sC),
      RankingMetric.TICHU_NET,
    )
    assertEquals(listOf("b", "a", "c"), ranked.map { it.playerId })
    assertEquals("—", ranked[2].formattedValue)
  }

  @Test
  fun `backupSerializer round-trips preferences players sessions and games`() {
    val prefs = AppPreferences(
      themeMode = ThemeMode.DARK,
      winThreshold = 1000,
      chipsVisible = false,
      modeHintDismissed = true,
      playersRankingSort = RankingMetric.WIN_RATE,
      sessionRankingSort = RankingMetric.DOUBLE_WINS,
      currentSessionGameId = "g1",
      quickGameId = "q1",
      quickTeamAName = "North",
      quickTeamBName = "South",
    )
    val players = listOf(Player(id = "p1", name = "Alice", active = true, createdAt = 100L))
    val sessions = listOf(
      Session(
        id = "s1",
        status = SessionStatus.ACTIVE,
        playerIds = listOf("p1", "p2", "p3", "p4"),
        nextPoolStart = 2,
        pauseLog = listOf(PauseInterval(pausedAt = 200L, resumedAt = 250L)),
        startedAt = 150L,
      ),
    )
    val games = listOf(
      Game(
        id = "g1",
        sessionId = "s1",
        mode = GameMode.FIXED,
        teams = listOf(listOf("p1", "p2"), listOf("p3", "p4")),
        winThreshold = 1000,
        startedAt = 160L,
        rounds = listOf(
          RoundRecord(
            gameId = "g1",
            roundNumber = 1,
            scores = listOf(155, -55),
            tichuMods = listOf(100, 0),
            doubleWins = listOf(false, false),
            dealerPlayerId = "p1",
          ),
        ),
      ),
    )

    val json = BackupSerializer.encode(prefs, players, sessions, games, exportedAt = 999L)
    val decoded = BackupSerializer.decode(json)

    assertEquals(prefs, decoded.preferences)
    assertEquals(players, decoded.players)
    assertEquals(sessions, decoded.sessions)
    assertEquals(games, decoded.games)
  }
}

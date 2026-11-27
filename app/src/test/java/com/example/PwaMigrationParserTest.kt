package com.example

import com.example.data.backup.BackupSerializer
import com.example.data.backup.PwaMigrationImporter
import com.example.data.preferences.AppPreferences
import com.example.domain.FinalScores
import com.example.domain.GameMode
import com.example.domain.RankingMetric
import com.example.domain.SessionStatus
import com.example.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PwaMigrationParserTest {

  @Test
  fun `decodes raw stringified localStorage dump with fixed rotation and threeTeams games`() {
    val rawLocalStorageDump = """
      {
        "tichuTheme": "dark",
        "winThreshold": "1000",
        "chipsVisible": "false",
        "tichuModeHintDismissed": "true",
        "tichuPlayersRankingSort": "winRate",
        "tichuSessionRankingSort": "doubleWins",
        "teamAName": "Alice & Bob",
        "teamBName": "Cara & Dan",
        "roundScores": "[{\"teamA\":125,\"teamB\":-25,\"teamATichu\":\"T\",\"teamBTichu\":\"\"}]",
        "tichuPlayers": "[{\"id\":\"p1\",\"name\":\"Alice\",\"active\":true,\"createdAt\":100,\"statsResetAt\":null},{\"id\":\"p2\",\"name\":\"Bob\",\"active\":true,\"createdAt\":101,\"statsResetAt\":null},{\"id\":\"p3\",\"name\":\"Cara\",\"active\":true,\"createdAt\":102,\"statsResetAt\":null},{\"id\":\"p4\",\"name\":\"Dan\",\"active\":true,\"createdAt\":103,\"statsResetAt\":null},{\"id\":\"p5\",\"name\":\"Eve\",\"active\":false,\"createdAt\":104,\"statsResetAt\":500},{\"id\":\"p6\",\"name\":\"Frank\",\"active\":true,\"createdAt\":105,\"statsResetAt\":null}]",
        "tichuSessions": "[{\"id\":\"s1\",\"status\":\"paused\",\"startedAt\":1000,\"endedAt\":null,\"playerIds\":[\"p1\",\"p2\",\"p3\",\"p4\",\"p5\",\"p6\"],\"nextPoolStart\":1,\"pauseLog\":[{\"pausedAt\":2000,\"resumedAt\":2500},{\"pausedAt\":3000,\"resumedAt\":null}]}]",
        "tichuGames": "[{\"id\":\"g_rot\",\"sessionId\":\"s1\",\"mode\":\"rotation\",\"winner\":\"A\",\"startedAt\":1100,\"endedAt\":1900,\"fixedTeam\":[\"p1\",\"p2\"],\"pool\":[\"p3\",\"p4\",\"p5\"],\"finalScores\":{\"teamA\":1020,\"teamB\":440},\"nextDealerPlayerId\":\"p4\",\"rounds\":[{\"teamAPoints\":175,\"teamBPoints\":25,\"teamATichuMod\":100,\"teamBTichuMod\":0,\"teamADoubleWin\":false,\"teamBDoubleWin\":false,\"sittingOutPlayerId\":\"p3\",\"dealerPlayerId\":\"p1\"}]},{\"id\":\"g_six\",\"sessionId\":\"s1\",\"mode\":\"threeTeams\",\"winner\":\"B\",\"startedAt\":2100,\"endedAt\":2900,\"teams\":[[\"p1\",\"p2\"],[\"p3\",\"p4\"],[\"p5\",\"p6\"]],\"firstRestingTeam\":2,\"pendingNextGame\":true,\"finalScores\":{\"teamA\":300,\"teamB\":1010,\"teamC\":220},\"nextDealerPlayerId\":\"p2\",\"rounds\":[{\"scores\":[125,-25,0],\"activeTeams\":[0,1],\"tichuMods\":[100,0,null],\"doubleWins\":[false,false,false],\"dealerPlayerId\":\"p1\"}]}]",
        "tichuCurrentGameId": "g_six"
      }
    """.trimIndent()

    val preview = BackupSerializer.decodeWithPreview(rawLocalStorageDump, nowMs = 9999L)
    assertTrue(preview.isPwaExport)
    assertEquals(6, preview.playerCount)
    assertEquals(1, preview.sessionCount)
    assertEquals(2, preview.gameCount)
    assertEquals(0, preview.quickRoundsCount)

    val decoded = preview.decoded
    assertEquals(ThemeMode.DARK, decoded.preferences.themeMode)
    assertEquals(1000, decoded.preferences.winThreshold)
    assertFalse(decoded.preferences.chipsVisible)
    assertTrue(decoded.preferences.modeHintDismissed)
    assertEquals(RankingMetric.WIN_RATE, decoded.preferences.playersRankingSort)
    assertEquals(RankingMetric.DOUBLE_WINS, decoded.preferences.sessionRankingSort)
    assertEquals("g_six", decoded.preferences.currentSessionGameId)
    // Active session mirror roundScores must NOT create a duplicate Quick Game
    assertNull(decoded.preferences.quickGameId)

    val session = decoded.sessions.single()
    assertEquals(SessionStatus.PAUSED, session.status)
    assertEquals(1, session.nextPoolStart)
    assertEquals(2, session.pauseLog.size)

    val rotGame = decoded.games.first { it.id == "g_rot" }
    assertEquals(GameMode.ROTATION, rotGame.mode)
    assertEquals("p3", rotGame.rounds.single().sittingOutPlayerId)
    assertEquals("p1", rotGame.rounds.single().dealerPlayerId)
    assertEquals(listOf(100, 0), rotGame.rounds.single().tichuMods)

    val sixGame = decoded.games.first { it.id == "g_six" }
    assertEquals(GameMode.THREE_TEAMS, sixGame.mode)
    assertTrue(sixGame.pendingNextGame)
    assertEquals(FinalScores(300, 1010, 220), sixGame.finalScores)
    // Null tichuMod for resting team maps cleanly to 0
    assertEquals(listOf(100, 0, 0), sixGame.rounds.single().tichuMods)
    assertEquals(listOf(0, 1), sixGame.rounds.single().activeTeams)
  }

  @Test
  fun `decodes quick game roundScores with HTML tichu badges when no session game is active`() {
    val pwaJson = """
      {
        "teamAName": "North",
        "teamBName": "South",
        "winThreshold": 500,
        "A_points": 345,
        "B_points": -145,
        "roundScores": [
          {
            "teamA": 255,
            "teamB": 55,
            "teamATichu": "⇉ GT",
            "teamBTichu": "<s>T</s>"
          },
          {
            "teamA": 345,
            "teamB": -145,
            "teamATichu": "T",
            "teamBTichu": "<s>GT</s>"
          }
        ]
      }
    """.trimIndent()

    val preview = BackupSerializer.decodeWithPreview(pwaJson, nowMs = 5000L)
    assertTrue(preview.isPwaExport)
    assertEquals(2, preview.quickRoundsCount)

    val quickGame = preview.decoded.games.single()
    assertEquals(GameMode.QUICK, quickGame.mode)
    assertEquals("North", quickGame.teamAName)
    assertEquals("South", quickGame.teamBName)
    assertEquals(500, quickGame.winThreshold)
    assertEquals("q_pwa_imported", preview.decoded.preferences.quickGameId)

    val r1 = quickGame.rounds[0]
    assertEquals(listOf(255, 55), r1.scores)
    assertEquals(listOf(200, -100), r1.tichuMods)
    assertEquals(listOf(true, false), r1.doubleWins)

    val r2 = quickGame.rounds[1]
    assertEquals(listOf(345, -145), r2.scores)
    assertEquals(listOf(100, -200), r2.tichuMods)
    assertEquals(listOf(false, false), r2.doubleWins)
  }

  @Test
  fun `decodeWithPreview distinguishes native schema v1 backup from PWA export`() {
    val nativeJson = BackupSerializer.encode(
      preferences = AppPreferences(winThreshold = 750),
      players = emptyList(),
      sessions = emptyList(),
      games = emptyList(),
      exportedAt = 1234L,
    )

    val preview = BackupSerializer.decodeWithPreview(nativeJson)
    assertFalse(preview.isPwaExport)
    assertEquals(750, preview.decoded.preferences.winThreshold)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `rejects empty or unrecognized json objects`() {
    PwaMigrationImporter.decode("""{"unrelatedKey": 123}""")
  }
}

package com.example

import com.example.data.preferences.AppPreferences
import com.example.data.remote.FirestoreMapper
import com.example.domain.FinalScores
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.PauseInterval
import com.example.domain.Player
import com.example.domain.PlayerInvite
import com.example.domain.RankingMetric
import com.example.domain.RoundRecord
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.ThemeMode
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreMapperTest {

  @Test
  fun userSettings_roundTripAndClamping() {
    val prefs = AppPreferences(
      themeMode = ThemeMode.DARK,
      winThreshold = 200_000, // exceeds max 100000
      chipsVisible = false,
      modeHintDismissed = true,
      playersRankingSort = RankingMetric.WIN_RATE,
      sessionRankingSort = RankingMetric.TICHU_NET,
      currentSessionGameId = "game_1",
      quickGameId = "quick_1",
      quickTeamAName = "  Red Dragons  ",
      quickTeamBName = "   ", // should default to Team B
    )
    val map = FirestoreMapper.userSettingsToCreateMap(
      uid = "user_abc",
      displayName = "Alice",
      prefs = prefs,
      localMigratedAt = 123456L,
    )
    assertEquals("user_abc", map["uid"])
    assertEquals(100_000, map["winThreshold"])
    assertEquals("Red Dragons", map["quickTeamAName"])
    assertEquals("Team B", map["quickTeamBName"])
    assertTrue(map["createdAt"] is FieldValue)
    assertTrue(map["updatedAt"] is FieldValue)

    val restored = FirestoreMapper.preferencesFromMap(map)
    assertEquals(ThemeMode.DARK, restored.themeMode)
    assertEquals(100_000, restored.winThreshold)
    assertFalse(restored.chipsVisible)
    assertTrue(restored.modeHintDismissed)
    assertEquals(RankingMetric.WIN_RATE, restored.playersRankingSort)
    assertEquals(RankingMetric.TICHU_NET, restored.sessionRankingSort)
    assertEquals("game_1", restored.currentSessionGameId)
    assertEquals("quick_1", restored.quickGameId)
    assertEquals("Red Dragons", restored.quickTeamAName)
    assertEquals("Team B", restored.quickTeamBName)
  }

  @Test
  fun player_roundTripWithLinkedFieldsAndOmittedNulls() {
    val guest = Player(
      id = "p1",
      name = "Bob",
      active = true,
      createdAt = 1000L,
      statsResetAt = null,
      ownerUid = "host_1",
      linkedUid = null,
      linkedLabel = null,
    )
    val guestMap = FirestoreMapper.playerToCreateMap(guest, "host_1")
    assertFalse("linkedUid" in guestMap)
    assertFalse("linkedLabel" in guestMap)
    assertFalse("statsResetAt" in guestMap)
    assertEquals(listOf("host_1"), guestMap["memberUids"])

    val linked = guest.copy(
      linkedUid = "friend_2",
      linkedLabel = "Bob <bob@example.com>",
      statsResetAt = 5000L,
    )
    val linkedMap = FirestoreMapper.playerToCreateMap(linked, "host_1")
    assertEquals(listOf("host_1", "friend_2"), linkedMap["memberUids"])
    assertEquals("friend_2", linkedMap["linkedUid"])
    assertEquals("Bob <bob@example.com>", linkedMap["linkedLabel"])
    assertEquals(5000L, linkedMap["statsResetAt"])

    val restored = FirestoreMapper.playerFromMap("p1", linkedMap)
    assertEquals("p1", restored.id)
    assertEquals("Bob", restored.name)
    assertEquals("host_1", restored.ownerUid)
    assertEquals("friend_2", restored.linkedUid)
    assertEquals("Bob <bob@example.com>", restored.linkedLabel)
    assertEquals(5000L, restored.statsResetAt)
  }

  @Test
  fun playerInvite_toCreateMap() {
    val invite = PlayerInvite(
      code = "ABCD1234",
      ownerUid = "host_1",
      playerId = "p1",
      playerName = "Bob",
      sessionIds = listOf("s1", "s2"),
      gameIds = listOf("g1"),
      status = "pending",
      expiresAtMs = 999999L,
      claimedByUid = null,
    )
    val map = FirestoreMapper.playerInviteToCreateMap(invite)
    assertFalse("claimedByUid" in map)
    assertEquals("pending", map["status"])
    assertEquals(listOf("s1", "s2"), map["sessionIds"])
    assertEquals(listOf("g1"), map["gameIds"])
    assertEquals(999999L, map["expiresAtMs"])
  }

  @Test
  fun session_roundTripPauseLogAndParticipantUids() {
    val playersById = mapOf(
      "p1" to Player(id = "p1", name = "A", createdAt = 100L, ownerUid = "host_1", linkedUid = "host_1"),
      "p2" to Player(id = "p2", name = "B", createdAt = 100L, ownerUid = "host_1", linkedUid = "u2"),
      "p3" to Player(id = "p3", name = "C", createdAt = 100L, ownerUid = "host_1", linkedUid = null),
      "p4" to Player(id = "p4", name = "D", createdAt = 100L, ownerUid = "host_1", linkedUid = "u2"),
    )
    val participantUids = FirestoreMapper.participantUidsFor(
      ownerUid = "host_1",
      playerIds = listOf("p1", "p2", "p3", "p4"),
      playersById = playersById,
    )
    assertEquals(listOf("host_1", "u2"), participantUids)

    val session = Session(
      id = "s1",
      status = SessionStatus.PAUSED,
      playerIds = listOf("p1", "p2", "p3", "p4"),
      nextPoolStart = 2,
      pauseLog = listOf(
        PauseInterval(pausedAt = 100L, resumedAt = 200L),
        PauseInterval(pausedAt = 300L, resumedAt = null),
      ),
      startedAt = 50L,
      endedAt = null,
    )
    val map = FirestoreMapper.sessionToCreateMap(session, "host_1", participantUids)
    assertFalse("endedAtMs" in map)
    val restored = FirestoreMapper.sessionFromMap("s1", map)
    assertEquals(SessionStatus.PAUSED, restored.status)
    assertEquals(listOf("p1", "p2", "p3", "p4"), restored.playerIds)
    assertEquals(2, restored.nextPoolStart)
    assertEquals(2, restored.pauseLog.size)
    assertEquals(200L, restored.pauseLog[0].resumedAt)
    assertNull(restored.pauseLog[1].resumedAt)
    assertEquals("host_1", restored.ownerUid)
    assertEquals(listOf("host_1", "u2"), restored.participantUids)
  }

  @Test
  fun game_threeTeamsAndRotationRoundTrip() {
    val threeTeamGame = Game(
      id = "g3",
      sessionId = "s1",
      mode = GameMode.THREE_TEAMS,
      teams = listOf(listOf("p1", "p2"), listOf("p3", "p4"), listOf("p5", "p6")),
      firstRestingTeam = 1,
      winThreshold = 500,
      winner = "B",
      pendingNextGame = true,
      finalScores = FinalScores(teamA = 320, teamB = 515, teamC = 410),
      nextDealerPlayerId = "p3",
      startedAt = 1000L,
      endedAt = 2000L,
      rounds = listOf(
        RoundRecord(
          id = 1L,
          gameId = "g3",
          roundNumber = 1,
          scores = listOf(75, 125, 0),
          activeTeams = listOf(0, 1),
          tichuMods = listOf(0, 100),
          doubleWins = listOf(false, false),
          dealerPlayerId = "p1",
        ),
      ),
    )

    val map3 = FirestoreMapper.gameToCreateMap(threeTeamGame, "host_1", listOf("host_1", "u2"))
    val restored3 = FirestoreMapper.gameFromMap("g3", map3)
    assertEquals(GameMode.THREE_TEAMS, restored3.mode)
    assertEquals(3, restored3.teams.size)
    assertEquals(1, restored3.firstRestingTeam)
    assertEquals("B", restored3.winner)
    assertEquals(FinalScores(320, 515, 410), restored3.finalScores)
    assertEquals(1, restored3.rounds.size)
    assertEquals(listOf(75, 125, 0), restored3.rounds[0].scores)
    assertEquals("p1", restored3.rounds[0].dealerPlayerId)

    val rotationGame = Game(
      id = "g5",
      sessionId = "s2",
      mode = GameMode.ROTATION,
      fixedTeam = listOf("p1", "p2"),
      pool = listOf("p3", "p4", "p5"),
      winThreshold = 500,
      startedAt = 5000L,
    )
    val map5 = FirestoreMapper.gameToCreateMap(rotationGame, "host_1", listOf("host_1"))
    val restored5 = FirestoreMapper.gameFromMap("g5", map5)
    assertEquals(GameMode.ROTATION, restored5.mode)
    assertTrue(restored5.teams.isEmpty())
    assertEquals(listOf("p1", "p2"), restored5.fixedTeam)
    assertEquals(listOf("p3", "p4", "p5"), restored5.pool)
    assertNull(restored5.finalScores)
  }

  @Test
  fun sanitizeDocId_handlesSpecialCharactersAndBlanks() {
    assertEquals("abc_123-XYZ", FirestoreMapper.sanitizeDocId("abc/123-XYZ"))
    assertEquals("id_item", FirestoreMapper.sanitizeDocId(""))
    assertTrue(FirestoreMapper.isValidDocId("q_1234-abc"))
    assertFalse(FirestoreMapper.isValidDocId("bad/id"))
  }
}

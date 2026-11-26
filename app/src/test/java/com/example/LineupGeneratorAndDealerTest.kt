package com.example

import com.example.domain.DealerSelector
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.Lineup
import com.example.domain.LineupGenerator
import com.example.domain.RoundRecord
import com.example.domain.Session
import com.example.domain.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LineupGeneratorAndDealerTest {

  @Test
  fun `fourPlayerSplits produces 3 unique partitions and avoids repeats on next game`() {
    val players = listOf("p1", "p2", "p3", "p4")
    val splits = LineupGenerator.fourPlayerSplits(players)
    assertEquals(3, splits.size)

    val game1 = Game(
      id = "g1",
      sessionId = "s1",
      mode = GameMode.FIXED,
      teams = listOf(listOf("p1", "p2"), listOf("p3", "p4")),
      winner = "A",
      startedAt = 100L,
    )
    val session = Session(
      id = "s1",
      status = SessionStatus.ACTIVE,
      playerIds = players,
      startedAt = 100L,
    )
    val counts = LineupGenerator.sessionPairingCounts(listOf(game1))
    assertEquals(2, counts.values.sum())

    val preview = LineupGenerator.previewLineupForSession(session, listOf(game1), Random(42)) as Lineup.Fixed
    assertEquals(0, LineupGenerator.scoreLineup(preview, counts))
  }

  @Test
  fun `fivePlayerLineups covers all 10 fixed pairs and rotates sit-out in round robin`() {
    val players = listOf("p1", "p2", "p3", "p4", "p5")
    val all = LineupGenerator.fivePlayerLineups(players)
    assertEquals(10, all.size)

    val game = Game(
      id = "g1",
      sessionId = "s1",
      mode = GameMode.ROTATION,
      fixedTeam = listOf("p1", "p2"),
      pool = listOf("p3", "p4", "p5"),
      startedAt = 100L,
    )
    val r1 = LineupGenerator.getRoundLineup(game, 1)
    val r2 = LineupGenerator.getRoundLineup(game, 2)
    val r3 = LineupGenerator.getRoundLineup(game, 3)
    val r4 = LineupGenerator.getRoundLineup(game, 4)

    assertEquals("p3", r1.sittingOutPlayerId)
    assertEquals(listOf("p4", "p5"), r1.teamB)
    assertEquals("p4", r2.sittingOutPlayerId)
    assertEquals("p5", r3.sittingOutPlayerId)
    assertEquals("p3", r4.sittingOutPlayerId)
  }

  @Test
  fun `fivePlayer next game minimizes pool exposure variance and rotates first sitter`() {
    val players = listOf("p1", "p2", "p3", "p4", "p5")
    val session = Session(
      id = "s1",
      status = SessionStatus.ACTIVE,
      playerIds = players,
      nextPoolStart = 1,
      startedAt = 100L,
    )
    val game1 = Game(
      id = "g1",
      sessionId = "s1",
      mode = GameMode.ROTATION,
      fixedTeam = listOf("p1", "p2"),
      pool = listOf("p3", "p4", "p5"),
      winner = "A",
      startedAt = 100L,
      rounds = listOf(
        RoundRecord(
          gameId = "g1",
          roundNumber = 1,
          scores = listOf(100, 0),
          tichuMods = listOf(0, 0),
          doubleWins = listOf(false, false),
          sittingOutPlayerId = "p3",
        ),
      ),
    )

    val preview = LineupGenerator.previewLineupForSession(session, listOf(game1), Random(1)) as Lineup.Rotation
    // Variance is minimized when p1 and p2 are placed into the pool for game 2.
    assertTrue("p1" in preview.pool)
    assertTrue("p2" in preview.pool)
    // Next pool start = 1, and p3 already sat out first in game 1 -> first sitter shouldn't be p3
    assertNotEquals("p3", preview.pool[0])
  }

  @Test
  fun `sixPlayerLineups has 15 splits and round lineup rotates resting team`() {
    val players = listOf("p1", "p2", "p3", "p4", "p5", "p6")
    assertEquals(15, LineupGenerator.sixPlayerLineups(players).size)

    val game = Game(
      id = "g1",
      sessionId = "s1",
      mode = GameMode.THREE_TEAMS,
      teams = listOf(listOf("p1", "p2"), listOf("p3", "p4"), listOf("p5", "p6")),
      firstRestingTeam = 1,
      startedAt = 100L,
    )
    val r1 = LineupGenerator.getRoundLineup(game, 1)
    val r2 = LineupGenerator.getRoundLineup(game, 2)
    val r3 = LineupGenerator.getRoundLineup(game, 3)

    assertEquals(1, r1.restingTeamIndex)
    assertEquals(listOf(0, 2), r1.activeIndices)
    assertEquals(2, r2.restingTeamIndex)
    assertEquals(listOf(0, 1), r2.activeIndices)
    assertEquals(0, r3.restingTeamIndex)
    assertEquals(listOf(1, 2), r3.activeIndices)
  }

  @Test
  fun `swapPlayers swaps across teams or pool`() {
    val lineup = Lineup.Fixed(listOf(listOf("p1", "p2"), listOf("p3", "p4")))
    val swapped = LineupGenerator.swapPlayers(lineup, "p2", "p3") as Lineup.Fixed
    assertEquals(listOf(listOf("p1", "p3"), listOf("p2", "p4")), swapped.teams)
  }

  @Test
  fun `dealerSelector alternates opposite active team and respects lowest deal count and recency`() {
    val game = Game(
      id = "g1",
      sessionId = "s1",
      mode = GameMode.ROTATION,
      fixedTeam = listOf("p1", "p2"),
      pool = listOf("p3", "p4", "p5"),
      startedAt = 100L,
    )
    val lineup = LineupGenerator.getRoundLineup(game, 1) // p3 sits out; active B is p4, p5
    val dealer = DealerSelector.pickDealerForRound(
      game = game,
      lineup = lineup,
      prevDealerId = "p1", // Team A -> should pick from active Team B (p4 or p5)
      counts = mapOf("p1" to 2, "p2" to 1, "p3" to 0, "p4" to 1, "p5" to 1),
      history = listOf("p4", "p1"), // p5 dealt less recently than p4 (lastIndexOf -1 vs 0)
    )
    assertEquals("p5", dealer)
  }
}

package com.example

import com.example.data.local.GameEntity
import com.example.data.local.RoundEntity
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.RoundRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class EntityMappingTest {

  @Test
  fun `round and game entity round-trip preserves scores mods and double wins`() {
    val round = RoundRecord(
      id = 7L,
      gameId = "g1",
      roundNumber = 2,
      scores = listOf(145, 55),
      activeTeams = listOf(0, 1),
      tichuMods = listOf(100, -100),
      doubleWins = listOf(true, false),
      sittingOutPlayerId = "p5",
      dealerPlayerId = "p2",
    )
    val game = Game(
      id = "g1",
      sessionId = "s1",
      mode = GameMode.ROTATION,
      fixedTeam = listOf("p1", "p2"),
      pool = listOf("p3", "p4", "p5"),
      winThreshold = 500,
      startedAt = 1234L,
      rounds = listOf(round),
    )

    val roundEntity = RoundEntity.fromDomain(round)
    val gameEntity = GameEntity.fromDomain(game)
    val decodedGame = gameEntity.toDomain(listOf(roundEntity.toDomain()))

    assertEquals(game, decodedGame)
    assertEquals(listOf(145, 55), decodedGame.rounds.single().scores)
    assertEquals(listOf(100, -100), decodedGame.rounds.single().tichuMods)
    assertEquals(listOf(true, false), decodedGame.rounds.single().doubleWins)
  }
}

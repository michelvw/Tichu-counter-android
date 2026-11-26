package com.example

import com.example.domain.ScoreboardDraft
import com.example.domain.ScoringEngine
import com.example.domain.TeamRoundInput
import com.example.domain.TichuCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

  @Test
  fun `adjustCardPoints keeps sum at 100 and clamps to minus 25 through 125`() {
    var draft = ScoreboardDraft()
    draft = ScoringEngine.adjustCardPoints(draft, isTeamA = true, deltaSteps = 20)
    assertEquals(125, draft.teamA.cardPoints)
    assertEquals(-25, draft.teamB.cardPoints)

    draft = ScoringEngine.adjustCardPoints(draft, isTeamA = true, deltaSteps = -40)
    assertEquals(-25, draft.teamA.cardPoints)
    assertEquals(125, draft.teamB.cardPoints)

    draft = ScoringEngine.adjustCardPoints(draft, isTeamA = false, deltaSteps = -5)
    assertEquals(100, draft.teamB.cardPoints)
    assertEquals(0, draft.teamA.cardPoints)
  }

  @Test
  fun `toggleDoubleWin is mutually exclusive and applies 200 to winner and 0 card points to loser`() {
    var draft = ScoreboardDraft(
      teamA = TeamRoundInput(cardPoints = 70, tichuCall = TichuCall.TICHU_WON),
      teamB = TeamRoundInput(cardPoints = 30, tichuCall = TichuCall.GRAND_TICHU_LOST),
    )
    draft = ScoringEngine.toggleDoubleWin(draft, isTeamA = true, checked = true)
    assertTrue(draft.teamA.doubleWin)
    assertFalse(draft.teamB.doubleWin)

    val (deltaA, deltaB) = ScoringEngine.computeDraftDeltas(draft)
    assertEquals(300, deltaA) // 200 double win + 100 Tichu won
    assertEquals(-200, deltaB) // 0 card points (lost double win) - 200 Grand Tichu lost

    draft = ScoringEngine.toggleDoubleWin(draft, isTeamA = false, checked = true)
    assertFalse(draft.teamA.doubleWin)
    assertTrue(draft.teamB.doubleWin)
    val (deltaA2, deltaB2) = ScoringEngine.computeDraftDeltas(draft)
    assertEquals(100, deltaA2)
    assertEquals(0, deltaB2) // 200 double win - 200 Grand Tichu lost
  }

  @Test
  fun `applyRoundToTwoTeamTotals accumulates deltas and detects threshold crossings`() {
    val draft = ScoreboardDraft(
      teamA = TeamRoundInput(cardPoints = 100, tichuCall = TichuCall.TICHU_WON),
      teamB = TeamRoundInput(cardPoints = 0, tichuCall = TichuCall.NONE),
    )
    val prev = listOf(350, 480)
    val next = ScoringEngine.applyRoundToTwoTeamTotals(prev, draft)
    assertEquals(listOf(550, 480), next)
    assertEquals(listOf(0), ScoringEngine.newlyCrossedTwoTeamIndices(prev, next, 500))
    assertEquals(listOf(0), ScoringEngine.thresholdReachedTwoTeamIndices(next, 500))
  }

  @Test
  fun `evaluateThreeTeamWinner requires threshold and a strict single leader`() {
    assertNull(ScoringEngine.evaluateThreeTeamWinner(listOf(495, 450, 300), 500))
    assertNull(ScoringEngine.evaluateThreeTeamWinner(listOf(520, 520, 400), 500))
    assertEquals("B", ScoringEngine.evaluateThreeTeamWinner(listOf(520, 540, 400), 500))
    assertEquals("C", ScoringEngine.evaluateThreeTeamWinner(listOf(100, 200, 500), 500))
  }

  @Test
  fun `applyRoundToThreeTeamTotals updates only active team indices`() {
    val draft = ScoreboardDraft(
      teamA = TeamRoundInput(cardPoints = 65, tichuCall = TichuCall.NONE),
      teamB = TeamRoundInput(cardPoints = 35, tichuCall = TichuCall.TICHU_LOST),
    )
    val updated = ScoringEngine.applyRoundToThreeTeamTotals(
      currentTotals = listOf(100, 200, 300),
      activeIndices = listOf(0, 2),
      draft = draft,
    )
    assertEquals(listOf(165, 200, 235), updated)
  }
}

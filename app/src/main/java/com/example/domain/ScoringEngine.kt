package com.example.domain

object ScoringEngine {
  const val MIN_CARD_POINTS = -25
  const val MAX_CARD_POINTS = 125
  const val CARD_POINTS_SUM = 100
  const val STEP_POINTS = 5
  const val DOUBLE_WIN_POINTS = 200
  const val DEFAULT_WIN_THRESHOLD = 500

  fun adjustCardPoints(draft: ScoreboardDraft, isTeamA: Boolean, deltaSteps: Int): ScoreboardDraft {
    val delta = deltaSteps * STEP_POINTS
    return if (isTeamA) {
      val nextA = (draft.teamA.cardPoints + delta).coerceIn(MIN_CARD_POINTS, MAX_CARD_POINTS)
      val nextB = CARD_POINTS_SUM - nextA
      draft.copy(
        teamA = draft.teamA.copy(cardPoints = nextA),
        teamB = draft.teamB.copy(cardPoints = nextB),
      )
    } else {
      val nextB = (draft.teamB.cardPoints + delta).coerceIn(MIN_CARD_POINTS, MAX_CARD_POINTS)
      val nextA = CARD_POINTS_SUM - nextB
      draft.copy(
        teamA = draft.teamA.copy(cardPoints = nextA),
        teamB = draft.teamB.copy(cardPoints = nextB),
      )
    }
  }

  fun setTichuCall(draft: ScoreboardDraft, isTeamA: Boolean, call: TichuCall): ScoreboardDraft =
    if (isTeamA) {
      draft.copy(teamA = draft.teamA.copy(tichuCall = call))
    } else {
      draft.copy(teamB = draft.teamB.copy(tichuCall = call))
    }

  fun toggleDoubleWin(draft: ScoreboardDraft, isTeamA: Boolean, checked: Boolean): ScoreboardDraft =
    if (isTeamA) {
      draft.copy(
        teamA = draft.teamA.copy(doubleWin = checked),
        teamB = if (checked) draft.teamB.copy(doubleWin = false) else draft.teamB,
      )
    } else {
      draft.copy(
        teamB = draft.teamB.copy(doubleWin = checked),
        teamA = if (checked) draft.teamA.copy(doubleWin = false) else draft.teamA,
      )
    }

  fun computeRoundDelta(input: TeamRoundInput, opponentDoubleWin: Boolean): Int = when {
    input.doubleWin -> DOUBLE_WIN_POINTS + input.tichuCall.mod
    opponentDoubleWin -> input.tichuCall.mod
    else -> input.cardPoints + input.tichuCall.mod
  }

  fun computeDraftDeltas(draft: ScoreboardDraft): Pair<Int, Int> {
    val deltaA = computeRoundDelta(draft.teamA, draft.teamB.doubleWin)
    val deltaB = computeRoundDelta(draft.teamB, draft.teamA.doubleWin)
    return deltaA to deltaB
  }

  fun applyRoundToTwoTeamTotals(currentTotals: List<Int>, draft: ScoreboardDraft): List<Int> {
    val baseA = currentTotals.getOrElse(0) { 0 }
    val baseB = currentTotals.getOrElse(1) { 0 }
    val (deltaA, deltaB) = computeDraftDeltas(draft)
    return listOf(baseA + deltaA, baseB + deltaB)
  }

  fun applyRoundToThreeTeamTotals(
    currentTotals: List<Int>,
    activeIndices: List<Int>,
    draft: ScoreboardDraft,
  ): List<Int> {
    val updated = MutableList(3) { index -> currentTotals.getOrElse(index) { 0 } }
    val (deltaA, deltaB) = computeDraftDeltas(draft)
    if (activeIndices.size >= 2) {
      updated[activeIndices[0]] += deltaA
      updated[activeIndices[1]] += deltaB
    }
    return updated
  }

  /**
   * Mirrors `finishThreeTeamGameIfWon`: a 3-team game only wins when the top
   * cumulative score reaches [threshold] AND there is a single strict leader.
   */
  fun evaluateThreeTeamWinner(scores: List<Int>, threshold: Int): String? {
    if (scores.size < 3) return null
    val max = scores.max()
    if (max < threshold) return null
    val leaders = scores.indices.filter { scores[it] == max }
    if (leaders.size != 1) return null
    return when (leaders[0]) {
      0 -> "A"
      1 -> "B"
      2 -> "C"
      else -> null
    }
  }

  /**
   * Returns side indices (0 for Team A, 1 for Team B) that newly crossed [threshold].
   */
  fun newlyCrossedTwoTeamIndices(prevTotals: List<Int>, nextTotals: List<Int>, threshold: Int): List<Int> =
    listOf(0, 1).filter { idx ->
      prevTotals.getOrElse(idx) { 0 } < threshold && nextTotals.getOrElse(idx) { 0 } >= threshold
    }

  /**
   * Returns side indices currently at or above [threshold].
   */
  fun thresholdReachedTwoTeamIndices(totals: List<Int>, threshold: Int): List<Int> =
    listOf(0, 1).filter { idx -> totals.getOrElse(idx) { 0 } >= threshold }

  fun formatTwoTeamWinMessage(crossedTeamNames: List<String>, threshold: Int): String {
    val verb = if (crossedTeamNames.size > 1) "have" else "has"
    return "${crossedTeamNames.joinToString(" & ")} $verb reached $threshold points! \uD83C\uDF89"
  }
}

package com.example.domain

import kotlin.random.Random

object LineupGenerator {

  fun <T> rotateList(items: List<T>, startIndex: Int): List<T> {
    if (items.isEmpty()) return emptyList()
    val n = items.size
    val s = ((startIndex % n) + n) % n
    return items.drop(s) + items.take(s)
  }

  fun previewRandomLineup(playerIds: List<String>, random: Random = Random.Default): Lineup {
    require(playerIds.size in 4..6 && playerIds.distinct().size == playerIds.size) {
      "Sessions support 4, 5 or 6 distinct players."
    }
    val shuffled = playerIds.shuffled(random)
    return when (playerIds.size) {
      4 -> Lineup.Fixed(listOf(shuffled.subList(0, 2), shuffled.subList(2, 4)))
      5 -> Lineup.Rotation(fixedTeam = shuffled.subList(0, 2), pool = shuffled.subList(2, 5))
      else -> Lineup.ThreeTeams(
        listOf(
          shuffled.subList(0, 2),
          shuffled.subList(2, 4),
          shuffled.subList(4, 6),
        ),
      )
    }
  }

  fun isValidLineupFor(playerIds: List<String>, lineup: Lineup): Boolean {
    val expected = playerIds.sorted()
    return when (lineup) {
      is Lineup.Fixed -> playerIds.size == 4 &&
        lineup.teams.size == 2 &&
        lineup.teams.all { it.size == 2 } &&
        lineup.teams.flatten().sorted() == expected

      is Lineup.Rotation -> playerIds.size == 5 &&
        lineup.fixedTeam.size == 2 &&
        lineup.pool.size == 3 &&
        (lineup.fixedTeam + lineup.pool).sorted() == expected

      is Lineup.ThreeTeams -> playerIds.size == 6 &&
        lineup.teams.size == 3 &&
        lineup.teams.all { it.size == 2 } &&
        lineup.teams.flatten().sorted() == expected
    }
  }

  fun getRoundLineup(game: Game, roundNumber: Int): RoundLineup {
    val safeRound = roundNumber.coerceAtLeast(1)
    return when (game.mode) {
      GameMode.THREE_TEAMS -> {
        val firstRest = game.firstRestingTeam ?: 0
        val restIndex = (firstRest + safeRound - 1) % 3
        val activeIndices = listOf(0, 1, 2).filter { it != restIndex }
        RoundLineup(
          teamA = game.teams.getOrElse(activeIndices[0]) { emptyList() },
          teamB = game.teams.getOrElse(activeIndices[1]) { emptyList() },
          activeIndices = activeIndices,
          restingTeamIndex = restIndex,
          sittingOutTeamIds = game.teams.getOrElse(restIndex) { emptyList() },
        )
      }

      GameMode.ROTATION -> {
        val idx = (safeRound - 1) % 3
        val sittingOut = game.pool.getOrNull(idx)
        val activePool = game.pool.filter { it != sittingOut }
        RoundLineup(
          teamA = game.fixedTeam,
          teamB = activePool,
          activeIndices = listOf(0, 1),
          sittingOutPlayerId = sittingOut,
        )
      }

      GameMode.FIXED, GameMode.QUICK -> {
        RoundLineup(
          teamA = game.teams.getOrElse(0) { emptyList() },
          teamB = game.teams.getOrElse(1) { emptyList() },
          activeIndices = listOf(0, 1),
        )
      }
    }
  }

  fun fourPlayerSplits(playerIds: List<String>): List<Lineup.Fixed> {
    require(playerIds.size == 4)
    val anchor = playerIds[0]
    val rest = playerIds.drop(1)
    return rest.mapIndexed { idx, partner ->
      val others = rest.filterIndexed { i, _ -> i != idx }
      Lineup.Fixed(listOf(listOf(anchor, partner), others))
    }
  }

  fun fivePlayerLineups(playerIds: List<String>): List<Lineup.Rotation> {
    require(playerIds.size == 5)
    val lineups = mutableListOf<Lineup.Rotation>()
    for (i in playerIds.indices) {
      for (j in i + 1 until playerIds.size) {
        val fixed = listOf(playerIds[i], playerIds[j])
        val pool = playerIds.filter { it != fixed[0] && it != fixed[1] }
        lineups += Lineup.Rotation(fixedTeam = fixed, pool = pool)
      }
    }
    return lineups
  }

  fun sixPlayerLineups(playerIds: List<String>): List<Lineup.ThreeTeams> {
    require(playerIds.size == 6)
    val anchor = playerIds[0]
    val lineups = mutableListOf<Lineup.ThreeTeams>()
    for (partner in playerIds.drop(1)) {
      val remaining = playerIds.filter { it != anchor && it != partner }
      for (split in fourPlayerSplits(remaining)) {
        lineups += Lineup.ThreeTeams(listOf(listOf(anchor, partner)) + split.teams)
      }
    }
    return lineups
  }

  private fun pairKey(a: String, b: String): String =
    if (a <= b) "$a|$b" else "$b|$a"

  fun sessionPairingCounts(games: List<Game>): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    fun bump(a: String, b: String) {
      val k = pairKey(a, b)
      counts[k] = (counts[k] ?: 0) + 1
    }
    for (g in games) {
      if (g.winner == null) continue
      when (g.mode) {
        GameMode.ROTATION -> {
          if (g.fixedTeam.size >= 2) bump(g.fixedTeam[0], g.fixedTeam[1])
          for (r in g.rounds) {
            val sitter = r.sittingOutPlayerId ?: continue
            val active = g.pool.filter { it != sitter }
            if (active.size == 2) bump(active[0], active[1])
          }
        }

        GameMode.THREE_TEAMS -> {
          for (team in g.teams) {
            if (team.size >= 2) bump(team[0], team[1])
          }
        }

        GameMode.FIXED -> {
          if (g.teams.size >= 2) {
            if (g.teams[0].size >= 2) bump(g.teams[0][0], g.teams[0][1])
            if (g.teams[1].size >= 2) bump(g.teams[1][0], g.teams[1][1])
          }
        }

        GameMode.QUICK -> Unit
      }
    }
    return counts
  }

  fun scoreLineup(lineup: Lineup, counts: Map<String, Int>): Int {
    fun get(a: String, b: String): Int = counts[pairKey(a, b)] ?: 0
    return when (lineup) {
      is Lineup.ThreeTeams -> lineup.teams.sumOf { get(it[0], it[1]) }
      is Lineup.Fixed -> get(lineup.teams[0][0], lineup.teams[0][1]) +
        get(lineup.teams[1][0], lineup.teams[1][1])
      is Lineup.Rotation -> {
        val p = lineup.pool
        get(lineup.fixedTeam[0], lineup.fixedTeam[1]) +
          get(p[0], p[1]) + get(p[0], p[2]) + get(p[1], p[2])
      }
    }
  }

  private fun poolExposureCounts(games: List<Game>): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    for (g in games) {
      if (g.winner == null) continue
      if (g.mode == GameMode.ROTATION) {
        for (pid in g.pool) {
          counts[pid] = (counts[pid] ?: 0) + 1
        }
      }
    }
    return counts
  }

  private fun sitOutFirstCounts(games: List<Game>): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    for (g in games) {
      if (g.winner == null) continue
      if (g.mode == GameMode.ROTATION && g.pool.size == 3) {
        val firstSitter = g.pool[0]
        counts[firstSitter] = (counts[firstSitter] ?: 0) + 1
      }
    }
    return counts
  }

  fun previewLineupForSession(
    session: Session,
    sessionGames: List<Game>,
    random: Random = Random.Default,
  ): Lineup {
    val playerIds = session.playerIds
    val hasPriorGames = sessionGames.any { it.winner != null }
    if (!hasPriorGames) return previewRandomLineup(playerIds, random)

    if (playerIds.size == 6) {
      val sixCounts = sessionPairingCounts(sessionGames)
      val sixCandidates = sixPlayerLineups(playerIds)
      val best = sixCandidates.minOf { scoreLineup(it, sixCounts) }
      val bestLineups = sixCandidates.filter { scoreLineup(it, sixCounts) == best }
      val picked = bestLineups[random.nextInt(bestLineups.size)]
      return Lineup.ThreeTeams(picked.teams.shuffled(random))
    }

    if (playerIds.size == 4) {
      val candidates = fourPlayerSplits(playerIds)
      val counts = sessionPairingCounts(sessionGames)
      val minScore = candidates.minOf { scoreLineup(it, counts) }
      val bestSet = candidates.filter { scoreLineup(it, counts) == minScore }
      val chosen = bestSet[random.nextInt(bestSet.size)]
      val teams = if (random.nextBoolean()) {
        listOf(chosen.teams[0], chosen.teams[1])
      } else {
        listOf(chosen.teams[1], chosen.teams[0])
      }
      return Lineup.Fixed(teams)
    }

    // 5-player rotation fairness + variance + repeat pairing minimization
    val candidates = fivePlayerLineups(playerIds)
    val counts = sessionPairingCounts(sessionGames)
    val poolCounts = poolExposureCounts(sessionGames)
    val firstSitterCounts = sitOutFirstCounts(sessionGames)
    val nextStart = ((session.nextPoolStart % 3) + 3) % 3

    data class ScoredRotation(
      val lineup: Lineup.Rotation,
      val firstSitterScore: Int,
      val variance: Double,
      val repeatScore: Int,
    )

    val scored = candidates.map { ln ->
      val repeatScore = scoreLineup(ln, counts)
      val countsAfter = playerIds.associateWith { poolCounts[it] ?: 0 }.toMutableMap()
      val rotatedPool = rotateList(ln.pool, nextStart)
      val sitterId = rotatedPool[0]
      val firstSitterScore = firstSitterCounts[sitterId] ?: 0
      for (pid in ln.pool) {
        countsAfter[pid] = (countsAfter[pid] ?: 0) + 1
      }
      val vals = playerIds.map { (countsAfter[it] ?: 0).toDouble() }
      val average = vals.sum() / vals.size
      val variance = vals.sumOf { val d = it - average; d * d }
      ScoredRotation(ln, firstSitterScore, variance, repeatScore)
    }

    val minFirst = scored.minOf { it.firstSitterScore }
    val firstSet = scored.filter { it.firstSitterScore == minFirst }
    val minVariance = firstSet.minOf { it.variance }
    val varianceSet = firstSet.filter { it.variance == minVariance }
    val minRepeat = varianceSet.minOf { it.repeatScore }
    val repeatSet = varianceSet.filter { it.repeatScore == minRepeat }
    val chosen = repeatSet[random.nextInt(repeatSet.size)].lineup

    val shuffledPool = chosen.pool.shuffled(random)
    val rotatedPool = rotateList(shuffledPool, nextStart)
    return Lineup.Rotation(fixedTeam = chosen.fixedTeam, pool = rotatedPool)
  }

  /**
   * Aligns pool order to [session.nextPoolStart] before starting a rotation game,
   * matching `startNewGameInSession` in `players-storage.js`.
   */
  fun finalizeRotationLineupForStart(lineup: Lineup.Rotation, nextPoolStart: Int): Lineup.Rotation {
    val start = ((nextPoolStart % 3) + 3) % 3
    return lineup.copy(pool = rotateList(lineup.pool, start))
  }

  /**
   * Picks the first resting team index for a 6-player game, balanced across prior
   * three-team rests in the session.
   */
  fun selectFirstRestingTeam(
    teams: List<List<String>>,
    sessionPlayerIds: List<String>,
    sessionGames: List<Game>,
    random: Random = Random.Default,
  ): Int {
    val rests = sessionPlayerIds.associateWith { 0 }.toMutableMap()
    for (g in sessionGames) {
      if (g.mode != GameMode.THREE_TEAMS) continue
      for (r in g.rounds) {
        val restingTeam = g.teams.filterIndexed { idx, _ -> idx !in r.activeTeams }.firstOrNull()
        restingTeam?.forEach { pid -> rests[pid] = (rests[pid] ?: 0) + 1 }
      }
    }
    val restScores = teams.map { team -> team.sumOf { rests[it] ?: 0 } }
    val least = restScores.minOrNull() ?: 0
    val eligible = listOf(0, 1, 2).filter { restScores.getOrElse(it) { 0 } == least }
    return eligible[random.nextInt(eligible.size)]
  }

  /**
   * Swaps two players anywhere inside a previewed lineup for manual override before
   * committing to a game.
   */
  fun swapPlayers(lineup: Lineup, playerA: String, playerB: String): Lineup {
    if (playerA == playerB) return lineup
    fun swapInList(list: List<String>): List<String> = list.map { id ->
      when (id) {
        playerA -> playerB
        playerB -> playerA
        else -> id
      }
    }
    return when (lineup) {
      is Lineup.Fixed -> Lineup.Fixed(lineup.teams.map(::swapInList))
      is Lineup.Rotation -> Lineup.Rotation(swapInList(lineup.fixedTeam), swapInList(lineup.pool))
      is Lineup.ThreeTeams -> Lineup.ThreeTeams(lineup.teams.map(::swapInList))
    }
  }
}

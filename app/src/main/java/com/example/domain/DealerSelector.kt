package com.example.domain

object DealerSelector {

  fun playerSide(game: Game, playerId: String): String? = when (game.mode) {
    GameMode.THREE_TEAMS -> {
      val idx = game.teams.indexOfFirst { playerId in it }
      when (idx) {
        0 -> "A"
        1 -> "B"
        2 -> "C"
        else -> null
      }
    }

    GameMode.ROTATION -> when {
      playerId in game.fixedTeam -> "A"
      playerId in game.pool -> "B"
      else -> null
    }

    GameMode.FIXED -> when {
      game.teams.getOrNull(0)?.contains(playerId) == true -> "A"
      game.teams.getOrNull(1)?.contains(playerId) == true -> "B"
      else -> null
    }

    GameMode.QUICK -> null
  }

  fun sessionDealCounts(session: Session, sessionGames: List<Game>): MutableMap<String, Int> {
    val counts = session.playerIds.associateWith { 0 }.toMutableMap()
    for (g in sessionGames) {
      for (r in g.rounds) {
        val dealer = r.dealerPlayerId ?: continue
        if (counts.containsKey(dealer)) {
          counts[dealer] = (counts[dealer] ?: 0) + 1
        }
      }
    }
    return counts
  }

  fun sessionDealerHistory(sessionGames: List<Game>): MutableList<String> {
    val history = mutableListOf<String>()
    for (g in sessionGames) {
      for (r in g.rounds) {
        val dealer = r.dealerPlayerId
        if (!dealer.isNullOrEmpty()) history += dealer
      }
    }
    return history
  }

  fun lastRecordedDealer(sessionGames: List<Game>): String? =
    sessionDealerHistory(sessionGames).lastOrNull()

  fun pickDealerForRound(
    game: Game,
    lineup: RoundLineup,
    prevDealerId: String?,
    counts: Map<String, Int>,
    history: List<String>,
  ): String? {
    var active = lineup.teamA + lineup.teamB
    if (active.isEmpty()) return null

    var opposite: String? = null
    if (game.mode == GameMode.THREE_TEAMS && prevDealerId != null) {
      val previousTeam = playerSide(game, prevDealerId)
      val otherTeamPlayers = active.filter { playerSide(game, it) != previousTeam }
      if (otherTeamPlayers.isNotEmpty()) active = otherTeamPlayers
    } else if (prevDealerId != null && playerSide(game, prevDealerId) != null) {
      opposite = if (playerSide(game, prevDealerId) == "A") "B" else "A"
    }

    var candidates = if (opposite != null) {
      active.filter { playerSide(game, it) == opposite }
    } else {
      active
    }
    if (candidates.isEmpty()) candidates = active

    return candidates.sortedWith { a, b ->
      val diff = (counts[a] ?: 0) - (counts[b] ?: 0)
      if (diff != 0) return@sortedWith diff
      val recentA = history.lastIndexOf(a)
      val recentB = history.lastIndexOf(b)
      if (recentA != recentB) return@sortedWith recentA - recentB
      a.compareTo(b)
    }.firstOrNull()
  }
}

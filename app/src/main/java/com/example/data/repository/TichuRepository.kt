package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.backup.BackupSerializer
import com.example.data.local.AppDatabase
import com.example.data.local.GameEntity
import com.example.data.local.PlayerEntity
import com.example.data.local.RoundEntity
import com.example.data.local.SessionEntity
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.PreferencesStore
import com.example.domain.DealerSelector
import com.example.domain.FinalScores
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.Lineup
import com.example.domain.LineupGenerator
import com.example.domain.PauseInterval
import com.example.domain.Player
import com.example.domain.RankingMetric
import com.example.domain.RoundRecord
import com.example.domain.ScoreboardDraft
import com.example.domain.ScoringEngine
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class TichuRepository(
  private val database: AppDatabase,
  private val preferencesStore: PreferencesStore,
) {
  private val playerDao = database.playerDao()
  private val sessionDao = database.sessionDao()
  private val gameDao = database.gameDao()
  private val roundDao = database.roundDao()

  val preferencesFlow: Flow<AppPreferences> = preferencesStore.preferencesFlow

  val playersFlow: Flow<List<Player>> = playerDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  val sessionsFlow: Flow<List<Session>> = sessionDao.observeAll().map { list ->
    list.map { it.toDomain() }
  }

  val gamesFlow: Flow<List<Game>> = gameDao.observeGamesWithRounds().map { list ->
    list.map { it.toDomain() }
  }

  val activeOrPausedSessionFlow: Flow<Session?> = sessionsFlow.map { sessions ->
    sessions.firstOrNull { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }
  }

  val currentSessionGameFlow: Flow<Game?> = combine(
    gamesFlow,
    preferencesFlow,
  ) { games, prefs ->
    val id = prefs.currentSessionGameId ?: return@combine null
    val game = games.firstOrNull { it.id == id } ?: return@combine null
    if (game.winner != null && !game.pendingNextGame) null else game
  }

  val activeQuickGameFlow: Flow<Game?> = combine(
    gamesFlow,
    preferencesFlow,
  ) { games, prefs ->
    val id = prefs.quickGameId
    if (id != null) {
      games.firstOrNull { it.id == id && it.sessionId == null }
    } else {
      games.lastOrNull { it.sessionId == null && it.mode == GameMode.QUICK }
    }
  }

  private fun newId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

  // ---- Players ----

  suspend fun addPlayer(rawName: String): Player {
    val name = rawName.trim()
    require(name.isNotEmpty()) { "Player name can't be empty." }
    val player = Player(
      id = newId("p"),
      name = name,
      active = true,
      createdAt = System.currentTimeMillis(),
      statsResetAt = null,
    )
    playerDao.upsert(PlayerEntity.fromDomain(player))
    return player
  }

  suspend fun renamePlayer(id: String, rawName: String) {
    val name = rawName.trim()
    require(name.isNotEmpty()) { "Player name can't be empty." }
    val existing = playerDao.getById(id) ?: error("Player not found.")
    playerDao.upsert(existing.copy(name = name))
  }

  suspend fun setPlayerActive(id: String, active: Boolean) {
    val existing = playerDao.getById(id) ?: error("Player not found.")
    playerDao.upsert(existing.copy(active = active))
  }

  suspend fun resetPlayerStatistics(id: String) {
    val existing = playerDao.getById(id) ?: error("Player not found.")
    playerDao.upsert(existing.copy(statsResetAt = System.currentTimeMillis()))
  }

  // ---- Sessions ----

  suspend fun startSession(playerIds: List<String>, lineup: Lineup): Pair<Session, Game> {
    val existingSessions = sessionDao.getAll().map { it.toDomain() }
    check(existingSessions.none { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }) {
      "A session is already active or paused. End it before starting a new one."
    }
    require(playerIds.size in 4..6 && playerIds.distinct().size == playerIds.size) {
      "Sessions need 4, 5 or 6 distinct players."
    }
    require(LineupGenerator.isValidLineupFor(playerIds, lineup)) {
      "Selected lineup does not match the chosen players."
    }

    val session = Session(
      id = newId("s"),
      status = SessionStatus.ACTIVE,
      playerIds = playerIds,
      nextPoolStart = 0,
      pauseLog = emptyList(),
      startedAt = System.currentTimeMillis(),
      endedAt = null,
    )
    sessionDao.upsert(SessionEntity.fromDomain(session))
    val game = startNewGameInSession(session.id, lineup)
    return session to game
  }

  suspend fun pauseSession(sessionId: String) {
    val entity = sessionDao.getById(sessionId) ?: error("Session not found.")
    val session = entity.toDomain()
    check(session.status == SessionStatus.ACTIVE) { "Only an active session can be paused." }
    val updated = session.copy(
      status = SessionStatus.PAUSED,
      pauseLog = session.pauseLog + PauseInterval(pausedAt = System.currentTimeMillis(), resumedAt = null),
    )
    sessionDao.upsert(SessionEntity.fromDomain(updated))
  }

  suspend fun resumeSession(sessionId: String) {
    val entity = sessionDao.getById(sessionId) ?: error("Session not found.")
    val session = entity.toDomain()
    check(session.status == SessionStatus.PAUSED) { "Only a paused session can be resumed." }
    val now = System.currentTimeMillis()
    var closed = false
    val updatedLog = session.pauseLog.map { entry ->
      if (!closed && entry.resumedAt == null) {
        closed = true
        entry.copy(resumedAt = now)
      } else {
        entry
      }
    }
    val updated = session.copy(
      status = SessionStatus.ACTIVE,
      pauseLog = updatedLog,
    )
    sessionDao.upsert(SessionEntity.fromDomain(updated))
  }

  suspend fun endSession(sessionId: String) {
    val currentId = preferencesStore.getSnapshot().currentSessionGameId
    database.withTransaction {
      val entity = sessionDao.getById(sessionId) ?: error("Session not found.")
      val session = entity.toDomain()
      if (currentId != null) {
        val currentGame = gameDao.getGameWithRounds(currentId)?.toDomain()
        if (currentGame != null && currentGame.sessionId == sessionId) {
          if (currentGame.winner == null) {
            gameDao.deleteById(currentGame.id)
          }
        }
      }
      val now = System.currentTimeMillis()
      val closedLog = session.pauseLog.map { entry ->
        if (entry.resumedAt == null) entry.copy(resumedAt = now) else entry
      }
      sessionDao.upsert(
        SessionEntity.fromDomain(
          session.copy(
            status = SessionStatus.ENDED,
            endedAt = now,
            pauseLog = closedLog,
          ),
        ),
      )
    }
    if (currentId != null) {
      preferencesStore.setCurrentSessionGameId(null)
    }
  }

  suspend fun startNewGameInSession(sessionId: String, requestedLineup: Lineup? = null): Game {
    val prefs = preferencesStore.getSnapshot()
    val createdGame = database.withTransaction {
      var session = sessionDao.getById(sessionId)?.toDomain() ?: error("Session not found.")
      if (session.status == SessionStatus.PAUSED) {
        val now = System.currentTimeMillis()
        session = session.copy(
          status = SessionStatus.ACTIVE,
          pauseLog = session.pauseLog.map { if (it.resumedAt == null) it.copy(resumedAt = now) else it },
        )
        sessionDao.upsert(SessionEntity.fromDomain(session))
      }
      check(session.status == SessionStatus.ACTIVE) { "This session cannot start a new game." }

      val allGames = gameDao.getAllGamesWithRounds().map { it.toDomain() }
      val sessionGames = allGames.filter { it.sessionId == sessionId }
      val currentId = prefs.currentSessionGameId
      val current = currentId?.let { id -> sessionGames.firstOrNull { it.id == id } }
      if (current != null && !current.pendingNextGame && current.winner == null) {
        error("A game is already in progress for this session.")
      }

      var finalLineup = requestedLineup ?: LineupGenerator.previewLineupForSession(session, sessionGames)
      require(LineupGenerator.isValidLineupFor(session.playerIds, finalLineup)) {
        "That lineup doesn't match this session's players."
      }

      if (finalLineup is Lineup.Rotation) {
        finalLineup = LineupGenerator.finalizeRotationLineupForStart(finalLineup, session.nextPoolStart)
        session = session.copy(nextPoolStart = ((session.nextPoolStart % 3) + 3 + 1) % 3)
        sessionDao.upsert(SessionEntity.fromDomain(session))
      }

      val firstRest = if (finalLineup is Lineup.ThreeTeams) {
        LineupGenerator.selectFirstRestingTeam(finalLineup.teams, session.playerIds, sessionGames)
      } else {
        null
      }

      var newGame = Game(
        id = newId("g"),
        sessionId = sessionId,
        mode = finalLineup.mode,
        teams = when (finalLineup) {
          is Lineup.Fixed -> finalLineup.teams
          is Lineup.ThreeTeams -> finalLineup.teams
          is Lineup.Rotation -> emptyList()
        },
        fixedTeam = if (finalLineup is Lineup.Rotation) finalLineup.fixedTeam else emptyList(),
        pool = if (finalLineup is Lineup.Rotation) finalLineup.pool else emptyList(),
        firstRestingTeam = firstRest,
        winThreshold = prefs.winThreshold,
        startedAt = System.currentTimeMillis(),
      )

      val dealer = DealerSelector.pickDealerForRound(
        game = newGame,
        lineup = LineupGenerator.getRoundLineup(newGame, 1),
        prevDealerId = DealerSelector.lastRecordedDealer(sessionGames),
        counts = DealerSelector.sessionDealCounts(session, sessionGames),
        history = DealerSelector.sessionDealerHistory(sessionGames),
      )
      newGame = newGame.copy(nextDealerPlayerId = dealer)
      gameDao.upsert(GameEntity.fromDomain(newGame))
      newGame
    }
    preferencesStore.setCurrentSessionGameId(createdGame.id)
    return createdGame
  }

  suspend fun finishSessionGame(
    gameId: String,
    winner: String,
    finalScores: FinalScores,
    clearCurrentIfNotThreeTeam: Boolean = true,
  ): Game {
    val updated = database.withTransaction {
      val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
      val isThreeTeam = game.mode == GameMode.THREE_TEAMS
      val next = game.copy(
        winner = winner.ifBlank { "tie" },
        finalScores = finalScores,
        endedAt = System.currentTimeMillis(),
        pendingNextGame = isThreeTeam,
      )
      gameDao.upsert(GameEntity.fromDomain(next))
      next
    }
    if (clearCurrentIfNotThreeTeam && !updated.pendingNextGame &&
      preferencesStore.getSnapshot().currentSessionGameId == gameId
    ) {
      preferencesStore.setCurrentSessionGameId(null)
    }
    return updated
  }

  suspend fun deleteGame(gameId: String) {
    val currentSessionGameId = preferencesStore.getSnapshot().currentSessionGameId
    gameDao.deleteById(gameId)
    if (currentSessionGameId == gameId) {
      preferencesStore.setCurrentSessionGameId(null)
    }
  }

  suspend fun deleteSession(sessionId: String) {
    val currentSessionGameId = preferencesStore.getSnapshot().currentSessionGameId
    val sessionGames = gameDao.getAllGamesWithRounds().filter { it.game.sessionId == sessionId }
    val clearsCurrent = sessionGames.any { it.game.id == currentSessionGameId }
    database.withTransaction {
      gameDao.deleteBySessionId(sessionId)
      sessionDao.deleteById(sessionId)
    }
    if (clearsCurrent) {
      preferencesStore.setCurrentSessionGameId(null)
    }
  }

  // ---- Scoring (Quick Game + Session Game unified in Room) ----

  suspend fun ensureQuickGame(): Game {
    val prefs = preferencesStore.getSnapshot()
    val existingId = prefs.quickGameId
    if (existingId != null) {
      val found = gameDao.getGameWithRounds(existingId)?.toDomain()
      if (found != null && found.sessionId == null) return found
    }
    val fallback = gameDao.getAllGamesWithRounds()
      .map { it.toDomain() }
      .lastOrNull { it.sessionId == null && it.mode == GameMode.QUICK }
    if (fallback != null) {
      preferencesStore.setQuickGameId(fallback.id)
      return fallback
    }
    val created = Game(
      id = newId("q"),
      sessionId = null,
      mode = GameMode.QUICK,
      winThreshold = prefs.winThreshold,
      teamAName = prefs.quickTeamAName,
      teamBName = prefs.quickTeamBName,
      startedAt = System.currentTimeMillis(),
    )
    gameDao.upsert(GameEntity.fromDomain(created))
    preferencesStore.setQuickGameId(created.id)
    return created
  }

  suspend fun updateQuickTeamName(isTeamA: Boolean, name: String) {
    preferencesStore.setQuickTeamName(isTeamA, name)
    val quick = ensureQuickGame()
    val updated = if (isTeamA) quick.copy(teamAName = name) else quick.copy(teamBName = name)
    gameDao.upsert(GameEntity.fromDomain(updated))
  }

  suspend fun recordRound(gameId: String, draft: ScoreboardDraft, threshold: Int): Game {
    return database.withTransaction {
      val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
      val nextRoundNumber = game.rounds.size + 1

      val roundRecord: RoundRecord
      var updatedGame = game

      if (game.mode == GameMode.THREE_TEAMS) {
        val lineup = LineupGenerator.getRoundLineup(game, nextRoundNumber)
        val prevScores = game.rounds.lastOrNull()?.scores ?: listOf(0, 0, 0)
        val nextScores = ScoringEngine.applyRoundToThreeTeamTotals(prevScores, lineup.activeIndices, draft)
        val mods = MutableList(3) { 0 }
        val doubles = MutableList(3) { false }
        if (lineup.activeIndices.size >= 2) {
          mods[lineup.activeIndices[0]] = draft.teamA.tichuCall.mod
          mods[lineup.activeIndices[1]] = draft.teamB.tichuCall.mod
          doubles[lineup.activeIndices[0]] = draft.teamA.doubleWin
          doubles[lineup.activeIndices[1]] = draft.teamB.doubleWin
        }
        roundRecord = RoundRecord(
          gameId = game.id,
          roundNumber = nextRoundNumber,
          scores = nextScores,
          activeTeams = lineup.activeIndices,
          tichuMods = mods,
          doubleWins = doubles,
          dealerPlayerId = game.nextDealerPlayerId,
        )
        roundDao.insert(RoundEntity.fromDomain(roundRecord))
        val winner = ScoringEngine.evaluateThreeTeamWinner(nextScores, threshold)
        if (winner != null) {
          updatedGame = updatedGame.copy(
            winner = winner,
            pendingNextGame = true,
            endedAt = System.currentTimeMillis(),
            finalScores = FinalScores(nextScores[0], nextScores[1], nextScores[2]),
          )
        }
      } else {
        val prevScores = game.rounds.lastOrNull()?.scores ?: listOf(0, 0)
        val nextScores = ScoringEngine.applyRoundToTwoTeamTotals(prevScores, draft)
        val sittingOut = if (game.sessionId != null) {
          LineupGenerator.getRoundLineup(game, nextRoundNumber).sittingOutPlayerId
        } else {
          null
        }
        roundRecord = RoundRecord(
          gameId = game.id,
          roundNumber = nextRoundNumber,
          scores = nextScores,
          activeTeams = listOf(0, 1),
          tichuMods = listOf(draft.teamA.tichuCall.mod, draft.teamB.tichuCall.mod),
          doubleWins = listOf(draft.teamA.doubleWin, draft.teamB.doubleWin),
          sittingOutPlayerId = sittingOut,
          dealerPlayerId = if (game.sessionId != null) game.nextDealerPlayerId else null,
        )
        roundDao.insert(RoundEntity.fromDomain(roundRecord))
      }

      if (game.sessionId != null) {
        val session = sessionDao.getById(game.sessionId)?.toDomain()
        if (session != null) {
          val refreshedGames = gameDao.getAllGamesWithRounds()
            .filter { it.game.sessionId == session.id }
            .map { it.toDomain() }
          val counts = DealerSelector.sessionDealCounts(session, refreshedGames)
          val history = DealerSelector.sessionDealerHistory(refreshedGames)
          val upcomingDealer = DealerSelector.pickDealerForRound(
            game = updatedGame,
            lineup = LineupGenerator.getRoundLineup(updatedGame, nextRoundNumber + 1),
            prevDealerId = roundRecord.dealerPlayerId,
            counts = counts,
            history = history,
          )
          updatedGame = updatedGame.copy(nextDealerPlayerId = upcomingDealer)
        }
      }

      gameDao.upsert(GameEntity.fromDomain(updatedGame))
      gameDao.getGameWithRounds(game.id)!!.toDomain()
    }
  }

  suspend fun undoLastRound(gameId: String): Game {
    return database.withTransaction {
      val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
      val popped = game.rounds.lastOrNull() ?: return@withTransaction game
      roundDao.deleteLastRound(gameId)

      var updated = game
      if (game.mode == GameMode.THREE_TEAMS && game.winner != null) {
        updated = updated.copy(
          winner = null,
          finalScores = null,
          endedAt = null,
          pendingNextGame = false,
        )
      }
      if (popped.dealerPlayerId != null) {
        updated = updated.copy(nextDealerPlayerId = popped.dealerPlayerId)
      }
      gameDao.upsert(GameEntity.fromDomain(updated))
      gameDao.getGameWithRounds(gameId)!!.toDomain()
    }
  }

  suspend fun resetGameRounds(gameId: String, firstDealerId: String? = null): Game {
    return database.withTransaction {
      val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
      roundDao.deleteRoundsForGame(gameId)
      val dealer = firstDealerId ?: game.rounds.firstOrNull()?.dealerPlayerId ?: game.nextDealerPlayerId
      val updated = game.copy(
        winner = null,
        finalScores = null,
        endedAt = null,
        pendingNextGame = false,
        nextDealerPlayerId = dealer,
      )
      gameDao.upsert(GameEntity.fromDomain(updated))
      gameDao.getGameWithRounds(gameId)!!.toDomain()
    }
  }

  suspend fun restoreGameSnapshot(gameId: String, rounds: List<RoundRecord>, nextDealerPlayerId: String?): Game {
    return database.withTransaction {
      val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
      roundDao.deleteRoundsForGame(gameId)
      roundDao.insertAll(rounds.map { RoundEntity.fromDomain(it.copy(id = 0, gameId = gameId)) })
      val updated = game.copy(
        winner = null,
        finalScores = null,
        endedAt = null,
        pendingNextGame = false,
        nextDealerPlayerId = nextDealerPlayerId,
      )
      gameDao.upsert(GameEntity.fromDomain(updated))
      gameDao.getGameWithRounds(gameId)!!.toDomain()
    }
  }

  // ---- Preferences & Settings ----

  suspend fun setThemeMode(mode: ThemeMode) = preferencesStore.setThemeMode(mode)

  suspend fun setChipsVisible(visible: Boolean) = preferencesStore.setChipsVisible(visible)

  suspend fun dismissModeHint() = preferencesStore.setModeHintDismissed(true)

  suspend fun setPlayersRankingSort(metric: RankingMetric) = preferencesStore.setPlayersRankingSort(metric)

  suspend fun setSessionRankingSort(metric: RankingMetric) = preferencesStore.setSessionRankingSort(metric)

  suspend fun setWinThreshold(newThreshold: Int): Boolean {
    require(newThreshold > 0) { "Target must be a positive whole number." }
    preferencesStore.setWinThreshold(newThreshold)
    val prefs = preferencesStore.getSnapshot()
    var threeTeamWinnerTriggered = false
    database.withTransaction {
      val sessionGameId = prefs.currentSessionGameId
      if (sessionGameId != null) {
        val game = gameDao.getGameWithRounds(sessionGameId)?.toDomain()
        if (game != null && game.winner == null) {
          gameDao.upsert(GameEntity.fromDomain(game.copy(winThreshold = newThreshold)))
          if (game.mode == GameMode.THREE_TEAMS && game.rounds.isNotEmpty()) {
            val latest = game.rounds.last().scores
            val winner = ScoringEngine.evaluateThreeTeamWinner(latest, newThreshold)
            if (winner != null) {
              threeTeamWinnerTriggered = true
              gameDao.upsert(
                GameEntity.fromDomain(
                  game.copy(
                    winThreshold = newThreshold,
                    winner = winner,
                    pendingNextGame = true,
                    endedAt = System.currentTimeMillis(),
                    finalScores = FinalScores(latest[0], latest[1], latest[2]),
                  ),
                ),
              )
            }
          }
        }
      }
      val quickId = prefs.quickGameId
      if (quickId != null) {
        val quick = gameDao.getGameWithRounds(quickId)?.toDomain()
        if (quick != null) {
          gameDao.upsert(GameEntity.fromDomain(quick.copy(winThreshold = newThreshold)))
        }
      }
    }
    return threeTeamWinnerTriggered
  }

  // ---- JSON Backup Export / Import ----

  suspend fun exportBackupJson(): String {
    val prefs = preferencesStore.getSnapshot()
    val players = playerDao.getAll().map { it.toDomain() }
    val sessions = sessionDao.getAll().map { it.toDomain() }
    val games = gameDao.getAllGamesWithRounds().map { it.toDomain() }
    return BackupSerializer.encode(
      preferences = prefs,
      players = players,
      sessions = sessions,
      games = games,
    )
  }

  suspend fun importBackupJson(json: String) {
    val decoded = BackupSerializer.decode(json)
    database.withTransaction {
      roundDao.deleteAll()
      gameDao.deleteAll()
      sessionDao.deleteAll()
      playerDao.deleteAll()

      playerDao.upsertAll(decoded.players.map { PlayerEntity.fromDomain(it) })
      sessionDao.upsertAll(decoded.sessions.map { SessionEntity.fromDomain(it) })
      gameDao.upsertAll(decoded.games.map { GameEntity.fromDomain(it) })
      val allRounds = decoded.games.flatMap { game ->
        game.rounds.map { round -> RoundEntity.fromDomain(round.copy(id = 0, gameId = game.id)) }
      }
      if (allRounds.isNotEmpty()) {
        roundDao.insertAll(allRounds)
      }
    }
    preferencesStore.applyBackupPreferences(decoded.preferences)
  }
}

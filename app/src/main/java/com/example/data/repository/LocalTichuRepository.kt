package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.backup.BackupSerializer
import com.example.data.backup.DecodedBackup
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
import com.example.domain.PlayerInvite
import com.example.domain.RankingMetric
import com.example.domain.RoundRecord
import com.example.domain.ScoreboardDraft
import com.example.domain.ScoringEngine
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalTichuRepository(
  private val localDatabase: AppDatabase,
  private val preferencesStore: PreferencesStore,
) : TichuRepository {

  override val isGuestMode: Boolean = true
  override val currentUserId: String = ""
  override val currentUserDisplayName: String = "Guest"
  override val currentUserEmail: String? = null

  private val _streamError = MutableStateFlow<String?>(null)
  override val streamErrorFlow: StateFlow<String?> = _streamError.asStateFlow()
  override fun clearStreamError() {
    _streamError.value = null
  }

  private val playerDao get() = localDatabase.playerDao()
  private val sessionDao get() = localDatabase.sessionDao()
  private val gameDao get() = localDatabase.gameDao()
  private val roundDao get() = localDatabase.roundDao()

  override val preferencesFlow: Flow<AppPreferences> = preferencesStore.preferencesFlow

  override val playersFlow: Flow<List<Player>> = playerDao.observeAll()
    .map { list ->
      list.map { it.toDomain() }
        .sortedWith(compareBy<Player> { it.createdAt }.thenBy { it.name.lowercase() })
    }

  override val sessionsFlow: Flow<List<Session>> = sessionDao.observeAll()
    .map { list -> list.map { it.toDomain() }.sortedBy { it.startedAt } }

  override val gamesFlow: Flow<List<Game>> = gameDao.observeGamesWithRounds()
    .map { list -> list.map { it.toDomain() }.sortedBy { it.startedAt } }

  override val activeOrPausedSessionFlow: Flow<Session?> = sessionsFlow.map { list ->
    list.firstOrNull { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }
  }

  override val currentSessionGameFlow: Flow<Game?> = combine(
    gamesFlow,
    preferencesFlow,
  ) { games, prefs ->
    val id = prefs.currentSessionGameId ?: return@combine null
    val game = games.firstOrNull { it.id == id } ?: return@combine null
    if (game.winner != null && !game.pendingNextGame) null else game
  }

  override val activeQuickGameFlow: Flow<Game?> = combine(
    gamesFlow,
    preferencesFlow,
  ) { games, prefs ->
    val id = prefs.quickGameId
    if (id != null) {
      games.firstOrNull { it.id == id && it.sessionId == null }
        ?: games.lastOrNull { it.sessionId == null && it.mode == GameMode.QUICK }
    } else {
      games.lastOrNull { it.sessionId == null && it.mode == GameMode.QUICK }
    }
  }

  override fun close() = Unit

  private fun newId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

  // ---- Players ----

  override suspend fun addPlayer(rawName: String): Player {
    val name = rawName.trim()
    require(name.isNotEmpty()) { "Player name can't be empty." }
    require(name.length <= 60) { "Player name must be 60 characters or less." }
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

  override suspend fun renamePlayer(id: String, rawName: String) {
    val name = rawName.trim()
    require(name.isNotEmpty()) { "Player name can't be empty." }
    require(name.length <= 60) { "Player name must be 60 characters or less." }
    val existing = playerDao.getById(id) ?: error("Player not found.")
    playerDao.upsert(existing.copy(name = name))
  }

  override suspend fun setPlayerActive(id: String, active: Boolean) {
    val existing = playerDao.getById(id) ?: error("Player not found.")
    playerDao.upsert(existing.copy(active = active))
  }

  override suspend fun resetPlayerStatistics(id: String) {
    val existing = playerDao.getById(id) ?: error("Player not found.")
    playerDao.upsert(existing.copy(statsResetAt = System.currentTimeMillis()))
  }

  override suspend fun deleteArchivedPlayer(id: String) {
    val existing = playerDao.getById(id)?.toDomain() ?: error("Player not found.")
    check(!existing.active) { "Archive this player before deleting them permanently." }
    val activeSessions = sessionDao.getAll().map { it.toDomain() }
      .filter { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }
    check(activeSessions.none { id in it.playerIds }) {
      "End the active session before deleting ${existing.name}."
    }
    playerDao.deleteById(id)
  }

  override suspend fun createPlayerInvite(playerId: String): Result<PlayerInvite> =
    Result.failure(IllegalStateException("Sign in with Google to share invite codes and link players."))

  override suspend fun claimPlayerInvite(rawCode: String): Result<String> =
    Result.failure(IllegalStateException("Sign in with Google to claim a guest player invite code."))

  override suspend fun linkSelfToPlayer(playerId: String): Result<Unit> =
    Result.failure(IllegalStateException("Sign in with Google to link your account to a player."))

  override suspend fun unlinkPlayer(playerId: String): Result<Unit> =
    Result.failure(IllegalStateException("Sign in with Google to manage linked accounts."))

  // ---- Sessions ----

  override suspend fun startSession(playerIds: List<String>, lineup: Lineup): Pair<Session, Game> {
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

  override suspend fun pauseSession(sessionId: String) {
    val session = sessionDao.getById(sessionId)?.toDomain() ?: error("Session not found.")
    check(session.status == SessionStatus.ACTIVE) { "Only an active session can be paused." }
    val updatedLog = session.pauseLog + PauseInterval(pausedAt = System.currentTimeMillis(), resumedAt = null)
    sessionDao.upsert(
      SessionEntity.fromDomain(session.copy(status = SessionStatus.PAUSED, pauseLog = updatedLog)),
    )
  }

  override suspend fun resumeSession(sessionId: String) {
    val session = sessionDao.getById(sessionId)?.toDomain() ?: error("Session not found.")
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
    sessionDao.upsert(
      SessionEntity.fromDomain(session.copy(status = SessionStatus.ACTIVE, pauseLog = updatedLog)),
    )
  }

  override suspend fun endSession(sessionId: String) {
    val session = sessionDao.getById(sessionId)?.toDomain() ?: error("Session not found.")
    val currentId = preferencesStore.getSnapshot().currentSessionGameId
    localDatabase.withTransaction {
      if (currentId != null) {
        val currentGame = gameDao.getGameWithRounds(currentId)?.toDomain()
        if (currentGame != null && currentGame.sessionId == sessionId && currentGame.winner == null) {
          gameDao.deleteById(currentGame.id)
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

  override suspend fun startNewGameInSession(sessionId: String, requestedLineup: Lineup?): Game {
    val prefs = preferencesStore.getSnapshot()
    var session = sessionDao.getById(sessionId)?.toDomain() ?: error("Session not found.")

    if (session.status == SessionStatus.PAUSED) {
      val now = System.currentTimeMillis()
      val resumedLog = session.pauseLog.map { if (it.resumedAt == null) it.copy(resumedAt = now) else it }
      session = session.copy(status = SessionStatus.ACTIVE, pauseLog = resumedLog)
      sessionDao.upsert(SessionEntity.fromDomain(session))
    }
    check(session.status == SessionStatus.ACTIVE) { "This session cannot start a new game." }

    val sessionGames = gameDao.getAllGamesWithRounds()
      .map { it.toDomain() }
      .filter { it.sessionId == sessionId }
    val current = prefs.currentSessionGameId?.let { id -> sessionGames.firstOrNull { it.id == id } }
    if (current != null && !current.pendingNextGame && current.winner == null) {
      error("A game is already in progress for this session.")
    }

    var finalLineup = requestedLineup ?: LineupGenerator.previewLineupForSession(session, sessionGames)
    require(LineupGenerator.isValidLineupFor(session.playerIds, finalLineup)) {
      "That lineup doesn't match this session's players."
    }

    if (finalLineup is Lineup.Rotation) {
      finalLineup = LineupGenerator.finalizeRotationLineupForStart(finalLineup, session.nextPoolStart)
      val nextPool = ((session.nextPoolStart % 3) + 3 + 1) % 3
      session = session.copy(nextPoolStart = nextPool)
      sessionDao.upsert(SessionEntity.fromDomain(session))
    }

    val firstRest = if (finalLineup is Lineup.ThreeTeams) {
      LineupGenerator.selectFirstRestingTeam(finalLineup.teams, session.playerIds, sessionGames)
    } else {
      null
    }

    val gameId = newId("g")
    var newGame = Game(
      id = gameId,
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
    preferencesStore.setCurrentSessionGameId(gameId)
    return newGame
  }

  override suspend fun finishSessionGame(
    gameId: String,
    winner: String,
    finalScores: FinalScores,
    clearCurrentIfNotThreeTeam: Boolean,
  ): Game {
    val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
    val isThreeTeam = game.mode == GameMode.THREE_TEAMS
    val next = game.copy(
      winner = winner.ifBlank { "tie" },
      finalScores = finalScores,
      endedAt = System.currentTimeMillis(),
      pendingNextGame = isThreeTeam,
    )
    gameDao.upsert(GameEntity.fromDomain(next))
    val currentId = preferencesStore.getSnapshot().currentSessionGameId
    if (clearCurrentIfNotThreeTeam && !next.pendingNextGame && currentId == gameId) {
      preferencesStore.setCurrentSessionGameId(null)
    }
    return next
  }

  override suspend fun deleteGame(gameId: String) {
    val currentId = preferencesStore.getSnapshot().currentSessionGameId
    gameDao.deleteById(gameId)
    if (currentId == gameId) {
      preferencesStore.setCurrentSessionGameId(null)
    }
  }

  override suspend fun deleteSession(sessionId: String) {
    val currentId = preferencesStore.getSnapshot().currentSessionGameId
    val sessionGames = gameDao.getAllGamesWithRounds().filter { it.game.sessionId == sessionId }
    val clearsCurrent = sessionGames.any { it.game.id == currentId }
    sessionDao.deleteById(sessionId)
    if (clearsCurrent) {
      preferencesStore.setCurrentSessionGameId(null)
    }
  }

  // ---- Scoring ----

  override suspend fun ensureQuickGame(): Game {
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
    val gameId = newId("q")
    val created = Game(
      id = gameId,
      sessionId = null,
      mode = GameMode.QUICK,
      winThreshold = prefs.winThreshold,
      teamAName = prefs.quickTeamAName.ifBlank { "Team A" }.take(60),
      teamBName = prefs.quickTeamBName.ifBlank { "Team B" }.take(60),
      startedAt = System.currentTimeMillis(),
    )
    gameDao.upsert(GameEntity.fromDomain(created))
    preferencesStore.setQuickGameId(gameId)
    return created
  }

  override suspend fun updateQuickTeamName(isTeamA: Boolean, rawName: String) {
    val trimmed = rawName.trim().take(60)
    if (trimmed.isEmpty()) return
    preferencesStore.setQuickTeamName(isTeamA, trimmed)
    val quick = ensureQuickGame()
    val updated = if (isTeamA) quick.copy(teamAName = trimmed) else quick.copy(teamBName = trimmed)
    gameDao.upsert(GameEntity.fromDomain(updated))
  }

  override suspend fun recordRound(gameId: String, draft: ScoreboardDraft, threshold: Int): Game {
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
      val newRounds = game.rounds + roundRecord
      val winner = ScoringEngine.evaluateThreeTeamWinner(nextScores, threshold)
      updatedGame = if (winner != null) {
        updatedGame.copy(
          rounds = newRounds,
          winner = winner,
          pendingNextGame = true,
          endedAt = System.currentTimeMillis(),
          finalScores = FinalScores(nextScores[0], nextScores[1], nextScores[2]),
        )
      } else {
        updatedGame.copy(rounds = newRounds)
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
      updatedGame = updatedGame.copy(rounds = game.rounds + roundRecord)
    }

    if (game.sessionId != null) {
      val session = sessionDao.getById(game.sessionId)?.toDomain()
      if (session != null) {
        val sessionGames = gameDao.getAllGamesWithRounds()
          .map { it.toDomain() }
          .filter { it.sessionId == session.id }
          .map { if (it.id == updatedGame.id) updatedGame else it }
        val counts = DealerSelector.sessionDealCounts(session, sessionGames)
        val history = DealerSelector.sessionDealerHistory(sessionGames)
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

    localDatabase.withTransaction {
      roundDao.insert(RoundEntity.fromDomain(roundRecord))
      gameDao.upsert(GameEntity.fromDomain(updatedGame))
    }
    return gameDao.getGameWithRounds(game.id)?.toDomain() ?: updatedGame
  }

  override suspend fun undoLastRound(gameId: String): Game {
    val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
    val popped = game.rounds.lastOrNull() ?: return game
    var updated = game.copy(rounds = game.rounds.dropLast(1))
    if (game.mode == GameMode.THREE_TEAMS && game.winner != null) {
      updated = updated.copy(winner = null, finalScores = null, endedAt = null, pendingNextGame = false)
    }
    if (popped.dealerPlayerId != null) {
      updated = updated.copy(nextDealerPlayerId = popped.dealerPlayerId)
    }
    localDatabase.withTransaction {
      roundDao.deleteLastRound(gameId)
      gameDao.upsert(GameEntity.fromDomain(updated))
    }
    return gameDao.getGameWithRounds(gameId)?.toDomain() ?: updated
  }

  override suspend fun resetGameRounds(gameId: String, firstDealerId: String?): Game {
    val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
    val dealer = firstDealerId ?: game.rounds.firstOrNull()?.dealerPlayerId ?: game.nextDealerPlayerId
    val updated = game.copy(
      rounds = emptyList(),
      winner = null,
      finalScores = null,
      endedAt = null,
      pendingNextGame = false,
      nextDealerPlayerId = dealer,
    )
    localDatabase.withTransaction {
      roundDao.deleteRoundsForGame(gameId)
      gameDao.upsert(GameEntity.fromDomain(updated))
    }
    return updated
  }

  override suspend fun restoreGameSnapshot(
    gameId: String,
    rounds: List<RoundRecord>,
    nextDealerPlayerId: String?,
  ): Game {
    val game = gameDao.getGameWithRounds(gameId)?.toDomain() ?: error("Game not found.")
    val restored = rounds.map { it.copy(id = 0L, gameId = gameId) }
    val updated = game.copy(
      rounds = restored,
      winner = null,
      finalScores = null,
      endedAt = null,
      pendingNextGame = false,
      nextDealerPlayerId = nextDealerPlayerId,
    )
    localDatabase.withTransaction {
      roundDao.deleteRoundsForGame(gameId)
      roundDao.insertAll(restored.map { RoundEntity.fromDomain(it) })
      gameDao.upsert(GameEntity.fromDomain(updated))
    }
    return gameDao.getGameWithRounds(gameId)?.toDomain() ?: updated
  }

  // ---- Preferences & Settings ----

  override suspend fun setThemeMode(mode: ThemeMode) {
    preferencesStore.setThemeMode(mode)
  }

  override suspend fun setChipsVisible(visible: Boolean) {
    preferencesStore.setChipsVisible(visible)
  }

  override suspend fun dismissModeHint() {
    preferencesStore.setModeHintDismissed(true)
  }

  override suspend fun setPlayersRankingSort(metric: RankingMetric) {
    preferencesStore.setPlayersRankingSort(metric)
  }

  override suspend fun setSessionRankingSort(metric: RankingMetric) {
    preferencesStore.setSessionRankingSort(metric)
  }

  override suspend fun setWinThreshold(newThreshold: Int): Boolean {
    require(newThreshold in 1..100000) { "Target must be between 1 and 100,000 points." }
    val prefs = preferencesStore.getSnapshot()
    preferencesStore.setWinThreshold(newThreshold)
    var threeTeamWinnerTriggered = false

    val sessionGameId = prefs.currentSessionGameId
    if (sessionGameId != null) {
      val game = gameDao.getGameWithRounds(sessionGameId)?.toDomain()
      if (game != null && game.winner == null) {
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
          } else {
            gameDao.upsert(GameEntity.fromDomain(game.copy(winThreshold = newThreshold)))
          }
        } else {
          gameDao.upsert(GameEntity.fromDomain(game.copy(winThreshold = newThreshold)))
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
    return threeTeamWinnerTriggered
  }

  // ---- Backup Export / Import ----

  override suspend fun exportBackupJson(): String {
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

  override suspend fun importBackupJson(json: String) {
    importDecodedBackup(BackupSerializer.decodeWithPreview(json).decoded)
  }

  override suspend fun importDecodedBackup(decoded: DecodedBackup) {
    localDatabase.withTransaction {
      roundDao.deleteAll()
      gameDao.deleteAll()
      sessionDao.deleteAll()
      playerDao.deleteAll()

      playerDao.upsertAll(decoded.players.map { PlayerEntity.fromDomain(it) })
      sessionDao.upsertAll(decoded.sessions.map { SessionEntity.fromDomain(it) })
      gameDao.upsertAll(decoded.games.map { GameEntity.fromDomain(it) })

      val allRounds = decoded.games.flatMap { game ->
        game.rounds.map { r -> RoundEntity.fromDomain(r.copy(id = 0L, gameId = game.id)) }
      }
      if (allRounds.isNotEmpty()) {
        roundDao.insertAll(allRounds)
      }
    }
    preferencesStore.applyBackupPreferences(decoded.preferences)
    if (decoded.preferences.quickGameId == null) {
      ensureQuickGame()
    }
  }
}

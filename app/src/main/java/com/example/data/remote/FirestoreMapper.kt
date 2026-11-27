package com.example.data.remote

import com.example.data.preferences.AppPreferences
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
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

object FirestoreMapper {

  private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

  fun isValidDocId(id: String): Boolean =
    id.length in 1..128 && VALID_ID_REGEX.matches(id)

  fun sanitizeDocId(raw: String, fallbackPrefix: String = "id"): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(110)
    return if (cleaned.isNotEmpty()) cleaned else "${fallbackPrefix}_item"
  }

  fun participantUidsFor(
    ownerUid: String,
    playerIds: List<String>,
    playersById: Map<String, Player>,
  ): List<String> = (listOf(ownerUid) + playerIds.mapNotNull { playersById[it]?.linkedUid })
    .filter { it.isNotBlank() }
    .distinct()
    .take(10)

  fun gamePlayerIds(game: Game): List<String> =
    (game.teams.flatten() + game.fixedTeam + game.pool)
      .filter { it.isNotBlank() }
      .distinct()
      .take(6)

  // ---- UserSettings ----

  fun userSettingsToCreateMap(
    uid: String,
    displayName: String,
    prefs: AppPreferences,
    localMigratedAt: Long? = null,
  ): Map<String, Any> = buildMap {
    put("uid", uid)
    put("displayName", displayName.trim().take(80))
    put("themeMode", prefs.themeMode.key)
    put("winThreshold", prefs.winThreshold.coerceIn(1, 100000))
    put("chipsVisible", prefs.chipsVisible)
    put("modeHintDismissed", prefs.modeHintDismissed)
    put("playersRankingSort", prefs.playersRankingSort.id.take(40))
    put("sessionRankingSort", prefs.sessionRankingSort.id.take(40))
    prefs.currentSessionGameId?.takeIf { isValidDocId(it) }?.let { put("currentSessionGameId", it) }
    prefs.quickGameId?.takeIf { isValidDocId(it) }?.let { put("quickGameId", it) }
    put("quickTeamAName", prefs.quickTeamAName.trim().ifBlank { "Team A" }.take(60))
    put("quickTeamBName", prefs.quickTeamBName.trim().ifBlank { "Team B" }.take(60))
    if (localMigratedAt != null && localMigratedAt >= 0L) {
      put("localMigratedAt", localMigratedAt)
    }
    put("createdAt", FieldValue.serverTimestamp())
    put("updatedAt", FieldValue.serverTimestamp())
  }

  fun preferencesFromSnapshot(
    doc: DocumentSnapshot,
    fallback: AppPreferences = AppPreferences(),
  ): AppPreferences {
    val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return fallback
    return preferencesFromMap(data, fallback)
  }

  fun preferencesFromMap(
    data: Map<String, Any?>,
    fallback: AppPreferences = AppPreferences(),
  ): AppPreferences = AppPreferences(
    themeMode = ThemeMode.fromKey(data["themeMode"] as? String ?: fallback.themeMode.key),
    winThreshold = (data["winThreshold"] as? Number)?.toInt()?.coerceIn(1, 100000)
      ?: fallback.winThreshold,
    chipsVisible = data["chipsVisible"] as? Boolean ?: fallback.chipsVisible,
    modeHintDismissed = data["modeHintDismissed"] as? Boolean ?: fallback.modeHintDismissed,
    playersRankingSort = RankingMetric.fromId(
      data["playersRankingSort"] as? String ?: fallback.playersRankingSort.id,
    ),
    sessionRankingSort = RankingMetric.fromId(
      data["sessionRankingSort"] as? String ?: fallback.sessionRankingSort.id,
    ),
    currentSessionGameId = (data["currentSessionGameId"] as? String)?.takeIf { it.isNotBlank() },
    quickGameId = (data["quickGameId"] as? String)?.takeIf { it.isNotBlank() },
    quickTeamAName = (data["quickTeamAName"] as? String)?.takeIf { it.isNotBlank() }
      ?: fallback.quickTeamAName,
    quickTeamBName = (data["quickTeamBName"] as? String)?.takeIf { it.isNotBlank() }
      ?: fallback.quickTeamBName,
  )

  // ---- Player ----

  fun playerToCreateMap(player: Player, ownerUid: String): Map<String, Any> {
    val memberUids = listOfNotNull(ownerUid, player.linkedUid)
      .filter { it.isNotBlank() }
      .distinct()
      .take(2)
    return buildMap {
      put("ownerUid", ownerUid)
      put("memberUids", memberUids)
      put("name", player.name.trim().ifBlank { "Player" }.take(60))
      put("active", player.active)
      put("createdAtMs", player.createdAt.coerceAtLeast(0L))
      player.linkedUid?.takeIf { isValidDocId(it) }?.let { put("linkedUid", it) }
      player.linkedLabel?.takeIf { it.isNotBlank() }?.let { put("linkedLabel", it.take(80)) }
      player.statsResetAt?.takeIf { it >= 0L }?.let { put("statsResetAt", it) }
      put("createdAt", FieldValue.serverTimestamp())
      put("updatedAt", FieldValue.serverTimestamp())
    }
  }

  fun playerFromSnapshot(doc: DocumentSnapshot): Player? {
    val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return playerFromMap(doc.id, data)
  }

  fun playerFromMap(id: String, data: Map<String, Any?>): Player = Player(
    id = id,
    name = (data["name"] as? String)?.ifBlank { "Player" } ?: "Player",
    active = data["active"] as? Boolean ?: true,
    createdAt = (data["createdAtMs"] as? Number)?.toLong() ?: 0L,
    statsResetAt = (data["statsResetAt"] as? Number)?.toLong(),
    ownerUid = data["ownerUid"] as? String ?: "",
    linkedUid = (data["linkedUid"] as? String)?.takeIf { it.isNotBlank() },
    linkedLabel = (data["linkedLabel"] as? String)?.takeIf { it.isNotBlank() },
  )

  // ---- PlayerInvite ----

  fun playerInviteToCreateMap(invite: PlayerInvite): Map<String, Any> = buildMap {
    put("ownerUid", invite.ownerUid)
    put("playerId", invite.playerId)
    put("playerName", invite.playerName.trim().ifBlank { "Player" }.take(60))
    put("sessionIds", invite.sessionIds.filter { it.isNotBlank() }.distinct().take(500))
    put("gameIds", invite.gameIds.filter { it.isNotBlank() }.distinct().take(1000))
    put("status", invite.status)
    put("expiresAtMs", invite.expiresAtMs.coerceAtLeast(1L))
    invite.claimedByUid?.takeIf { isValidDocId(it) }?.let { put("claimedByUid", it) }
    put("createdAt", FieldValue.serverTimestamp())
    put("updatedAt", FieldValue.serverTimestamp())
  }

  fun playerInviteFromSnapshot(doc: DocumentSnapshot): PlayerInvite? {
    val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    val ownerUid = data["ownerUid"] as? String ?: return null
    val playerId = data["playerId"] as? String ?: return null
    return PlayerInvite(
      code = doc.id,
      ownerUid = ownerUid,
      playerId = playerId,
      playerName = (data["playerName"] as? String)?.ifBlank { "Player" } ?: "Player",
      sessionIds = asStringList(data["sessionIds"]),
      gameIds = asStringList(data["gameIds"]),
      status = data["status"] as? String ?: "pending",
      expiresAtMs = (data["expiresAtMs"] as? Number)?.toLong() ?: 0L,
      claimedByUid = (data["claimedByUid"] as? String)?.takeIf { it.isNotBlank() },
    )
  }

  // ---- Session ----

  fun sessionToCreateMap(
    session: Session,
    ownerUid: String,
    participantUids: List<String>,
  ): Map<String, Any> = buildMap {
    val cleanParticipants = (listOf(ownerUid) + participantUids).filter { it.isNotBlank() }.distinct().take(10)
    put("ownerUid", ownerUid)
    put("participantUids", cleanParticipants)
    put("status", session.status.key)
    put("playerIds", session.playerIds.filter { it.isNotBlank() }.take(6))
    put("nextPoolStart", session.nextPoolStart.coerceIn(0, 2))
    put("pauseLog", pauseLogToMaps(session.pauseLog))
    put("startedAtMs", session.startedAt.coerceAtLeast(0L))
    session.endedAt?.takeIf { it >= 0L }?.let { put("endedAtMs", it) }
    put("createdAt", FieldValue.serverTimestamp())
    put("updatedAt", FieldValue.serverTimestamp())
  }

  fun pauseLogToMaps(pauseLog: List<PauseInterval>): List<Map<String, Any>> =
    pauseLog.take(500).map { entry ->
      buildMap {
        put("pausedAt", entry.pausedAt.coerceAtLeast(0L))
        entry.resumedAt?.takeIf { it >= 0L }?.let { put("resumedAt", it) }
      }
    }

  fun sessionFromSnapshot(doc: DocumentSnapshot): Session? {
    val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return sessionFromMap(doc.id, data)
  }

  fun sessionFromMap(id: String, data: Map<String, Any?>): Session {
    val pauseLog = (data["pauseLog"] as? List<*>)?.mapNotNull { raw ->
      val m = raw as? Map<*, *> ?: return@mapNotNull null
      val pausedAt = (m["pausedAt"] as? Number)?.toLong() ?: return@mapNotNull null
      val resumedAt = (m["resumedAt"] as? Number)?.toLong()
      PauseInterval(pausedAt = pausedAt, resumedAt = resumedAt)
    } ?: emptyList()

    return Session(
      id = id,
      status = SessionStatus.fromKey(data["status"] as? String),
      playerIds = asStringList(data["playerIds"]),
      nextPoolStart = (data["nextPoolStart"] as? Number)?.toInt()?.coerceIn(0, 2) ?: 0,
      pauseLog = pauseLog,
      startedAt = (data["startedAtMs"] as? Number)?.toLong() ?: 0L,
      endedAt = (data["endedAtMs"] as? Number)?.toLong(),
      ownerUid = data["ownerUid"] as? String ?: "",
      participantUids = asStringList(data["participantUids"]),
    )
  }

  // ---- Game ----

  fun gameToCreateMap(
    game: Game,
    ownerUid: String,
    participantUids: List<String>,
  ): Map<String, Any> {
    val cleanParticipants = (listOf(ownerUid) + participantUids).filter { it.isNotBlank() }.distinct().take(10)
    return buildMap {
      put("ownerUid", ownerUid)
      put("participantUids", cleanParticipants)
      game.sessionId?.takeIf { isValidDocId(it) }?.let { put("sessionId", it) }
      put("mode", game.mode.key)
      put("playerIds", gamePlayerIds(game))
      put("team0", game.teams.getOrElse(0) { emptyList() }.take(2))
      put("team1", game.teams.getOrElse(1) { emptyList() }.take(2))
      put("team2", game.teams.getOrElse(2) { emptyList() }.take(2))
      put("fixedTeam", game.fixedTeam.take(2))
      put("pool", game.pool.take(3))
      game.firstRestingTeam?.let { put("firstRestingTeam", it.coerceIn(0, 2)) }
      put("winThreshold", game.winThreshold.coerceIn(1, 100000))
      put("teamAName", game.teamAName.trim().ifBlank { "Team A" }.take(60))
      put("teamBName", game.teamBName.trim().ifBlank { "Team B" }.take(60))
      game.winner?.takeIf { it in listOf("A", "B", "C", "tie") }?.let { put("winner", it) }
      put("pendingNextGame", game.pendingNextGame)
      game.finalScores?.let { put("finalScores", finalScoresToMap(it)) }
      game.nextDealerPlayerId?.takeIf { isValidDocId(it) }?.let { put("nextDealerPlayerId", it) }
      put("startedAtMs", game.startedAt.coerceAtLeast(0L))
      game.endedAt?.takeIf { it >= 0L }?.let { put("endedAtMs", it) }
      put("rounds", roundsToMaps(game.rounds))
      put("createdAt", FieldValue.serverTimestamp())
      put("updatedAt", FieldValue.serverTimestamp())
    }
  }

  fun finalScoresToMap(scores: FinalScores): Map<String, Any> = buildMap {
    put("teamA", scores.teamA)
    put("teamB", scores.teamB)
    scores.teamC?.let { put("teamC", it) }
  }

  fun roundsToMaps(rounds: List<RoundRecord>): List<Map<String, Any>> =
    rounds.sortedBy { it.roundNumber }.take(500).map { r ->
      buildMap {
        put("roundNumber", r.roundNumber)
        put("scores", r.scores)
        put("activeTeams", r.activeTeams)
        put("tichuMods", r.tichuMods)
        put("doubleWins", r.doubleWins)
        r.sittingOutPlayerId?.takeIf { it.isNotBlank() }?.let { put("sittingOutPlayerId", it) }
        r.dealerPlayerId?.takeIf { it.isNotBlank() }?.let { put("dealerPlayerId", it) }
      }
    }

  fun gameFromSnapshot(doc: DocumentSnapshot): Game? {
    val data = doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return null
    return gameFromMap(doc.id, data)
  }

  fun gameFromMap(id: String, data: Map<String, Any?>): Game {
    val mode = GameMode.fromKey(data["mode"] as? String)
    val team0 = asStringList(data["team0"])
    val team1 = asStringList(data["team1"])
    val team2 = asStringList(data["team2"])
    val teams = when (mode) {
      GameMode.THREE_TEAMS -> listOf(team0, team1, team2).filter { it.isNotEmpty() }
      GameMode.FIXED -> listOf(team0, team1).filter { it.isNotEmpty() }
      GameMode.ROTATION, GameMode.QUICK -> emptyList()
    }

    val fsMap = data["finalScores"] as? Map<*, *>
    val finalScores = fsMap?.let { m ->
      FinalScores(
        teamA = (m["teamA"] as? Number)?.toInt() ?: 0,
        teamB = (m["teamB"] as? Number)?.toInt() ?: 0,
        teamC = (m["teamC"] as? Number)?.toInt(),
      )
    }

    val rounds = (data["rounds"] as? List<*>)?.mapIndexedNotNull { idx, raw ->
      val m = raw as? Map<*, *> ?: return@mapIndexedNotNull null
      RoundRecord(
        id = (idx + 1).toLong(),
        gameId = id,
        roundNumber = (m["roundNumber"] as? Number)?.toInt() ?: (idx + 1),
        scores = asIntList(m["scores"]),
        activeTeams = asIntList(m["activeTeams"]).ifEmpty { listOf(0, 1) },
        tichuMods = asIntList(m["tichuMods"]),
        doubleWins = asBooleanList(m["doubleWins"]),
        sittingOutPlayerId = (m["sittingOutPlayerId"] as? String)?.takeIf { it.isNotBlank() },
        dealerPlayerId = (m["dealerPlayerId"] as? String)?.takeIf { it.isNotBlank() },
      )
    }?.sortedBy { it.roundNumber } ?: emptyList()

    return Game(
      id = id,
      sessionId = (data["sessionId"] as? String)?.takeIf { it.isNotBlank() },
      mode = mode,
      teams = teams,
      fixedTeam = asStringList(data["fixedTeam"]),
      pool = asStringList(data["pool"]),
      firstRestingTeam = (data["firstRestingTeam"] as? Number)?.toInt(),
      winThreshold = (data["winThreshold"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 500,
      teamAName = (data["teamAName"] as? String)?.ifBlank { "Team A" } ?: "Team A",
      teamBName = (data["teamBName"] as? String)?.ifBlank { "Team B" } ?: "Team B",
      winner = (data["winner"] as? String)?.takeIf { it.isNotBlank() },
      pendingNextGame = data["pendingNextGame"] as? Boolean ?: false,
      finalScores = finalScores,
      nextDealerPlayerId = (data["nextDealerPlayerId"] as? String)?.takeIf { it.isNotBlank() },
      startedAt = (data["startedAtMs"] as? Number)?.toLong() ?: 0L,
      endedAt = (data["endedAtMs"] as? Number)?.toLong(),
      rounds = rounds,
      ownerUid = data["ownerUid"] as? String ?: "",
      participantUids = asStringList(data["participantUids"]),
    )
  }

  private fun asStringList(raw: Any?): List<String> =
    (raw as? List<*>)?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } } ?: emptyList()

  private fun asIntList(raw: Any?): List<Int> =
    (raw as? List<*>)?.map { (it as? Number)?.toInt() ?: 0 } ?: emptyList()

  private fun asBooleanList(raw: Any?): List<Boolean> =
    (raw as? List<*>)?.map { it as? Boolean ?: false } ?: emptyList()
}

package com.example.data.backup

import com.example.data.local.JsonCodec
import com.example.data.preferences.AppPreferences
import com.example.domain.FinalScores
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.PauseInterval
import com.example.domain.Player
import com.example.domain.RankingMetric
import com.example.domain.RoundRecord
import com.example.domain.Session
import com.example.domain.SessionStatus
import com.example.domain.ThemeMode
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types

data class BackupImportPreview(
  val decoded: DecodedBackup,
  val isPwaExport: Boolean,
  val playerCount: Int,
  val sessionCount: Int,
  val gameCount: Int,
  val quickRoundsCount: Int,
) {
  fun summaryLine(): String {
    val parts = buildList {
      add("$playerCount ${if (playerCount == 1) "player" else "players"}")
      add("$sessionCount ${if (sessionCount == 1) "session" else "sessions"}")
      val sessionGames = decoded.games.count { it.sessionId != null }
      add("$sessionGames ${if (sessionGames == 1) "session game" else "session games"}")
      if (quickRoundsCount > 0) {
        add("Quick Game ($quickRoundsCount ${if (quickRoundsCount == 1) "round" else "rounds"})")
      }
    }
    return "Found ${parts.joinToString(", ")}."
  }
}

object PwaMigrationImporter {

  private val PWA_KEYS = setOf(
    "tichuPlayers",
    "tichuSessions",
    "tichuGames",
    "tichuCurrentGameId",
    "A_points",
    "B_points",
    "teamAName",
    "teamBName",
    "roundScores",
    "roundNumber",
    "winThreshold",
    "chipsVisible",
    "tichuTheme",
    "tichuModeHintDismissed",
    "tichuPlayersRankingSort",
    "tichuSessionRankingSort",
  )

  const val EXPORT_SCRIPT: String =
    "(function(){var k=['tichuPlayers','tichuSessions','tichuGames','tichuCurrentGameId','A_points','B_points','teamAName','teamBName','roundScores','roundNumber','winThreshold','chipsVisible','tichuTheme','tichuModeHintDismissed','tichuPlayersRankingSort','tichuSessionRankingSort'],d={};k.forEach(function(x){var v=localStorage.getItem(x);if(v!==null)d[x]=v;});var j=JSON.stringify(d,null,2);if(typeof copy==='function'){copy(j);}else if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(j).catch(function(){});}var a=document.createElement('a');a.href=URL.createObjectURL(new Blob([j],{type:'application/json'}));a.download='tichu-pwa-export.json';document.body.appendChild(a);a.click();a.remove();})();"

  const val EXPORT_BOOKMARKLET: String = "javascript:$EXPORT_SCRIPT"

  private val mapAdapter: JsonAdapter<Map<String, Any?>> = JsonCodec.moshi.adapter(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
  )

  private val listAdapter: JsonAdapter<List<Any?>> = JsonCodec.moshi.adapter(
    Types.newParameterizedType(List::class.java, Any::class.java),
  )

  fun looksLikePwaExport(rootMap: Map<String, Any?>): Boolean =
    !rootMap.containsKey("schemaVersion") && rootMap.keys.any { it in PWA_KEYS }

  fun decode(json: String, nowMs: Long = System.currentTimeMillis()): DecodedBackup {
    val root = runCatching { mapAdapter.fromJson(json.trim()) }.getOrNull()
      ?: throw IllegalArgumentException("Could not parse JSON. Make sure you pasted or selected a valid JSON export.")
    require(looksLikePwaExport(root)) {
      "Unrecognized export format. No Tichu Counter PWA data keys were found."
    }
    return decodeRootMap(root, nowMs)
  }

  internal fun decodeRootMap(root: Map<String, Any?>, nowMs: Long = System.currentTimeMillis()): DecodedBackup {
    val winThreshold = asInt(root["winThreshold"], 500).coerceAtLeast(1)
    val chipsVisible = if (root.containsKey("chipsVisible")) asBoolean(root["chipsVisible"], true) else true
    val modeHintDismissed = asBoolean(root["tichuModeHintDismissed"], false)
    val themeMode = ThemeMode.fromKey(asString(root["tichuTheme"]))
    val playersRankingSort = RankingMetric.fromId(asString(root["tichuPlayersRankingSort"]))
    val sessionRankingSort = RankingMetric.fromId(asString(root["tichuSessionRankingSort"]))
    val quickTeamAName = asString(root["teamAName"])?.takeIf { it.isNotBlank() } ?: "Team A"
    val quickTeamBName = asString(root["teamBName"])?.takeIf { it.isNotBlank() } ?: "Team B"

    val players = asList(root["tichuPlayers"]).mapNotNull { raw ->
      val item = asMap(raw) ?: return@mapNotNull null
      val id = asString(item["id"])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val name = asString(item["name"])?.trim()?.takeIf { it.isNotEmpty() } ?: "Player"
      Player(
        id = id,
        name = name,
        active = asBoolean(item["active"], true),
        createdAt = asLong(item["createdAt"]) ?: nowMs,
        statsResetAt = asLong(item["statsResetAt"]),
      )
    }

    val sessions = asList(root["tichuSessions"]).mapNotNull { raw ->
      val item = asMap(raw) ?: return@mapNotNull null
      val id = asString(item["id"])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val status = SessionStatus.fromKey(asString(item["status"]))
      val playerIds = asStringList(item["playerIds"])
      val pauseLog = asList(item["pauseLog"]).mapNotNull { rawPause ->
        val p = asMap(rawPause) ?: return@mapNotNull null
        val pausedAt = asLong(p["pausedAt"]) ?: return@mapNotNull null
        PauseInterval(pausedAt = pausedAt, resumedAt = asLong(p["resumedAt"]))
      }
      Session(
        id = id,
        status = status,
        playerIds = playerIds,
        nextPoolStart = asInt(item["nextPoolStart"], 0),
        pauseLog = pauseLog,
        startedAt = asLong(item["startedAt"]) ?: nowMs,
        endedAt = asLong(item["endedAt"]),
      )
    }

    val validSessionIds = sessions.map { it.id }.toSet()

    val sessionGames = asList(root["tichuGames"]).mapNotNull { raw ->
      val item = asMap(raw) ?: return@mapNotNull null
      val id = asString(item["id"])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val sessionId = asString(item["sessionId"])?.takeIf { it.isNotBlank() }
      if (sessionId != null && sessionId !in validSessionIds) return@mapNotNull null

      val mode = when (asString(item["mode"])) {
        GameMode.THREE_TEAMS.key -> GameMode.THREE_TEAMS
        GameMode.ROTATION.key -> GameMode.ROTATION
        GameMode.FIXED.key -> GameMode.FIXED
        null -> GameMode.FIXED // PWA fallback for legacy pre-mode games
        else -> GameMode.FIXED
      }

      val teams = asNestedStringList(item["teams"])
      val fixedTeam = asStringList(item["fixedTeam"])
      val pool = asStringList(item["pool"])
      val firstRestingTeam = asIntOrNull(item["firstRestingTeam"])
      val winner = asString(item["winner"])?.takeIf { it.isNotBlank() }
      val pendingNextGame = asBoolean(item["pendingNextGame"], false)
      val finalScoresMap = asMap(item["finalScores"])
      val finalScores = finalScoresMap?.let { fs ->
        FinalScores(
          teamA = asInt(fs["teamA"], 0),
          teamB = asInt(fs["teamB"], 0),
          teamC = asIntOrNull(fs["teamC"]),
        )
      }
      val nextDealerPlayerId = asString(item["nextDealerPlayerId"])?.takeIf { it.isNotBlank() }
      val startedAt = asLong(item["startedAt"]) ?: nowMs
      val endedAt = asLong(item["endedAt"])

      val rounds = asList(item["rounds"]).mapIndexedNotNull { idx, rawRound ->
        val r = asMap(rawRound) ?: return@mapIndexedNotNull null
        if (mode == GameMode.THREE_TEAMS) {
          val rawScores = asIntList(r["scores"], defaultValue = 0)
          val scores = List(3) { i -> rawScores.getOrElse(i) { 0 } }
          val activeTeams = asIntList(r["activeTeams"], defaultValue = 0).ifEmpty { listOf(0, 1) }
          val rawMods = asIntList(r["tichuMods"], defaultValue = 0)
          val mods = List(3) { i -> rawMods.getOrElse(i) { 0 } }
          val rawDoubles = asBooleanList(r["doubleWins"])
          val doubles = List(3) { i -> rawDoubles.getOrElse(i) { false } }
          RoundRecord(
            gameId = id,
            roundNumber = idx + 1,
            scores = scores,
            activeTeams = activeTeams,
            tichuMods = mods,
            doubleWins = doubles,
            sittingOutPlayerId = null,
            dealerPlayerId = asString(r["dealerPlayerId"])?.takeIf { it.isNotBlank() },
          )
        } else {
          val scores = if (r.containsKey("scores")) {
            val s = asIntList(r["scores"], defaultValue = 0)
            listOf(s.getOrElse(0) { 0 }, s.getOrElse(1) { 0 })
          } else {
            listOf(asInt(r["teamAPoints"], 0), asInt(r["teamBPoints"], 0))
          }
          val mods = if (r.containsKey("tichuMods")) {
            val m = asIntList(r["tichuMods"], defaultValue = 0)
            listOf(m.getOrElse(0) { 0 }, m.getOrElse(1) { 0 })
          } else {
            listOf(asInt(r["teamATichuMod"], 0), asInt(r["teamBTichuMod"], 0))
          }
          val doubles = if (r.containsKey("doubleWins")) {
            val d = asBooleanList(r["doubleWins"])
            listOf(d.getOrElse(0) { false }, d.getOrElse(1) { false })
          } else {
            listOf(asBoolean(r["teamADoubleWin"], false), asBoolean(r["teamBDoubleWin"], false))
          }
          RoundRecord(
            gameId = id,
            roundNumber = idx + 1,
            scores = scores,
            activeTeams = listOf(0, 1),
            tichuMods = mods,
            doubleWins = doubles,
            sittingOutPlayerId = asString(r["sittingOutPlayerId"])?.takeIf { it.isNotBlank() },
            dealerPlayerId = asString(r["dealerPlayerId"])?.takeIf { it.isNotBlank() },
          )
        }
      }

      Game(
        id = id,
        sessionId = sessionId,
        mode = mode,
        teams = teams,
        fixedTeam = fixedTeam,
        pool = pool,
        firstRestingTeam = firstRestingTeam,
        winThreshold = winThreshold,
        teamAName = "Team A",
        teamBName = "Team B",
        winner = winner,
        pendingNextGame = pendingNextGame,
        finalScores = finalScores,
        nextDealerPlayerId = nextDealerPlayerId,
        startedAt = startedAt,
        endedAt = endedAt,
        rounds = rounds,
      )
    }

    val rawCurrentGameId = asString(root["tichuCurrentGameId"])?.takeIf { it.isNotBlank() }
    val currentSessionGame = sessionGames.firstOrNull {
      it.id == rawCurrentGameId && (it.winner == null || it.pendingNextGame)
    }
    val currentSessionGameId = currentSessionGame?.id

    // In the PWA, Quick Game state in localStorage (roundScores, A_points, B_points)
    // doubles as the live mirror whenever a session game is active. Only import Quick Game
    // rounds when no session game is currently active.
    val quickRounds = if (currentSessionGameId == null) {
      val parsedRounds = asList(root["roundScores"]).mapIndexedNotNull { idx, raw ->
        val entry = asMap(raw) ?: return@mapIndexedNotNull null
        val scoreA = asInt(entry["teamA"], 0)
        val scoreB = asInt(entry["teamB"], 0)
        val labelA = asString(entry["teamATichu"]).orEmpty()
        val labelB = asString(entry["teamBTichu"]).orEmpty()
        RoundRecord(
          gameId = "q_pwa_imported",
          roundNumber = idx + 1,
          scores = listOf(scoreA, scoreB),
          activeTeams = listOf(0, 1),
          tichuMods = listOf(parseTichuModFromLabel(labelA), parseTichuModFromLabel(labelB)),
          doubleWins = listOf(hasDoubleWinLabel(labelA), hasDoubleWinLabel(labelB)),
        )
      }
      if (parsedRounds.isNotEmpty()) {
        parsedRounds
      } else {
        val pointsA = asInt(root["A_points"], 0)
        val pointsB = asInt(root["B_points"], 0)
        if (pointsA != 0 || pointsB != 0) {
          listOf(
            RoundRecord(
              gameId = "q_pwa_imported",
              roundNumber = 1,
              scores = listOf(pointsA, pointsB),
              activeTeams = listOf(0, 1),
              tichuMods = listOf(0, 0),
              doubleWins = listOf(false, false),
            ),
          )
        } else {
          emptyList()
        }
      }
    } else {
      emptyList()
    }

    val games = buildList {
      addAll(sessionGames)
      if (quickRounds.isNotEmpty()) {
        add(
          Game(
            id = "q_pwa_imported",
            sessionId = null,
            mode = GameMode.QUICK,
            winThreshold = winThreshold,
            teamAName = quickTeamAName,
            teamBName = quickTeamBName,
            startedAt = nowMs,
            rounds = quickRounds,
          ),
        )
      }
    }

    return DecodedBackup(
      preferences = AppPreferences(
        themeMode = themeMode,
        winThreshold = winThreshold,
        chipsVisible = chipsVisible,
        modeHintDismissed = modeHintDismissed,
        playersRankingSort = playersRankingSort,
        sessionRankingSort = sessionRankingSort,
        currentSessionGameId = currentSessionGameId,
        quickGameId = if (quickRounds.isNotEmpty()) "q_pwa_imported" else null,
        quickTeamAName = quickTeamAName,
        quickTeamBName = quickTeamBName,
      ),
      players = players,
      sessions = sessions,
      games = games,
    )
  }

  private fun hasDoubleWinLabel(label: String): Boolean =
    label.contains("\u21c9") || label.contains("⇉")

  private fun parseTichuModFromLabel(label: String): Int = when {
    label.contains("<s>GT</s>", ignoreCase = true) || label.contains("✗GT") -> -200
    label.contains("<s>T</s>", ignoreCase = true) || label.contains("✗T") -> -100
    label.contains("GT") -> 200
    label.contains("T") -> 100
    else -> 0
  }

  private fun asString(raw: Any?): String? = when (raw) {
    null -> null
    is String -> raw
    is Number, is Boolean -> raw.toString()
    else -> null
  }

  private fun asInt(raw: Any?, defaultValue: Int): Int =
    asIntOrNull(raw) ?: defaultValue

  private fun asIntOrNull(raw: Any?): Int? = when (raw) {
    null -> null
    is Number -> raw.toInt()
    is String -> raw.trim().toIntOrNull()
    else -> null
  }

  private fun asLong(raw: Any?): Long? = when (raw) {
    null -> null
    is Number -> raw.toLong()
    is String -> raw.trim().toLongOrNull()
    else -> null
  }

  private fun asBoolean(raw: Any?, defaultValue: Boolean): Boolean = when (raw) {
    null -> defaultValue
    is Boolean -> raw
    is Number -> raw.toInt() != 0
    is String -> when (raw.trim().lowercase()) {
      "true", "1" -> true
      "false", "0" -> false
      else -> defaultValue
    }
    else -> defaultValue
  }

  @Suppress("UNCHECKED_CAST")
  private fun asMap(raw: Any?): Map<String, Any?>? = when (raw) {
    is Map<*, *> -> raw.entries.associate { (k, v) -> k.toString() to v }
    is String -> {
      val trimmed = raw.trim()
      if (trimmed.startsWith("{")) runCatching { mapAdapter.fromJson(trimmed) }.getOrNull() else null
    }
    else -> null
  }

  private fun asList(raw: Any?): List<Any?> = when (raw) {
    is List<*> -> raw
    is String -> {
      val trimmed = raw.trim()
      if (trimmed.isEmpty() || trimmed == "null") {
        emptyList()
      } else if (trimmed.startsWith("[")) {
        runCatching { listAdapter.fromJson(trimmed) }.getOrNull().orEmpty()
      } else {
        emptyList()
      }
    }
    else -> emptyList()
  }

  private fun asStringList(raw: Any?): List<String> =
    asList(raw).mapNotNull { asString(it)?.takeIf { s -> s.isNotBlank() } }

  private fun asNestedStringList(raw: Any?): List<List<String>> =
    asList(raw).map { asStringList(it) }

  private fun asIntList(raw: Any?, defaultValue: Int): List<Int> =
    asList(raw).map { asInt(it, defaultValue) }

  private fun asBooleanList(raw: Any?): List<Boolean> =
    asList(raw).map { asBoolean(it, false) }
}

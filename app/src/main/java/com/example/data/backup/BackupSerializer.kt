package com.example.data.backup

import com.example.data.local.FinalScoresJson
import com.example.data.local.JsonCodec
import com.example.data.local.PauseIntervalJson
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
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class BackupPreferencesJson(
  val themeMode: String = ThemeMode.SYSTEM.key,
  val winThreshold: Int = 500,
  val chipsVisible: Boolean = true,
  val modeHintDismissed: Boolean = false,
  val playersRankingSort: String = RankingMetric.TOTAL_POINTS.id,
  val sessionRankingSort: String = RankingMetric.TOTAL_POINTS.id,
  val currentSessionGameId: String? = null,
  val quickGameId: String? = null,
  val quickTeamAName: String = "Team A",
  val quickTeamBName: String = "Team B",
)

@JsonClass(generateAdapter = true)
data class BackupPlayerJson(
  val id: String,
  val name: String,
  val active: Boolean = true,
  val createdAt: Long,
  val statsResetAt: Long? = null,
)

@JsonClass(generateAdapter = true)
data class BackupSessionJson(
  val id: String,
  val status: String,
  val playerIds: List<String>,
  val nextPoolStart: Int = 0,
  val pauseLog: List<PauseIntervalJson> = emptyList(),
  val startedAt: Long,
  val endedAt: Long? = null,
)

@JsonClass(generateAdapter = true)
data class BackupRoundJson(
  val roundNumber: Int,
  val scores: List<Int>,
  val activeTeams: List<Int> = listOf(0, 1),
  val tichuMods: List<Int>,
  val doubleWins: List<Boolean>,
  val sittingOutPlayerId: String? = null,
  val dealerPlayerId: String? = null,
)

@JsonClass(generateAdapter = true)
data class BackupGameJson(
  val id: String,
  val sessionId: String? = null,
  val mode: String,
  val teams: List<List<String>> = emptyList(),
  val fixedTeam: List<String> = emptyList(),
  val pool: List<String> = emptyList(),
  val firstRestingTeam: Int? = null,
  val winThreshold: Int = 500,
  val teamAName: String = "Team A",
  val teamBName: String = "Team B",
  val winner: String? = null,
  val pendingNextGame: Boolean = false,
  val finalScores: FinalScoresJson? = null,
  val nextDealerPlayerId: String? = null,
  val startedAt: Long,
  val endedAt: Long? = null,
  val rounds: List<BackupRoundJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TichuBackupJson(
  val schemaVersion: Int = 1,
  val exportedAt: Long,
  val preferences: BackupPreferencesJson = BackupPreferencesJson(),
  val players: List<BackupPlayerJson> = emptyList(),
  val sessions: List<BackupSessionJson> = emptyList(),
  val games: List<BackupGameJson> = emptyList(),
)

data class DecodedBackup(
  val preferences: AppPreferences,
  val players: List<Player>,
  val sessions: List<Session>,
  val games: List<Game>,
)

object BackupSerializer {
  private val adapter = JsonCodec.moshi.adapter(TichuBackupJson::class.java).indent("  ")
  private val rootMapAdapter: JsonAdapter<Map<String, Any?>> = JsonCodec.moshi.adapter(
    Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java),
  )

  fun encode(
    preferences: AppPreferences,
    players: List<Player>,
    sessions: List<Session>,
    games: List<Game>,
    exportedAt: Long = System.currentTimeMillis(),
  ): String {
    val payload = TichuBackupJson(
      schemaVersion = 1,
      exportedAt = exportedAt,
      preferences = BackupPreferencesJson(
        themeMode = preferences.themeMode.key,
        winThreshold = preferences.winThreshold,
        chipsVisible = preferences.chipsVisible,
        modeHintDismissed = preferences.modeHintDismissed,
        playersRankingSort = preferences.playersRankingSort.id,
        sessionRankingSort = preferences.sessionRankingSort.id,
        currentSessionGameId = preferences.currentSessionGameId,
        quickGameId = preferences.quickGameId,
        quickTeamAName = preferences.quickTeamAName,
        quickTeamBName = preferences.quickTeamBName,
      ),
      players = players.map {
        BackupPlayerJson(
          id = it.id,
          name = it.name,
          active = it.active,
          createdAt = it.createdAt,
          statsResetAt = it.statsResetAt,
        )
      },
      sessions = sessions.map {
        BackupSessionJson(
          id = it.id,
          status = it.status.key,
          playerIds = it.playerIds,
          nextPoolStart = it.nextPoolStart,
          pauseLog = it.pauseLog.map { p -> PauseIntervalJson(p.pausedAt, p.resumedAt) },
          startedAt = it.startedAt,
          endedAt = it.endedAt,
        )
      },
      games = games.map { g ->
        BackupGameJson(
          id = g.id,
          sessionId = g.sessionId,
          mode = g.mode.key,
          teams = g.teams,
          fixedTeam = g.fixedTeam,
          pool = g.pool,
          firstRestingTeam = g.firstRestingTeam,
          winThreshold = g.winThreshold,
          teamAName = g.teamAName,
          teamBName = g.teamBName,
          winner = g.winner,
          pendingNextGame = g.pendingNextGame,
          finalScores = g.finalScores?.let { FinalScoresJson(it.teamA, it.teamB, it.teamC) },
          nextDealerPlayerId = g.nextDealerPlayerId,
          startedAt = g.startedAt,
          endedAt = g.endedAt,
          rounds = g.rounds.sortedBy { it.roundNumber }.map { r ->
            BackupRoundJson(
              roundNumber = r.roundNumber,
              scores = r.scores,
              activeTeams = r.activeTeams,
              tichuMods = r.tichuMods,
              doubleWins = r.doubleWins,
              sittingOutPlayerId = r.sittingOutPlayerId,
              dealerPlayerId = r.dealerPlayerId,
            )
          },
        )
      },
    )
    return adapter.toJson(payload)
  }

  fun decode(json: String): DecodedBackup {
    val parsed = adapter.fromJson(json)
      ?: throw IllegalArgumentException("Backup file could not be parsed.")
    require(parsed.schemaVersion >= 1) { "Unsupported backup schema version." }

    val sessionIds = parsed.sessions.map { it.id }.toSet()
    val validGames = parsed.games.filter { it.sessionId == null || it.sessionId in sessionIds }

    return DecodedBackup(
      preferences = AppPreferences(
        themeMode = ThemeMode.fromKey(parsed.preferences.themeMode),
        winThreshold = parsed.preferences.winThreshold.coerceAtLeast(1),
        chipsVisible = parsed.preferences.chipsVisible,
        modeHintDismissed = parsed.preferences.modeHintDismissed,
        playersRankingSort = RankingMetric.fromId(parsed.preferences.playersRankingSort),
        sessionRankingSort = RankingMetric.fromId(parsed.preferences.sessionRankingSort),
        currentSessionGameId = parsed.preferences.currentSessionGameId,
        quickGameId = parsed.preferences.quickGameId,
        quickTeamAName = parsed.preferences.quickTeamAName.ifBlank { "Team A" },
        quickTeamBName = parsed.preferences.quickTeamBName.ifBlank { "Team B" },
      ),
      players = parsed.players.map {
        Player(
          id = it.id,
          name = it.name.ifBlank { "Player" },
          active = it.active,
          createdAt = it.createdAt,
          statsResetAt = it.statsResetAt,
        )
      },
      sessions = parsed.sessions.map {
        Session(
          id = it.id,
          status = SessionStatus.fromKey(it.status),
          playerIds = it.playerIds,
          nextPoolStart = it.nextPoolStart,
          pauseLog = it.pauseLog.map { p -> PauseInterval(p.pausedAt, p.resumedAt) },
          startedAt = it.startedAt,
          endedAt = it.endedAt,
        )
      },
      games = validGames.map { g ->
        Game(
          id = g.id,
          sessionId = g.sessionId,
          mode = GameMode.fromKey(g.mode),
          teams = g.teams,
          fixedTeam = g.fixedTeam,
          pool = g.pool,
          firstRestingTeam = g.firstRestingTeam,
          winThreshold = g.winThreshold.coerceAtLeast(1),
          teamAName = g.teamAName,
          teamBName = g.teamBName,
          winner = g.winner,
          pendingNextGame = g.pendingNextGame,
          finalScores = g.finalScores?.let { FinalScores(it.teamA, it.teamB, it.teamC) },
          nextDealerPlayerId = g.nextDealerPlayerId,
          startedAt = g.startedAt,
          endedAt = g.endedAt,
          rounds = g.rounds.sortedBy { it.roundNumber }.map { r ->
            RoundRecord(
              gameId = g.id,
              roundNumber = r.roundNumber,
              scores = r.scores,
              activeTeams = r.activeTeams,
              tichuMods = r.tichuMods,
              doubleWins = r.doubleWins,
              sittingOutPlayerId = r.sittingOutPlayerId,
              dealerPlayerId = r.dealerPlayerId,
            )
          },
        )
      },
    )
  }

  fun decodeWithPreview(
    json: String,
    nowMs: Long = System.currentTimeMillis(),
  ): BackupImportPreview {
    val trimmed = json.trim()
    val root = runCatching { rootMapAdapter.fromJson(trimmed) }.getOrNull()
      ?: throw IllegalArgumentException("Could not parse JSON. Check that the file or pasted text is valid JSON.")
    val isPwa = PwaMigrationImporter.looksLikePwaExport(root)
    val decoded = if (isPwa) {
      PwaMigrationImporter.decodeRootMap(root, nowMs)
    } else {
      require(root.containsKey("schemaVersion")) {
        "Unrecognized JSON format. Select a Tichu Counter backup or a PWA localStorage export."
      }
      decode(trimmed)
    }
    val quickRoundsCount = decoded.games.firstOrNull { it.sessionId == null && it.mode == GameMode.QUICK }?.rounds?.size ?: 0
    return BackupImportPreview(
      decoded = decoded,
      isPwaExport = isPwa,
      playerCount = decoded.players.size,
      sessionCount = decoded.sessions.size,
      gameCount = decoded.games.size,
      quickRoundsCount = quickRoundsCount,
    )
  }
}

package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.Game
import com.example.domain.GameMode
import com.example.domain.Player
import com.example.domain.RoundRecord
import com.example.domain.Session
import com.example.domain.SessionStatus

@Entity(tableName = "players")
data class PlayerEntity(
  @PrimaryKey val id: String,
  val name: String,
  val active: Boolean = true,
  val createdAt: Long,
  val statsResetAt: Long? = null,
) {
  fun toDomain(): Player = Player(
    id = id,
    name = name,
    active = active,
    createdAt = createdAt,
    statsResetAt = statsResetAt,
  )

  companion object {
    fun fromDomain(player: Player): PlayerEntity = PlayerEntity(
      id = player.id,
      name = player.name,
      active = player.active,
      createdAt = player.createdAt,
      statsResetAt = player.statsResetAt,
    )
  }
}

@Entity(tableName = "sessions")
data class SessionEntity(
  @PrimaryKey val id: String,
  val status: String,
  val playerIdsJson: String,
  val nextPoolStart: Int = 0,
  val pauseLogJson: String = "[]",
  val startedAt: Long,
  val endedAt: Long? = null,
) {
  fun toDomain(): Session = Session(
    id = id,
    status = SessionStatus.fromKey(status),
    playerIds = JsonCodec.decodeStringList(playerIdsJson),
    nextPoolStart = nextPoolStart,
    pauseLog = JsonCodec.decodePauseLog(pauseLogJson),
    startedAt = startedAt,
    endedAt = endedAt,
  )

  companion object {
    fun fromDomain(session: Session): SessionEntity = SessionEntity(
      id = session.id,
      status = session.status.key,
      playerIdsJson = JsonCodec.encodeStringList(session.playerIds),
      nextPoolStart = session.nextPoolStart,
      pauseLogJson = JsonCodec.encodePauseLog(session.pauseLog),
      startedAt = session.startedAt,
      endedAt = session.endedAt,
    )
  }
}

@Entity(
  tableName = "games",
  foreignKeys = [
    ForeignKey(
      entity = SessionEntity::class,
      parentColumns = ["id"],
      childColumns = ["sessionId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("sessionId")],
)
data class GameEntity(
  @PrimaryKey val id: String,
  val sessionId: String? = null,
  val mode: String,
  val teamsJson: String = "[]",
  val fixedTeamJson: String = "[]",
  val poolJson: String = "[]",
  val firstRestingTeam: Int? = null,
  val winThreshold: Int = 500,
  val teamAName: String = "Team A",
  val teamBName: String = "Team B",
  val winner: String? = null,
  val pendingNextGame: Boolean = false,
  val finalScoresJson: String? = null,
  val nextDealerPlayerId: String? = null,
  val startedAt: Long,
  val endedAt: Long? = null,
) {
  fun toDomain(rounds: List<RoundRecord>): Game = Game(
    id = id,
    sessionId = sessionId,
    mode = GameMode.fromKey(mode),
    teams = JsonCodec.decodeNestedStringList(teamsJson),
    fixedTeam = JsonCodec.decodeStringList(fixedTeamJson),
    pool = JsonCodec.decodeStringList(poolJson),
    firstRestingTeam = firstRestingTeam,
    winThreshold = winThreshold,
    teamAName = teamAName,
    teamBName = teamBName,
    winner = winner,
    pendingNextGame = pendingNextGame,
    finalScores = JsonCodec.decodeFinalScores(finalScoresJson),
    nextDealerPlayerId = nextDealerPlayerId,
    startedAt = startedAt,
    endedAt = endedAt,
    rounds = rounds.sortedBy { it.roundNumber },
  )

  companion object {
    fun fromDomain(game: Game): GameEntity = GameEntity(
      id = game.id,
      sessionId = game.sessionId,
      mode = game.mode.key,
      teamsJson = JsonCodec.encodeNestedStringList(game.teams),
      fixedTeamJson = JsonCodec.encodeStringList(game.fixedTeam),
      poolJson = JsonCodec.encodeStringList(game.pool),
      firstRestingTeam = game.firstRestingTeam,
      winThreshold = game.winThreshold,
      teamAName = game.teamAName,
      teamBName = game.teamBName,
      winner = game.winner,
      pendingNextGame = game.pendingNextGame,
      finalScoresJson = JsonCodec.encodeFinalScores(game.finalScores),
      nextDealerPlayerId = game.nextDealerPlayerId,
      startedAt = game.startedAt,
      endedAt = game.endedAt,
    )
  }
}

@Entity(
  tableName = "rounds",
  foreignKeys = [
    ForeignKey(
      entity = GameEntity::class,
      parentColumns = ["id"],
      childColumns = ["gameId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index("gameId")],
)
data class RoundEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val gameId: String,
  val roundNumber: Int,
  val scoresJson: String,
  val activeTeamsJson: String,
  val tichuModsJson: String,
  val doubleWinsJson: String,
  val sittingOutPlayerId: String? = null,
  val dealerPlayerId: String? = null,
) {
  fun toDomain(): RoundRecord = RoundRecord(
    id = id,
    gameId = gameId,
    roundNumber = roundNumber,
    scores = JsonCodec.decodeIntList(scoresJson),
    activeTeams = JsonCodec.decodeIntList(activeTeamsJson),
    tichuMods = JsonCodec.decodeIntList(tichuModsJson),
    doubleWins = JsonCodec.decodeBooleanList(doubleWinsJson),
    sittingOutPlayerId = sittingOutPlayerId,
    dealerPlayerId = dealerPlayerId,
  )

  companion object {
    fun fromDomain(round: RoundRecord): RoundEntity = RoundEntity(
      id = round.id,
      gameId = round.gameId,
      roundNumber = round.roundNumber,
      scoresJson = JsonCodec.encodeIntList(round.scores),
      activeTeamsJson = JsonCodec.encodeIntList(round.activeTeams),
      tichuModsJson = JsonCodec.encodeIntList(round.tichuMods),
      doubleWinsJson = JsonCodec.encodeBooleanList(round.doubleWins),
      sittingOutPlayerId = round.sittingOutPlayerId,
      dealerPlayerId = round.dealerPlayerId,
    )
  }
}

data class GameWithRounds(
  @Embedded val game: GameEntity,
  @Relation(
    parentColumn = "id",
    entityColumn = "gameId",
  )
  val rounds: List<RoundEntity>,
) {
  fun toDomain(): Game = game.toDomain(rounds.map { it.toDomain() })
}

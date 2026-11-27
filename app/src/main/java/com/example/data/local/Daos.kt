package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
  @Query("SELECT * FROM players ORDER BY createdAt ASC")
  fun observeAll(): Flow<List<PlayerEntity>>

  @Query("SELECT * FROM players ORDER BY createdAt ASC")
  suspend fun getAll(): List<PlayerEntity>

  @Query("SELECT * FROM players WHERE id = :id")
  suspend fun getById(id: String): PlayerEntity?

  @Upsert
  suspend fun upsert(entity: PlayerEntity)

  @Upsert
  suspend fun upsertAll(entities: List<PlayerEntity>)

  @Query("DELETE FROM players WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM players")
  suspend fun deleteAll()
}

@Dao
interface SessionDao {
  @Query("SELECT * FROM sessions ORDER BY startedAt ASC")
  fun observeAll(): Flow<List<SessionEntity>>

  @Query("SELECT * FROM sessions ORDER BY startedAt ASC")
  suspend fun getAll(): List<SessionEntity>

  @Query("SELECT * FROM sessions WHERE id = :id")
  suspend fun getById(id: String): SessionEntity?

  @Upsert
  suspend fun upsert(entity: SessionEntity)

  @Upsert
  suspend fun upsertAll(entities: List<SessionEntity>)

  @Query("DELETE FROM sessions WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM sessions")
  suspend fun deleteAll()
}

@Dao
interface GameDao {
  @Transaction
  @Query("SELECT * FROM games ORDER BY startedAt ASC")
  fun observeGamesWithRounds(): Flow<List<GameWithRounds>>

  @Transaction
  @Query("SELECT * FROM games ORDER BY startedAt ASC")
  suspend fun getAllGamesWithRounds(): List<GameWithRounds>

  @Transaction
  @Query("SELECT * FROM games WHERE id = :id")
  suspend fun getGameWithRounds(id: String): GameWithRounds?

  @Upsert
  suspend fun upsert(entity: GameEntity)

  @Upsert
  suspend fun upsertAll(entities: List<GameEntity>)

  @Query("DELETE FROM games WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM games WHERE sessionId = :sessionId")
  suspend fun deleteBySessionId(sessionId: String)

  @Query("DELETE FROM games")
  suspend fun deleteAll()
}

@Dao
interface RoundDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: RoundEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(entities: List<RoundEntity>)

  @Query("SELECT * FROM rounds WHERE gameId = :gameId ORDER BY roundNumber ASC")
  suspend fun getRoundsForGame(gameId: String): List<RoundEntity>

  @Query("DELETE FROM rounds WHERE id = (SELECT id FROM rounds WHERE gameId = :gameId ORDER BY roundNumber DESC, id DESC LIMIT 1)")
  suspend fun deleteLastRound(gameId: String)

  @Query("DELETE FROM rounds WHERE gameId = :gameId")
  suspend fun deleteRoundsForGame(gameId: String)

  @Query("DELETE FROM rounds")
  suspend fun deleteAll()
}

package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    PlayerEntity::class,
    SessionEntity::class,
    GameEntity::class,
    RoundEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun playerDao(): PlayerDao
  abstract fun sessionDao(): SessionDao
  abstract fun gameDao(): GameDao
  abstract fun roundDao(): RoundDao

  companion object {
    @Volatile
    private var instance: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase =
      instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "tichu_counter.db",
        ).build().also { instance = it }
      }
  }
}

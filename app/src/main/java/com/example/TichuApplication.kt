package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferencesStore
import com.example.data.repository.TichuRepository

class TichuApplication : Application() {
  val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
  val preferencesStore: PreferencesStore by lazy { PreferencesStore(this) }
  val repository: TichuRepository by lazy { TichuRepository(database, preferencesStore) }
}

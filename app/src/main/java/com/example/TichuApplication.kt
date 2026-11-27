package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preferences.PreferencesStore
import com.example.data.repository.FirestoreTichuRepository
import com.example.data.repository.LocalTichuRepository
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class TichuApplication : Application() {
  val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
  val preferencesStore: PreferencesStore by lazy { PreferencesStore(this) }
  val firestore: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
  }

  fun createCloudRepository(user: FirebaseUser): FirestoreTichuRepository = FirestoreTichuRepository(
    db = firestore,
    currentUserId = user.uid,
    currentUserDisplayName = user.displayName.orEmpty(),
    currentUserEmail = user.email,
    preferencesStore = preferencesStore,
    localDatabase = database,
  )

  fun createLocalRepository(): LocalTichuRepository = LocalTichuRepository(
    localDatabase = database,
    preferencesStore = preferencesStore,
  )
}

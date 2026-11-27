package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.backup.BackupSerializer
import com.example.data.backup.DecodedBackup
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.PreferencesStore
import com.example.data.remote.FirestoreMapper
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
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
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class FirestoreTichuRepository(
  private val db: FirebaseFirestore,
  override val currentUserId: String,
  override val currentUserDisplayName: String,
  override val currentUserEmail: String?,
  private val preferencesStore: PreferencesStore,
  private val localDatabase: AppDatabase,
) : TichuRepository {

  override val isGuestMode: Boolean = false

  private val auth = Firebase.auth
  private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val cloudInitMutex = Mutex()

  private var cloudInitialized = false

  private val _streamError = MutableStateFlow<String?>(null)
  override val streamErrorFlow: StateFlow<String?> = _streamError.asStateFlow()

  private val _pendingGuestMerge = MutableStateFlow<PendingGuestData?>(null)
  val pendingGuestMergeFlow: StateFlow<PendingGuestData?> = _pendingGuestMerge.asStateFlow()

  override fun clearStreamError() {
    _streamError.value = null
  }

  private fun requireUserId(): String {
    val uid = auth.currentUser?.uid
    if (uid.isNullOrBlank() || uid != currentUserId) {
      throw IllegalStateException("Please sign in with Google to continue.")
    }
    return uid
  }

  private val userSettingsRef get() = db.collection("users").document(currentUserId)
  private val playersCol get() = db.collection("players")
  private val invitesCol get() = db.collection("playerInvites")
  private val sessionsCol get() = db.collection("sessions")
  private val gamesCol get() = db.collection("games")

  override val preferencesFlow: Flow<AppPreferences> = userSettingsRef
    .snapshots()
    .map { doc: DocumentSnapshot ->
      if (!doc.exists()) {
        preferencesStore.getSnapshot()
      } else {
        val parsed = FirestoreMapper.preferencesFromSnapshot(doc, preferencesStore.getSnapshot())
        preferencesStore.setThemeMode(parsed.themeMode)
        parsed
      }
    }
    .catch { error ->
      handleStreamFailure(error, OperationType.GET, "users/$currentUserId")
      emit(preferencesStore.getSnapshot())
    }
    .shareIn(repoScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  override val playersFlow: Flow<List<Player>> = playersCol
    .whereArrayContains("memberUids", currentUserId)
    .snapshots()
    .map { snap: QuerySnapshot ->
      snap.documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
        .sortedWith(compareBy<Player> { it.createdAt }.thenBy { it.name.lowercase() })
    }
    .catch { error ->
      handleStreamFailure(error, OperationType.LIST, "players")
      emit(emptyList())
    }
    .shareIn(repoScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  override val sessionsFlow: Flow<List<Session>> = sessionsCol
    .whereArrayContains("participantUids", currentUserId)
    .snapshots()
    .map { snap: QuerySnapshot ->
      snap.documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
        .sortedBy { it.startedAt }
    }
    .catch { error ->
      handleStreamFailure(error, OperationType.LIST, "sessions")
      emit(emptyList())
    }
    .shareIn(repoScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  override val gamesFlow: Flow<List<Game>> = gamesCol
    .whereArrayContains("participantUids", currentUserId)
    .snapshots()
    .map { snap: QuerySnapshot ->
      snap.documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
        .sortedBy { it.startedAt }
    }
    .catch { error ->
      handleStreamFailure(error, OperationType.LIST, "games")
      emit(emptyList())
    }
    .shareIn(repoScope, SharingStarted.WhileSubscribed(5000), replay = 1)

  override val activeOrPausedSessionFlow: Flow<Session?> = sessionsFlow.map { sessions ->
    val live = sessions.filter { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }
    live.firstOrNull { it.ownerUid == currentUserId } ?: live.firstOrNull()
  }

  override val currentSessionGameFlow: Flow<Game?> = combine(
    activeOrPausedSessionFlow,
    gamesFlow,
    preferencesFlow,
  ) { activeSession, games, prefs ->
    if (activeSession != null && activeSession.ownerUid != currentUserId) {
      return@combine games
        .filter { it.sessionId == activeSession.id }
        .lastOrNull { it.winner == null || it.pendingNextGame }
    }
    val id = prefs.currentSessionGameId
    if (id != null) {
      val explicit = games.firstOrNull { it.id == id }
      if (explicit != null) {
        return@combine if (explicit.winner != null && !explicit.pendingNextGame) null else explicit
      }
    }
    if (activeSession != null && activeSession.ownerUid == currentUserId) {
      return@combine games
        .filter { it.sessionId == activeSession.id }
        .lastOrNull { it.winner == null || it.pendingNextGame }
    }
    null
  }

  override val activeQuickGameFlow: Flow<Game?> = combine(
    gamesFlow,
    preferencesFlow,
  ) { games, prefs ->
    val id = prefs.quickGameId
    if (id != null) {
      games.firstOrNull { it.id == id && it.sessionId == null && it.ownerUid == currentUserId }
        ?: games.lastOrNull { it.sessionId == null && it.mode == GameMode.QUICK && it.ownerUid == currentUserId }
    } else {
      games.lastOrNull { it.sessionId == null && it.mode == GameMode.QUICK && it.ownerUid == currentUserId }
    }
  }

  private fun handleStreamFailure(error: Throwable, op: OperationType, path: String) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val rootError = firestoreError ?: (error as? Exception)
    if (rootError != null && auth.currentUser?.uid == currentUserId) {
      handleFirestoreError(rootError, op, path)
      _streamError.value = friendlyMessage(rootError)
    }
  }

  override fun close() {
    repoScope.cancel()
  }

  private fun newId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"

  private fun newInviteCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val rng = SecureRandom()
    val suffix = buildString {
      repeat(6) { append(chars[rng.nextInt(chars.length)]) }
    }
    return "TC$suffix"
  }

  // ---- Bootstrap, Local Room Migration & Guest Merge ----

  suspend fun initializeUserCloudState() {
    cloudInitMutex.withLock {
      if (cloudInitialized) return
      val uid = requireUserId()
      val userPath = "users/$uid"
      try {
        val userDoc = userSettingsRef.get().await()
        val localPrefs = preferencesStore.getSnapshot()
        val localPlayers = localDatabase.playerDao().getAll().map { it.toDomain() }
        val localSessions = localDatabase.sessionDao().getAll().map { it.toDomain() }
        val localGames = localDatabase.gameDao().getAllGamesWithRounds().map { it.toDomain() }
        val nonEmptyGames = localGames.filter { it.sessionId != null || it.rounds.isNotEmpty() }
        val hasLocalData = localPlayers.isNotEmpty() || localSessions.isNotEmpty() || nonEmptyGames.isNotEmpty()

        if (userDoc.exists() && userDoc.get("localMigratedAt") != null) {
          if (hasLocalData) {
            _pendingGuestMerge.value = PendingGuestData(
              playerCount = localPlayers.size,
              sessionCount = localSessions.size,
              gameCount = nonEmptyGames.size,
            )
          } else if (localGames.isNotEmpty()) {
            // Clear empty quick-game shells created during guest browsing
            clearLocalRoomTables()
          }
          cloudInitialized = true
          return
        }

        val migratedAt = System.currentTimeMillis()
        if (!userDoc.exists()) {
          val initPrefs = if (hasLocalData) {
            val uidTag = uid.take(6)
            val idMap = buildIdRemap(uidTag, localPlayers, localSessions, localGames)
            localPrefs.copy(
              currentSessionGameId = localPrefs.currentSessionGameId?.let { idMap[it] ?: it },
              quickGameId = localPrefs.quickGameId?.let { idMap[it] ?: it },
            )
          } else {
            localPrefs
          }
          val display = currentUserDisplayName.trim()
            .ifBlank { currentUserEmail?.substringBefore("@") ?: "Player" }
            .ifBlank { "Player" }
          userSettingsRef.set(
            FirestoreMapper.userSettingsToCreateMap(
              uid = uid,
              displayName = display,
              prefs = initPrefs,
              localMigratedAt = if (hasLocalData) null else migratedAt,
            ),
          ).await()
        }

        if (hasLocalData) {
          val uidTag = uid.take(6)
          val idMap = buildIdRemap(uidTag, localPlayers, localSessions, localGames)
          val remappedPlayers = localPlayers.map { p -> p.copy(id = idMap.getValue(p.id), ownerUid = uid) }
          val remappedSessions = localSessions.map { s ->
            s.copy(
              id = idMap.getValue(s.id),
              playerIds = s.playerIds.map { idMap[it] ?: it },
              ownerUid = uid,
              participantUids = listOf(uid),
            )
          }
          val remappedGames = localGames.map { g -> remapGameIds(g, idMap, uid) }

          val writes = buildList<Pair<DocumentReference, Map<String, Any>>> {
            remappedPlayers.forEach { p ->
              add(playersCol.document(p.id) to FirestoreMapper.playerToCreateMap(p, uid))
            }
            remappedSessions.forEach { s ->
              add(sessionsCol.document(s.id) to FirestoreMapper.sessionToCreateMap(s, uid, listOf(uid)))
            }
            remappedGames.forEach { g ->
              add(gamesCol.document(g.id) to FirestoreMapper.gameToCreateMap(g, uid, listOf(uid)))
            }
          }
          commitSetChunks(writes)

          userSettingsRef.update(
            buildMap {
              put("localMigratedAt", migratedAt)
              localPrefs.currentSessionGameId?.let { idMap[it] }?.takeIf { FirestoreMapper.isValidDocId(it) }
                ?.let { put("currentSessionGameId", it) }
              localPrefs.quickGameId?.let { idMap[it] }?.takeIf { FirestoreMapper.isValidDocId(it) }
                ?.let { put("quickGameId", it) }
              put("updatedAt", FieldValue.serverTimestamp())
            },
          ).await()
          clearLocalRoomTables()
        } else if (userDoc.exists()) {
          userSettingsRef.update(
            mapOf(
              "localMigratedAt" to migratedAt,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
          if (localGames.isNotEmpty()) {
            clearLocalRoomTables()
          }
        }

        cloudInitialized = true
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, userPath)
      }
    }
  }

  suspend fun resolvePendingGuestMerge(mergeIntoCloud: Boolean): Result<Unit> {
    return try {
      val uid = requireUserId()
      if (mergeIntoCloud) {
        val localPlayers = localDatabase.playerDao().getAll().map { it.toDomain() }
        val localSessions = localDatabase.sessionDao().getAll().map { it.toDomain() }
        val localGames = localDatabase.gameDao().getAllGamesWithRounds().map { it.toDomain() }
          .filter { it.sessionId != null || it.rounds.isNotEmpty() }

        val existingSessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
          .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
          .filter { it.ownerUid == uid }
        val cloudHasActiveSession = existingSessions.any {
          it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED
        }

        val idMap = buildMap {
          localPlayers.forEach { put(it.id, newId("p")) }
          localSessions.forEach { put(it.id, newId("s")) }
          localGames.forEach { put(it.id, newId("g")) }
        }

        val now = System.currentTimeMillis()
        val newPlayers = localPlayers.map { p -> p.copy(id = idMap.getValue(p.id), ownerUid = uid) }
        val newSessions = localSessions.map { s ->
          val normalized = if (cloudHasActiveSession && s.status != SessionStatus.ENDED) {
            s.copy(
              status = SessionStatus.ENDED,
              endedAt = s.endedAt ?: now,
              pauseLog = s.pauseLog.map { entry ->
                if (entry.resumedAt == null) entry.copy(resumedAt = now) else entry
              },
            )
          } else {
            s
          }
          normalized.copy(
            id = idMap.getValue(s.id),
            playerIds = s.playerIds.map { idMap[it] ?: it },
            ownerUid = uid,
            participantUids = listOf(uid),
          )
        }
        val newGames = localGames.map { g -> remapGameIds(g, idMap, uid) }

        val writes = buildList<Pair<DocumentReference, Map<String, Any>>> {
          newPlayers.forEach { p ->
            add(playersCol.document(p.id) to FirestoreMapper.playerToCreateMap(p, uid))
          }
          newSessions.forEach { s ->
            add(sessionsCol.document(s.id) to FirestoreMapper.sessionToCreateMap(s, uid, listOf(uid)))
          }
          newGames.forEach { g ->
            add(gamesCol.document(g.id) to FirestoreMapper.gameToCreateMap(g, uid, listOf(uid)))
          }
        }
        commitSetChunks(writes)
      }

      clearLocalRoomTables()
      _pendingGuestMerge.value = null
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$currentUserId")
      Result.failure(IllegalStateException(friendlyMessage(e), e))
    }
  }

  private suspend fun clearLocalRoomTables() {
    localDatabase.withTransaction {
      localDatabase.roundDao().deleteAll()
      localDatabase.gameDao().deleteAll()
      localDatabase.sessionDao().deleteAll()
      localDatabase.playerDao().deleteAll()
    }
    preferencesStore.setCurrentSessionGameId(null)
    preferencesStore.setQuickGameId(null)
  }

  private fun buildIdRemap(
    uidTag: String,
    players: List<Player>,
    sessions: List<Session>,
    games: List<Game>,
  ): Map<String, String> = buildMap {
    players.forEach { put(it.id, "${uidTag}_${FirestoreMapper.sanitizeDocId(it.id, "p")}") }
    sessions.forEach { put(it.id, "${uidTag}_${FirestoreMapper.sanitizeDocId(it.id, "s")}") }
    games.forEach { put(it.id, "${uidTag}_${FirestoreMapper.sanitizeDocId(it.id, "g")}") }
  }

  private fun remapGameIds(game: Game, idMap: Map<String, String>, ownerUid: String): Game {
    val newGameId = idMap[game.id] ?: newId("g")
    return game.copy(
      id = newGameId,
      sessionId = game.sessionId?.let { idMap[it] ?: it },
      teams = game.teams.map { team -> team.map { idMap[it] ?: it } },
      fixedTeam = game.fixedTeam.map { idMap[it] ?: it },
      pool = game.pool.map { idMap[it] ?: it },
      nextDealerPlayerId = game.nextDealerPlayerId?.let { idMap[it] ?: it },
      rounds = game.rounds.map { r ->
        r.copy(
          gameId = newGameId,
          sittingOutPlayerId = r.sittingOutPlayerId?.let { idMap[it] ?: it },
          dealerPlayerId = r.dealerPlayerId?.let { idMap[it] ?: it },
        )
      },
      ownerUid = ownerUid,
      participantUids = listOf(ownerUid),
    )
  }

  private suspend fun commitSetChunks(
    writes: List<Pair<DocumentReference, Map<String, Any>>>,
  ) {
    writes.chunked(400).forEach { chunk ->
      val batch = db.batch()
      chunk.forEach { (ref, map) -> batch.set(ref, map) }
      batch.commit().await()
    }
  }

  private suspend fun commitDeleteChunks(
    refs: List<DocumentReference>,
  ) {
    refs.chunked(400).forEach { chunk ->
      val batch = db.batch()
      chunk.forEach { ref -> batch.delete(ref) }
      batch.commit().await()
    }
  }

  // ---- Players & Account Linking ----

  override suspend fun addPlayer(rawName: String): Player {
    val uid = requireUserId()
    initializeUserCloudState()
    val name = rawName.trim()
    require(name.isNotEmpty()) { "Player name can't be empty." }
    require(name.length <= 60) { "Player name must be 60 characters or less." }

    val id = newId("p")
    val player = Player(
      id = id,
      name = name,
      active = true,
      createdAt = System.currentTimeMillis(),
      statsResetAt = null,
      ownerUid = uid,
    )
    val path = "players/$id"
    return try {
      playersCol.document(id).set(FirestoreMapper.playerToCreateMap(player, uid)).await()
      player
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun renamePlayer(id: String, rawName: String) {
    val uid = requireUserId()
    val name = rawName.trim()
    require(name.isNotEmpty()) { "Player name can't be empty." }
    require(name.length <= 60) { "Player name must be 60 characters or less." }
    val path = "players/$id"
    try {
      val doc = playersCol.document(id).get().await()
      val existing = FirestoreMapper.playerFromSnapshot(doc) ?: error("Player not found.")
      check(existing.ownerUid == uid) { "Only the host who created ${existing.name} can rename them." }
      playersCol.document(id).update(
        mapOf(
          "name" to name,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun setPlayerActive(id: String, active: Boolean) {
    val uid = requireUserId()
    val path = "players/$id"
    try {
      val doc = playersCol.document(id).get().await()
      val existing = FirestoreMapper.playerFromSnapshot(doc) ?: error("Player not found.")
      check(existing.ownerUid == uid) { "Only the host can archive or restore ${existing.name}." }
      playersCol.document(id).update(
        mapOf(
          "active" to active,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun resetPlayerStatistics(id: String) {
    val uid = requireUserId()
    val path = "players/$id"
    try {
      val doc = playersCol.document(id).get().await()
      val existing = FirestoreMapper.playerFromSnapshot(doc) ?: error("Player not found.")
      check(existing.ownerUid == uid) { "Only the host can reset ${existing.name}'s statistics." }
      playersCol.document(id).update(
        mapOf(
          "statsResetAt" to System.currentTimeMillis(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun deleteArchivedPlayer(id: String) {
    val uid = requireUserId()
    val path = "players/$id"
    try {
      val doc = playersCol.document(id).get().await()
      val existing = FirestoreMapper.playerFromSnapshot(doc) ?: error("Player not found.")
      check(existing.ownerUid == uid) { "Only the host can permanently delete ${existing.name}." }
      check(!existing.active) { "Archive this player before deleting them permanently." }
      val sessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
        .filter { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }
      check(sessions.none { id in it.playerIds }) {
        "End the active session before deleting ${existing.name}."
      }
      playersCol.document(id).delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun createPlayerInvite(playerId: String): Result<PlayerInvite> {
    var path = "playerInvites"
    return try {
      val uid = requireUserId()
      val playerDoc = playersCol.document(playerId).get().await()
      val player = FirestoreMapper.playerFromSnapshot(playerDoc) ?: error("Player not found.")
      check(player.ownerUid == uid) { "Only the host who created ${player.name} can share a link code." }
      check(player.linkedUid == null) { "${player.name} is already linked to a user account." }

      val ownedSessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
        .filter { it.ownerUid == uid && playerId in it.playerIds }
      val ownedGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
        .filter { it.ownerUid == uid && playerId in FirestoreMapper.gamePlayerIds(it) }

      val code = newInviteCode()
      path = "playerInvites/$code"
      val invite = PlayerInvite(
        code = code,
        ownerUid = uid,
        playerId = playerId,
        playerName = player.name,
        sessionIds = ownedSessions.map { it.id }.take(500),
        gameIds = ownedGames.map { it.id }.take(1000),
        status = "pending",
        expiresAtMs = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000L,
      )
      invitesCol.document(code).set(FirestoreMapper.playerInviteToCreateMap(invite)).await()
      Result.success(invite)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(IllegalStateException(friendlyMessage(e), e))
    }
  }

  override suspend fun claimPlayerInvite(rawCode: String): Result<String> {
    val code = rawCode.trim().uppercase()
    var path = "playerInvites/$code"
    return try {
      val uid = requireUserId()
      require(FirestoreMapper.isValidDocId(code)) { "Enter a valid invite code (letters & numbers)." }

      val inviteDoc = invitesCol.document(code).get().await()
      val invite = FirestoreMapper.playerInviteFromSnapshot(inviteDoc)
        ?: error("Invite code '$code' was not found.")
      check(invite.status == "pending") { "This invite code has already been used or revoked." }
      check(invite.expiresAtMs > System.currentTimeMillis()) { "This invite code has expired." }
      check(invite.ownerUid != uid) { "You already manage ${invite.playerName} on this account." }

      val label = currentUserDisplayName.trim()
        .ifBlank { currentUserEmail?.substringBefore("@") ?: "Linked player" }
        .ifBlank { "Linked player" }
        .take(80)

      val batch = db.batch()
      batch.update(
        playersCol.document(invite.playerId),
        mapOf(
          "linkedUid" to uid,
          "linkedLabel" to label,
          "memberUids" to listOf(invite.ownerUid, uid).distinct(),
          "lastClaimCode" to code,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        invitesCol.document(code),
        mapOf(
          "status" to "claimed",
          "claimedByUid" to uid,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      path = "players/${invite.playerId}"
      batch.commit().await()

      val backfillRefs = invite.sessionIds.map { sessionsCol.document(it) } +
        invite.gameIds.map { gamesCol.document(it) }
      backfillRefs.chunked(400).forEach { chunk ->
        val backfillBatch = db.batch()
        chunk.forEach { ref ->
          backfillBatch.update(
            ref,
            mapOf(
              "participantUids" to FieldValue.arrayUnion(uid),
              "lastLinkedPlayerId" to invite.playerId,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        backfillBatch.commit().await()
      }

      Result.success(invite.playerName)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(IllegalStateException(friendlyMessage(e), e))
    }
  }

  override suspend fun linkSelfToPlayer(playerId: String): Result<Unit> {
    val path = "players/$playerId"
    return try {
      val uid = requireUserId()
      val doc = playersCol.document(playerId).get().await()
      val player = FirestoreMapper.playerFromSnapshot(doc) ?: error("Player not found.")
      check(player.ownerUid == uid) {
        "Only the host who created ${player.name} can link their account here."
      }
      if (player.linkedUid == uid) return Result.success(Unit)
      check(player.linkedUid == null) {
        "${player.name} is already linked to another user account."
      }

      val existingSelf = playersCol.whereArrayContains("memberUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
        .firstOrNull { it.ownerUid == uid && it.linkedUid == uid && it.id != playerId }
      check(existingSelf == null) {
        "Your account is already linked to ${existingSelf?.name}. Unlink ${existingSelf?.name} first."
      }

      val label = currentUserDisplayName.trim()
        .ifBlank { currentUserEmail?.substringBefore("@") ?: "You" }
        .ifBlank { "You" }
        .take(80)

      playersCol.document(playerId).update(
        mapOf(
          "linkedUid" to uid,
          "linkedLabel" to label,
          "memberUids" to listOf(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(IllegalStateException(friendlyMessage(e), e))
    }
  }

  override suspend fun unlinkPlayer(playerId: String): Result<Unit> {
    val path = "players/$playerId"
    return try {
      val uid = requireUserId()
      val doc = playersCol.document(playerId).get().await()
      val player = FirestoreMapper.playerFromSnapshot(doc) ?: error("Player not found.")
      val previousLinkedUid = player.linkedUid ?: return Result.success(Unit)
      check(player.ownerUid == uid || previousLinkedUid == uid) {
        "You don't have permission to unlink ${player.name}."
      }

      playersCol.document(playerId).update(
        mapOf(
          "linkedUid" to FieldValue.delete(),
          "linkedLabel" to FieldValue.delete(),
          "memberUids" to listOf(player.ownerUid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()

      if (player.ownerUid == uid && previousLinkedUid != uid) {
        val allPlayers = playersCol.whereArrayContains("memberUids", uid).get().await()
          .documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
        val playersById = allPlayers.associateBy { it.id }

        val ownedSessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
          .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
          .filter { it.ownerUid == uid && previousLinkedUid in it.participantUids }

        val ownedGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
          .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
          .filter { it.ownerUid == uid && previousLinkedUid in it.participantUids }

        val updates = buildList<Pair<DocumentReference, Map<String, Any>>> {
          ownedSessions.forEach { s ->
            val updatedParticipants = FirestoreMapper.participantUidsFor(uid, s.playerIds, playersById)
            add(
              sessionsCol.document(s.id) to mapOf(
                "participantUids" to updatedParticipants,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
          ownedGames.forEach { g ->
            val updatedParticipants = FirestoreMapper.participantUidsFor(
              uid,
              FirestoreMapper.gamePlayerIds(g),
              playersById,
            )
            add(
              gamesCol.document(g.id) to mapOf(
                "participantUids" to updatedParticipants,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
        updates.chunked(400).forEach { chunk ->
          val batch = db.batch()
          chunk.forEach { (ref, payload) -> batch.update(ref, payload) }
          batch.commit().await()
        }
      }

      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(IllegalStateException(friendlyMessage(e), e))
    }
  }

  // ---- Sessions ----

  override suspend fun startSession(playerIds: List<String>, lineup: Lineup): Pair<Session, Game> {
    val uid = requireUserId()
    initializeUserCloudState()
    val path = "sessions"
    try {
      val existingSessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
        .filter { it.ownerUid == uid }
      check(existingSessions.none { it.status == SessionStatus.ACTIVE || it.status == SessionStatus.PAUSED }) {
        "A session is already active or paused. End it before starting a new one."
      }
      require(playerIds.size in 4..6 && playerIds.distinct().size == playerIds.size) {
        "Sessions need 4, 5 or 6 distinct players."
      }
      require(LineupGenerator.isValidLineupFor(playerIds, lineup)) {
        "Selected lineup does not match the chosen players."
      }

      val players = playersCol.whereArrayContains("memberUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
      val playersById = players.associateBy { it.id }
      val participantUids = FirestoreMapper.participantUidsFor(uid, playerIds, playersById)

      val sessionId = newId("s")
      val session = Session(
        id = sessionId,
        status = SessionStatus.ACTIVE,
        playerIds = playerIds,
        nextPoolStart = 0,
        pauseLog = emptyList(),
        startedAt = System.currentTimeMillis(),
        endedAt = null,
        ownerUid = uid,
        participantUids = participantUids,
      )
      sessionsCol.document(sessionId)
        .set(FirestoreMapper.sessionToCreateMap(session, uid, participantUids))
        .await()

      val game = startNewGameInSession(sessionId, lineup)
      return session to game
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun pauseSession(sessionId: String) {
    val uid = requireUserId()
    val path = "sessions/$sessionId"
    try {
      val session = FirestoreMapper.sessionFromSnapshot(sessionsCol.document(sessionId).get().await())
        ?: error("Session not found.")
      check(session.ownerUid == uid) { "Only the session host can pause this session." }
      check(session.status == SessionStatus.ACTIVE) { "Only an active session can be paused." }
      val updatedLog = session.pauseLog + PauseInterval(pausedAt = System.currentTimeMillis(), resumedAt = null)
      sessionsCol.document(sessionId).update(
        mapOf(
          "status" to SessionStatus.PAUSED.key,
          "pauseLog" to FirestoreMapper.pauseLogToMaps(updatedLog),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun resumeSession(sessionId: String) {
    val uid = requireUserId()
    val path = "sessions/$sessionId"
    try {
      val session = FirestoreMapper.sessionFromSnapshot(sessionsCol.document(sessionId).get().await())
        ?: error("Session not found.")
      check(session.ownerUid == uid) { "Only the session host can resume this session." }
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
      sessionsCol.document(sessionId).update(
        mapOf(
          "status" to SessionStatus.ACTIVE.key,
          "pauseLog" to FirestoreMapper.pauseLogToMaps(updatedLog),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun endSession(sessionId: String) {
    val uid = requireUserId()
    val path = "sessions/$sessionId"
    try {
      val userDoc = userSettingsRef.get().await()
      val currentId = FirestoreMapper.preferencesFromSnapshot(userDoc).currentSessionGameId
      val session = FirestoreMapper.sessionFromSnapshot(sessionsCol.document(sessionId).get().await())
        ?: error("Session not found.")
      check(session.ownerUid == uid) { "Only the session host can end this session." }

      if (currentId != null) {
        val gameDoc = gamesCol.document(currentId).get().await()
        val currentGame = FirestoreMapper.gameFromSnapshot(gameDoc)
        if (currentGame != null && currentGame.sessionId == sessionId && currentGame.winner == null) {
          gamesCol.document(currentGame.id).delete().await()
        }
      }
      val now = System.currentTimeMillis()
      val closedLog = session.pauseLog.map { entry ->
        if (entry.resumedAt == null) entry.copy(resumedAt = now) else entry
      }
      sessionsCol.document(sessionId).update(
        mapOf(
          "status" to SessionStatus.ENDED.key,
          "endedAtMs" to now,
          "pauseLog" to FirestoreMapper.pauseLogToMaps(closedLog),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      if (currentId != null) {
        clearCurrentSessionGamePointer(uid)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun startNewGameInSession(sessionId: String, requestedLineup: Lineup?): Game {
    val uid = requireUserId()
    initializeUserCloudState()
    var path = "sessions/$sessionId"
    try {
      val userDoc = userSettingsRef.get().await()
      val prefs = FirestoreMapper.preferencesFromSnapshot(userDoc, preferencesStore.getSnapshot())
      var session = FirestoreMapper.sessionFromSnapshot(sessionsCol.document(sessionId).get().await())
        ?: error("Session not found.")
      check(session.ownerUid == uid) { "Only the session host can start games in this session." }

      val players = playersCol.whereArrayContains("memberUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
      val playersById = players.associateBy { it.id }
      val participantUids = FirestoreMapper.participantUidsFor(uid, session.playerIds, playersById)

      val sessionUpdates = mutableMapOf<String, Any>()
      if (session.status == SessionStatus.PAUSED) {
        val now = System.currentTimeMillis()
        val resumedLog = session.pauseLog.map { if (it.resumedAt == null) it.copy(resumedAt = now) else it }
        session = session.copy(status = SessionStatus.ACTIVE, pauseLog = resumedLog)
        sessionUpdates["status"] = SessionStatus.ACTIVE.key
        sessionUpdates["pauseLog"] = FirestoreMapper.pauseLogToMaps(resumedLog)
      }
      check(session.status == SessionStatus.ACTIVE) { "This session cannot start a new game." }

      val allGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
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
        val nextPool = ((session.nextPoolStart % 3) + 3 + 1) % 3
        session = session.copy(nextPoolStart = nextPool)
        sessionUpdates["nextPoolStart"] = nextPool
      }
      if (participantUids != session.participantUids) {
        sessionUpdates["participantUids"] = participantUids
      }
      if (sessionUpdates.isNotEmpty()) {
        sessionUpdates["updatedAt"] = FieldValue.serverTimestamp()
        sessionsCol.document(sessionId).update(sessionUpdates).await()
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
        ownerUid = uid,
        participantUids = participantUids,
      )

      val dealer = DealerSelector.pickDealerForRound(
        game = newGame,
        lineup = LineupGenerator.getRoundLineup(newGame, 1),
        prevDealerId = DealerSelector.lastRecordedDealer(sessionGames),
        counts = DealerSelector.sessionDealCounts(session, sessionGames),
        history = DealerSelector.sessionDealerHistory(sessionGames),
      )
      newGame = newGame.copy(nextDealerPlayerId = dealer)

      path = "games/$gameId"
      gamesCol.document(gameId)
        .set(FirestoreMapper.gameToCreateMap(newGame, uid, participantUids))
        .await()
      userSettingsRef.update(
        mapOf(
          "currentSessionGameId" to gameId,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      return newGame
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun finishSessionGame(
    gameId: String,
    winner: String,
    finalScores: FinalScores,
    clearCurrentIfNotThreeTeam: Boolean,
  ): Game {
    val uid = requireUserId()
    val path = "games/$gameId"
    try {
      val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(gameId).get().await())
        ?: error("Game not found.")
      check(game.ownerUid == uid) { "Only the session host can finish this game." }
      val isThreeTeam = game.mode == GameMode.THREE_TEAMS
      val endedAt = System.currentTimeMillis()
      val finalWinner = winner.ifBlank { "tie" }
      val next = game.copy(
        winner = finalWinner,
        finalScores = finalScores,
        endedAt = endedAt,
        pendingNextGame = isThreeTeam,
      )
      gamesCol.document(gameId).update(
        mapOf(
          "winner" to finalWinner,
          "finalScores" to FirestoreMapper.finalScoresToMap(finalScores),
          "endedAtMs" to endedAt,
          "pendingNextGame" to isThreeTeam,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()

      val currentSessionGameId = FirestoreMapper.preferencesFromSnapshot(userSettingsRef.get().await())
        .currentSessionGameId
      if (clearCurrentIfNotThreeTeam && !next.pendingNextGame && currentSessionGameId == gameId) {
        clearCurrentSessionGamePointer(uid)
      }
      return next
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun deleteGame(gameId: String) {
    val uid = requireUserId()
    val path = "games/$gameId"
    try {
      val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(gameId).get().await())
        ?: error("Game not found.")
      check(game.ownerUid == uid) { "Only the host can delete this game." }
      val currentId = FirestoreMapper.preferencesFromSnapshot(userSettingsRef.get().await()).currentSessionGameId
      gamesCol.document(gameId).delete().await()
      if (currentId == gameId) {
        clearCurrentSessionGamePointer(uid)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun deleteSession(sessionId: String) {
    val uid = requireUserId()
    val path = "sessions/$sessionId"
    try {
      val session = FirestoreMapper.sessionFromSnapshot(sessionsCol.document(sessionId).get().await())
        ?: error("Session not found.")
      check(session.ownerUid == uid) { "Only the host can delete this session." }
      val currentId = FirestoreMapper.preferencesFromSnapshot(userSettingsRef.get().await()).currentSessionGameId
      val sessionGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
        .filter { it.sessionId == sessionId && it.ownerUid == uid }
      val clearsCurrent = sessionGames.any { it.id == currentId }

      val refsToDelete = sessionGames.map { gamesCol.document(it.id) } + sessionsCol.document(sessionId)
      commitDeleteChunks(refsToDelete)
      if (clearsCurrent) {
        clearCurrentSessionGamePointer(uid)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  // ---- Scoring (Quick Game + Session Game) ----

  override suspend fun ensureQuickGame(): Game {
    val uid = requireUserId()
    initializeUserCloudState()
    var path = "games"
    try {
      val userDoc = userSettingsRef.get().await()
      val prefs = FirestoreMapper.preferencesFromSnapshot(userDoc, preferencesStore.getSnapshot())
      val existingId = prefs.quickGameId
      if (existingId != null) {
        val found = FirestoreMapper.gameFromSnapshot(gamesCol.document(existingId).get().await())
        if (found != null && found.sessionId == null && found.ownerUid == uid) return found
      }
      val ownedGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
      val fallback = ownedGames.lastOrNull {
        it.sessionId == null && it.mode == GameMode.QUICK && it.ownerUid == uid
      }
      if (fallback != null) {
        userSettingsRef.update(
          mapOf(
            "quickGameId" to fallback.id,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
        return fallback
      }

      val gameId = newId("q")
      path = "games/$gameId"
      val created = Game(
        id = gameId,
        sessionId = null,
        mode = GameMode.QUICK,
        winThreshold = prefs.winThreshold,
        teamAName = prefs.quickTeamAName.ifBlank { "Team A" }.take(60),
        teamBName = prefs.quickTeamBName.ifBlank { "Team B" }.take(60),
        startedAt = System.currentTimeMillis(),
        ownerUid = uid,
        participantUids = listOf(uid),
      )
      gamesCol.document(gameId).set(FirestoreMapper.gameToCreateMap(created, uid, listOf(uid))).await()
      userSettingsRef.update(
        mapOf(
          "quickGameId" to gameId,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      return created
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun updateQuickTeamName(isTeamA: Boolean, rawName: String) {
    val trimmed = rawName.trim().take(60)
    if (trimmed.isEmpty()) return
    val uid = requireUserId()
    val key = if (isTeamA) "quickTeamAName" else "quickTeamBName"
    val gameField = if (isTeamA) "teamAName" else "teamBName"
    try {
      preferencesStore.setQuickTeamName(isTeamA, trimmed)
      val quick = ensureQuickGame()
      userSettingsRef.update(
        mapOf(
          key to trimmed,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      if (quick.ownerUid == uid) {
        gamesCol.document(quick.id).update(
          mapOf(
            gameField to trimmed,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
    }
  }

  override suspend fun recordRound(gameId: String, draft: ScoreboardDraft, threshold: Int): Game {
    val uid = requireUserId()
    val path = "games/$gameId"
    try {
      val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(gameId).get().await())
        ?: error("Game not found.")
      check(game.ownerUid == uid) { "Only the host can record rounds in this game." }
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
        val session = FirestoreMapper.sessionFromSnapshot(sessionsCol.document(game.sessionId).get().await())
        if (session != null) {
          val sessionGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
            .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
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

      val updateMap = mutableMapOf<String, Any>(
        "rounds" to FirestoreMapper.roundsToMaps(updatedGame.rounds),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      updatedGame.nextDealerPlayerId?.let { updateMap["nextDealerPlayerId"] = it }
      if (updatedGame.winner != null && updatedGame.finalScores != null) {
        updateMap["winner"] = updatedGame.winner!!
        updateMap["finalScores"] = FirestoreMapper.finalScoresToMap(updatedGame.finalScores!!)
        updateMap["pendingNextGame"] = updatedGame.pendingNextGame
        updateMap["endedAtMs"] = updatedGame.endedAt ?: System.currentTimeMillis()
      }

      gamesCol.document(game.id).update(updateMap).await()
      return updatedGame
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun undoLastRound(gameId: String): Game {
    val uid = requireUserId()
    val path = "games/$gameId"
    try {
      val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(gameId).get().await())
        ?: error("Game not found.")
      check(game.ownerUid == uid) { "Only the host can undo rounds in this game." }
      val popped = game.rounds.lastOrNull() ?: return game
      val remainingRounds = game.rounds.dropLast(1)

      val updateMap = mutableMapOf<String, Any>(
        "rounds" to FirestoreMapper.roundsToMaps(remainingRounds),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      var updated = game.copy(rounds = remainingRounds)
      if (game.mode == GameMode.THREE_TEAMS && game.winner != null) {
        updateMap["winner"] = FieldValue.delete()
        updateMap["finalScores"] = FieldValue.delete()
        updateMap["endedAtMs"] = FieldValue.delete()
        updateMap["pendingNextGame"] = false
        updated = updated.copy(winner = null, finalScores = null, endedAt = null, pendingNextGame = false)
      }
      if (popped.dealerPlayerId != null) {
        updateMap["nextDealerPlayerId"] = popped.dealerPlayerId
        updated = updated.copy(nextDealerPlayerId = popped.dealerPlayerId)
      }

      gamesCol.document(gameId).update(updateMap).await()
      return updated
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun resetGameRounds(gameId: String, firstDealerId: String?): Game {
    val uid = requireUserId()
    val path = "games/$gameId"
    try {
      val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(gameId).get().await())
        ?: error("Game not found.")
      check(game.ownerUid == uid) { "Only the host can reset this game." }
      val dealer = firstDealerId ?: game.rounds.firstOrNull()?.dealerPlayerId ?: game.nextDealerPlayerId
      val updateMap = mutableMapOf<String, Any>(
        "rounds" to emptyList<Map<String, Any>>(),
        "winner" to FieldValue.delete(),
        "finalScores" to FieldValue.delete(),
        "endedAtMs" to FieldValue.delete(),
        "pendingNextGame" to false,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (dealer != null) {
        updateMap["nextDealerPlayerId"] = dealer
      }
      gamesCol.document(gameId).update(updateMap).await()
      return game.copy(
        rounds = emptyList(),
        winner = null,
        finalScores = null,
        endedAt = null,
        pendingNextGame = false,
        nextDealerPlayerId = dealer,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun restoreGameSnapshot(
    gameId: String,
    rounds: List<RoundRecord>,
    nextDealerPlayerId: String?,
  ): Game {
    val uid = requireUserId()
    val path = "games/$gameId"
    try {
      val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(gameId).get().await())
        ?: error("Game not found.")
      check(game.ownerUid == uid) { "Only the host can restore this game." }
      val restoredRounds = rounds.map { it.copy(gameId = gameId) }
      val updateMap = mutableMapOf<String, Any>(
        "rounds" to FirestoreMapper.roundsToMaps(restoredRounds),
        "winner" to FieldValue.delete(),
        "finalScores" to FieldValue.delete(),
        "endedAtMs" to FieldValue.delete(),
        "pendingNextGame" to false,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (nextDealerPlayerId != null) {
        updateMap["nextDealerPlayerId"] = nextDealerPlayerId
      } else {
        updateMap["nextDealerPlayerId"] = FieldValue.delete()
      }
      gamesCol.document(gameId).update(updateMap).await()
      return game.copy(
        rounds = restoredRounds,
        winner = null,
        finalScores = null,
        endedAt = null,
        pendingNextGame = false,
        nextDealerPlayerId = nextDealerPlayerId,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  // ---- Preferences & Settings ----

  override suspend fun setThemeMode(mode: ThemeMode) {
    preferencesStore.setThemeMode(mode)
    updateUserField("themeMode", mode.key)
  }

  override suspend fun setChipsVisible(visible: Boolean) {
    preferencesStore.setChipsVisible(visible)
    updateUserField("chipsVisible", visible)
  }

  override suspend fun dismissModeHint() {
    preferencesStore.setModeHintDismissed(true)
    updateUserField("modeHintDismissed", true)
  }

  override suspend fun setPlayersRankingSort(metric: RankingMetric) {
    preferencesStore.setPlayersRankingSort(metric)
    updateUserField("playersRankingSort", metric.id)
  }

  override suspend fun setSessionRankingSort(metric: RankingMetric) {
    preferencesStore.setSessionRankingSort(metric)
    updateUserField("sessionRankingSort", metric.id)
  }

  private suspend fun updateUserField(key: String, value: Any) {
    val uid = requireUserId()
    val path = "users/$uid"
    try {
      initializeUserCloudState()
      userSettingsRef.update(
        mapOf(
          key to value,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
    }
  }

  private suspend fun clearCurrentSessionGamePointer(uid: String) {
    userSettingsRef.update(
      mapOf(
        "currentSessionGameId" to FieldValue.delete(),
        "updatedAt" to FieldValue.serverTimestamp(),
      ),
    ).await()
  }

  override suspend fun setWinThreshold(newThreshold: Int): Boolean {
    require(newThreshold in 1..100000) { "Target must be between 1 and 100,000 points." }
    val uid = requireUserId()
    initializeUserCloudState()
    preferencesStore.setWinThreshold(newThreshold)
    var threeTeamWinnerTriggered = false
    val path = "users/$uid"
    try {
      val prefs = FirestoreMapper.preferencesFromSnapshot(userSettingsRef.get().await(), preferencesStore.getSnapshot())
      userSettingsRef.update(
        mapOf(
          "winThreshold" to newThreshold,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()

      val sessionGameId = prefs.currentSessionGameId
      if (sessionGameId != null) {
        val game = FirestoreMapper.gameFromSnapshot(gamesCol.document(sessionGameId).get().await())
        if (game != null && game.ownerUid == uid && game.winner == null) {
          if (game.mode == GameMode.THREE_TEAMS && game.rounds.isNotEmpty()) {
            val latest = game.rounds.last().scores
            val winner = ScoringEngine.evaluateThreeTeamWinner(latest, newThreshold)
            if (winner != null) {
              threeTeamWinnerTriggered = true
              gamesCol.document(game.id).update(
                mapOf(
                  "winThreshold" to newThreshold,
                  "winner" to winner,
                  "pendingNextGame" to true,
                  "endedAtMs" to System.currentTimeMillis(),
                  "finalScores" to FirestoreMapper.finalScoresToMap(FinalScores(latest[0], latest[1], latest[2])),
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              ).await()
            } else {
              gamesCol.document(game.id).update(
                mapOf(
                  "winThreshold" to newThreshold,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              ).await()
            }
          } else {
            gamesCol.document(game.id).update(
              mapOf(
                "winThreshold" to newThreshold,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            ).await()
          }
        }
      }

      val quickId = prefs.quickGameId
      if (quickId != null) {
        val quick = FirestoreMapper.gameFromSnapshot(gamesCol.document(quickId).get().await())
        if (quick != null && quick.ownerUid == uid) {
          gamesCol.document(quick.id).update(
            mapOf(
              "winThreshold" to newThreshold,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
        }
      }
      return threeTeamWinnerTriggered
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  // ---- JSON Backup Export / Import ----

  override suspend fun exportBackupJson(): String {
    val uid = requireUserId()
    initializeUserCloudState()
    return try {
      val prefs = FirestoreMapper.preferencesFromSnapshot(userSettingsRef.get().await(), preferencesStore.getSnapshot())
      val players = playersCol.whereArrayContains("memberUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
        .filter { it.ownerUid == uid }
      val sessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
        .filter { it.ownerUid == uid }
      val games = gamesCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
        .filter { it.ownerUid == uid }
      BackupSerializer.encode(
        preferences = prefs,
        players = players,
        sessions = sessions,
        games = games,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users/$uid")
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  override suspend fun importBackupJson(json: String) {
    val decoded = BackupSerializer.decodeWithPreview(json).decoded
    importDecodedBackup(decoded)
  }

  override suspend fun importDecodedBackup(decoded: DecodedBackup) {
    val uid = requireUserId()
    initializeUserCloudState()
    try {
      val oldPlayers = playersCol.whereArrayContains("memberUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.playerFromSnapshot(it) }
        .filter { it.ownerUid == uid }
      val oldSessions = sessionsCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.sessionFromSnapshot(it) }
        .filter { it.ownerUid == uid }
      val oldGames = gamesCol.whereArrayContains("participantUids", uid).get().await()
        .documents.mapNotNull { FirestoreMapper.gameFromSnapshot(it) }
        .filter { it.ownerUid == uid }

      val deleteRefs = oldGames.map { gamesCol.document(it.id) } +
        oldSessions.map { sessionsCol.document(it.id) } +
        oldPlayers.map { playersCol.document(it.id) }
      commitDeleteChunks(deleteRefs)

      val idMap = buildMap {
        decoded.players.forEach { put(it.id, newId("p")) }
        decoded.sessions.forEach { put(it.id, newId("s")) }
        decoded.games.forEach { put(it.id, newId("g")) }
      }

      val newPlayers = decoded.players.map { it.copy(id = idMap.getValue(it.id), ownerUid = uid, linkedUid = null, linkedLabel = null) }
      val newSessions = decoded.sessions.map {
        it.copy(
          id = idMap.getValue(it.id),
          playerIds = it.playerIds.map { pid -> idMap[pid] ?: pid },
          ownerUid = uid,
          participantUids = listOf(uid),
        )
      }
      val newGames = decoded.games.map { remapGameIds(it, idMap, uid) }

      val writes = buildList<Pair<DocumentReference, Map<String, Any>>> {
        newPlayers.forEach { add(playersCol.document(it.id) to FirestoreMapper.playerToCreateMap(it, uid)) }
        newSessions.forEach { add(sessionsCol.document(it.id) to FirestoreMapper.sessionToCreateMap(it, uid, listOf(uid))) }
        newGames.forEach { add(gamesCol.document(it.id) to FirestoreMapper.gameToCreateMap(it, uid, listOf(uid))) }
      }
      commitSetChunks(writes)

      val updatedPrefs = decoded.preferences.copy(
        currentSessionGameId = decoded.preferences.currentSessionGameId?.let { idMap[it] },
        quickGameId = decoded.preferences.quickGameId?.let { idMap[it] },
      )
      preferencesStore.applyBackupPreferences(updatedPrefs)

      val userUpdates = mutableMapOf<String, Any>(
        "themeMode" to updatedPrefs.themeMode.key,
        "winThreshold" to updatedPrefs.winThreshold.coerceIn(1, 100000),
        "chipsVisible" to updatedPrefs.chipsVisible,
        "modeHintDismissed" to updatedPrefs.modeHintDismissed,
        "playersRankingSort" to updatedPrefs.playersRankingSort.id.take(40),
        "sessionRankingSort" to updatedPrefs.sessionRankingSort.id.take(40),
        "quickTeamAName" to updatedPrefs.quickTeamAName.trim().ifBlank { "Team A" }.take(60),
        "quickTeamBName" to updatedPrefs.quickTeamBName.trim().ifBlank { "Team B" }.take(60),
        "currentSessionGameId" to (updatedPrefs.currentSessionGameId ?: FieldValue.delete()),
        "quickGameId" to (updatedPrefs.quickGameId ?: FieldValue.delete()),
        "localMigratedAt" to System.currentTimeMillis(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      userSettingsRef.update(userUpdates).await()

      if (updatedPrefs.quickGameId == null) {
        ensureQuickGame()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$uid")
      throw IllegalStateException(friendlyMessage(e), e)
    }
  }

  private fun friendlyMessage(e: Throwable): String {
    val fs = generateSequence(e) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    return when (fs?.code) {
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "Action not permitted. Check that you are signed in and own this game or player."
      FirebaseFirestoreException.Code.UNAVAILABLE ->
        "Network unavailable. Changes will sync once back online."
      else -> e.message ?: "Cloud sync operation failed."
    }
  }
}

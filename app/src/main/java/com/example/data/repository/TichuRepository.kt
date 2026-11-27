package com.example.data.repository

import com.example.data.backup.DecodedBackup
import com.example.data.preferences.AppPreferences
import com.example.domain.FinalScores
import com.example.domain.Game
import com.example.domain.Lineup
import com.example.domain.Player
import com.example.domain.PlayerInvite
import com.example.domain.RankingMetric
import com.example.domain.RoundRecord
import com.example.domain.ScoreboardDraft
import com.example.domain.Session
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PendingGuestData(
  val playerCount: Int,
  val sessionCount: Int,
  val gameCount: Int,
)

interface TichuRepository {
  val isGuestMode: Boolean
  val currentUserId: String
  val currentUserDisplayName: String
  val currentUserEmail: String?

  val streamErrorFlow: StateFlow<String?>
  fun clearStreamError()

  val preferencesFlow: Flow<AppPreferences>
  val playersFlow: Flow<List<Player>>
  val sessionsFlow: Flow<List<Session>>
  val gamesFlow: Flow<List<Game>>
  val activeOrPausedSessionFlow: Flow<Session?>
  val currentSessionGameFlow: Flow<Game?>
  val activeQuickGameFlow: Flow<Game?>

  fun close()

  // Players & account linking
  suspend fun addPlayer(rawName: String): Player
  suspend fun renamePlayer(id: String, rawName: String)
  suspend fun setPlayerActive(id: String, active: Boolean)
  suspend fun resetPlayerStatistics(id: String)
  suspend fun deleteArchivedPlayer(id: String)
  suspend fun createPlayerInvite(playerId: String): Result<PlayerInvite>
  suspend fun claimPlayerInvite(rawCode: String): Result<String>
  suspend fun linkSelfToPlayer(playerId: String): Result<Unit>
  suspend fun unlinkPlayer(playerId: String): Result<Unit>

  // Sessions
  suspend fun startSession(playerIds: List<String>, lineup: Lineup): Pair<Session, Game>
  suspend fun pauseSession(sessionId: String)
  suspend fun resumeSession(sessionId: String)
  suspend fun endSession(sessionId: String)
  suspend fun startNewGameInSession(sessionId: String, requestedLineup: Lineup? = null): Game
  suspend fun finishSessionGame(
    gameId: String,
    winner: String,
    finalScores: FinalScores,
    clearCurrentIfNotThreeTeam: Boolean = true,
  ): Game
  suspend fun deleteGame(gameId: String)
  suspend fun deleteSession(sessionId: String)

  // Scoring
  suspend fun ensureQuickGame(): Game
  suspend fun updateQuickTeamName(isTeamA: Boolean, rawName: String)
  suspend fun recordRound(gameId: String, draft: ScoreboardDraft, threshold: Int): Game
  suspend fun undoLastRound(gameId: String): Game
  suspend fun resetGameRounds(gameId: String, firstDealerId: String? = null): Game
  suspend fun restoreGameSnapshot(gameId: String, rounds: List<RoundRecord>, nextDealerPlayerId: String?): Game

  // Preferences & Settings
  suspend fun setThemeMode(mode: ThemeMode)
  suspend fun setChipsVisible(visible: Boolean)
  suspend fun dismissModeHint()
  suspend fun setPlayersRankingSort(metric: RankingMetric)
  suspend fun setSessionRankingSort(metric: RankingMetric)
  suspend fun setWinThreshold(newThreshold: Int): Boolean

  // Backup Export / Import
  suspend fun exportBackupJson(): String
  suspend fun importBackupJson(json: String)
  suspend fun importDecodedBackup(decoded: DecodedBackup)
}

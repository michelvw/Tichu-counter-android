package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.RankingMetric
import com.example.domain.ScoringEngine
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tichuDataStore: DataStore<Preferences> by preferencesDataStore(name = "tichu_prefs")

data class AppPreferences(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val winThreshold: Int = ScoringEngine.DEFAULT_WIN_THRESHOLD,
  val chipsVisible: Boolean = true,
  val modeHintDismissed: Boolean = false,
  val playersRankingSort: RankingMetric = RankingMetric.TOTAL_POINTS,
  val sessionRankingSort: RankingMetric = RankingMetric.TOTAL_POINTS,
  val currentSessionGameId: String? = null,
  val quickGameId: String? = null,
  val quickTeamAName: String = "Team A",
  val quickTeamBName: String = "Team B",
)

class PreferencesStore(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val WIN_THRESHOLD = intPreferencesKey("win_threshold")
    val CHIPS_VISIBLE = booleanPreferencesKey("chips_visible")
    val MODE_HINT_DISMISSED = booleanPreferencesKey("mode_hint_dismissed")
    val PLAYERS_RANKING_SORT = stringPreferencesKey("players_ranking_sort")
    val SESSION_RANKING_SORT = stringPreferencesKey("session_ranking_sort")
    val CURRENT_SESSION_GAME_ID = stringPreferencesKey("current_session_game_id")
    val QUICK_GAME_ID = stringPreferencesKey("quick_game_id")
    val QUICK_TEAM_A_NAME = stringPreferencesKey("quick_team_a_name")
    val QUICK_TEAM_B_NAME = stringPreferencesKey("quick_team_b_name")
    val GUEST_MODE_ACTIVE = booleanPreferencesKey("guest_mode_active")
  }

  val guestModeFlow: Flow<Boolean> = context.tichuDataStore.data.map { prefs ->
    prefs[Keys.GUEST_MODE_ACTIVE] ?: false
  }

  val preferencesFlow: Flow<AppPreferences> = context.tichuDataStore.data.map { prefs ->
    AppPreferences(
      themeMode = ThemeMode.fromKey(prefs[Keys.THEME_MODE]),
      winThreshold = prefs[Keys.WIN_THRESHOLD] ?: ScoringEngine.DEFAULT_WIN_THRESHOLD,
      chipsVisible = prefs[Keys.CHIPS_VISIBLE] ?: true,
      modeHintDismissed = prefs[Keys.MODE_HINT_DISMISSED] ?: false,
      playersRankingSort = RankingMetric.fromId(prefs[Keys.PLAYERS_RANKING_SORT]),
      sessionRankingSort = RankingMetric.fromId(prefs[Keys.SESSION_RANKING_SORT]),
      currentSessionGameId = prefs[Keys.CURRENT_SESSION_GAME_ID],
      quickGameId = prefs[Keys.QUICK_GAME_ID],
      quickTeamAName = prefs[Keys.QUICK_TEAM_A_NAME] ?: "Team A",
      quickTeamBName = prefs[Keys.QUICK_TEAM_B_NAME] ?: "Team B",
    )
  }

  suspend fun getSnapshot(): AppPreferences = preferencesFlow.first()

  suspend fun setGuestModeActive(active: Boolean) {
    context.tichuDataStore.edit { it[Keys.GUEST_MODE_ACTIVE] = active }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.tichuDataStore.edit { it[Keys.THEME_MODE] = mode.key }
  }

  suspend fun setWinThreshold(value: Int) {
    context.tichuDataStore.edit { it[Keys.WIN_THRESHOLD] = value }
  }

  suspend fun setChipsVisible(visible: Boolean) {
    context.tichuDataStore.edit { it[Keys.CHIPS_VISIBLE] = visible }
  }

  suspend fun setModeHintDismissed(dismissed: Boolean) {
    context.tichuDataStore.edit { it[Keys.MODE_HINT_DISMISSED] = dismissed }
  }

  suspend fun setPlayersRankingSort(metric: RankingMetric) {
    context.tichuDataStore.edit { it[Keys.PLAYERS_RANKING_SORT] = metric.id }
  }

  suspend fun setSessionRankingSort(metric: RankingMetric) {
    context.tichuDataStore.edit { it[Keys.SESSION_RANKING_SORT] = metric.id }
  }

  suspend fun setCurrentSessionGameId(id: String?) {
    context.tichuDataStore.edit {
      if (id == null) it.remove(Keys.CURRENT_SESSION_GAME_ID)
      else it[Keys.CURRENT_SESSION_GAME_ID] = id
    }
  }

  suspend fun setQuickGameId(id: String?) {
    context.tichuDataStore.edit {
      if (id == null) it.remove(Keys.QUICK_GAME_ID)
      else it[Keys.QUICK_GAME_ID] = id
    }
  }

  suspend fun setQuickTeamName(isTeamA: Boolean, name: String) {
    context.tichuDataStore.edit {
      val key = if (isTeamA) Keys.QUICK_TEAM_A_NAME else Keys.QUICK_TEAM_B_NAME
      it[key] = name
    }
  }

  suspend fun applyBackupPreferences(prefs: AppPreferences) {
    context.tichuDataStore.edit {
      it[Keys.THEME_MODE] = prefs.themeMode.key
      it[Keys.WIN_THRESHOLD] = prefs.winThreshold
      it[Keys.CHIPS_VISIBLE] = prefs.chipsVisible
      it[Keys.MODE_HINT_DISMISSED] = prefs.modeHintDismissed
      it[Keys.PLAYERS_RANKING_SORT] = prefs.playersRankingSort.id
      it[Keys.SESSION_RANKING_SORT] = prefs.sessionRankingSort.id
      if (prefs.currentSessionGameId == null) it.remove(Keys.CURRENT_SESSION_GAME_ID)
      else it[Keys.CURRENT_SESSION_GAME_ID] = prefs.currentSessionGameId
      if (prefs.quickGameId == null) it.remove(Keys.QUICK_GAME_ID)
      else it[Keys.QUICK_GAME_ID] = prefs.quickGameId
      it[Keys.QUICK_TEAM_A_NAME] = prefs.quickTeamAName
      it[Keys.QUICK_TEAM_B_NAME] = prefs.quickTeamBName
    }
  }
}

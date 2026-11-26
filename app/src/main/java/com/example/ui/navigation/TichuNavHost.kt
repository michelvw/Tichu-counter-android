package com.example.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.TichuRepository
import com.example.ui.screens.PlayerStatsScreen
import com.example.ui.screens.PlayersScreen
import com.example.ui.screens.RoundScoresScreen
import com.example.ui.screens.ScoreboardScreen
import com.example.ui.screens.SessionDetailsScreen
import com.example.ui.screens.SessionHistoryScreen
import com.example.ui.screens.SessionsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.AppViewModelFactory
import com.example.ui.viewmodel.PlayersViewModel
import com.example.ui.viewmodel.RoundScoresViewModel
import com.example.ui.viewmodel.ScoreboardViewModel
import com.example.ui.viewmodel.SessionsViewModel
import com.example.ui.viewmodel.SettingsViewModel

object Routes {
  const val SCOREBOARD = "scoreboard"
  const val SESSIONS = "sessions"
  const val PLAYERS = "players"
  const val ROUND_SCORES_LIVE = "round_scores"
  const val ROUND_SCORES_GAME = "round_scores/{gameId}"
  const val SETTINGS = "settings"
  const val SESSION_DETAILS = "session_details"
  const val SESSION_HISTORY = "session_history"
  const val PLAYER_STATS = "player_stats/{playerId}"

  fun roundScoresForGame(gameId: String): String = "round_scores/${Uri.encode(gameId)}"
  fun playerStats(playerId: String): String = "player_stats/${Uri.encode(playerId)}"
}

private data class TopLevelTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String,
)

private val TOP_LEVEL_TABS = listOf(
  TopLevelTab(
    route = Routes.SCOREBOARD,
    label = "Scoreboard",
    selectedIcon = Icons.Rounded.Casino,
    unselectedIcon = Icons.Outlined.Casino,
    testTag = "bottom_nav_scoreboard",
  ),
  TopLevelTab(
    route = Routes.SESSIONS,
    label = "Sessions",
    selectedIcon = Icons.Rounded.Groups,
    unselectedIcon = Icons.Outlined.Groups,
    testTag = "bottom_nav_sessions",
  ),
  TopLevelTab(
    route = Routes.PLAYERS,
    label = "Players",
    selectedIcon = Icons.Rounded.Person,
    unselectedIcon = Icons.Outlined.Person,
    testTag = "bottom_nav_players",
  ),
)

@Composable
fun TichuAppRoot(
  repository: TichuRepository,
  navController: NavHostController = rememberNavController(),
) {
  val colors = TichuTheme.colors
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  val currentRoute = currentDestination?.route
  val showTopLevelNav = currentRoute in setOf(Routes.SCOREBOARD, Routes.SESSIONS, Routes.PLAYERS)

  fun navigateToTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useNavRail = maxWidth >= 840.dp

    Scaffold(
      containerColor = colors.bg,
      contentWindowInsets = if (showTopLevelNav) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
      bottomBar = {
        if (showTopLevelNav && !useNavRail) {
          NavigationBar(
            containerColor = colors.surface,
            contentColor = colors.text,
            windowInsets = WindowInsets.navigationBars,
          ) {
            TOP_LEVEL_TABS.forEach { tab ->
              val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = colors.brand,
                  selectedTextColor = colors.brand,
                  indicatorColor = colors.brand.copy(alpha = 0.14f),
                  unselectedIconColor = colors.textMuted,
                  unselectedTextColor = colors.textMuted,
                ),
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding),
      ) {
        if (showTopLevelNav && useNavRail) {
          NavigationRail(
            containerColor = colors.surface,
            contentColor = colors.text,
          ) {
            TOP_LEVEL_TABS.forEach { tab ->
              val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(
                  selectedIconColor = colors.brand,
                  selectedTextColor = colors.brand,
                  indicatorColor = colors.brand.copy(alpha = 0.14f),
                  unselectedIconColor = colors.textMuted,
                  unselectedTextColor = colors.textMuted,
                ),
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        NavHost(
          navController = navController,
          startDestination = Routes.SCOREBOARD,
          modifier = Modifier.weight(1f),
        ) {
          composable(Routes.SCOREBOARD) {
            val vm: ScoreboardViewModel = viewModel(factory = AppViewModelFactory(repository))
            ScoreboardScreen(
              viewModel = vm,
              onOpenRoundScores = { navController.navigate(Routes.ROUND_SCORES_LIVE) },
              onOpenSettings = { navController.navigate(Routes.SETTINGS) },
              onOpenSessionsTab = { navigateToTab(Routes.SESSIONS) },
            )
          }

          composable(Routes.SESSIONS) {
            val vm: SessionsViewModel = viewModel(factory = AppViewModelFactory(repository))
            SessionsScreen(
              viewModel = vm,
              onNavigateToScoreboard = { navigateToTab(Routes.SCOREBOARD) },
              onOpenSessionDetails = { navController.navigate(Routes.SESSION_DETAILS) },
              onOpenSessionHistory = { navController.navigate(Routes.SESSION_HISTORY) },
              onGoToPlayersTab = { navigateToTab(Routes.PLAYERS) },
            )
          }

          composable(Routes.PLAYERS) {
            val vm: PlayersViewModel = viewModel(factory = AppViewModelFactory(repository))
            PlayersScreen(
              viewModel = vm,
              onOpenPlayerStats = { playerId ->
                navController.navigate(Routes.playerStats(playerId))
              },
            )
          }

          composable(Routes.ROUND_SCORES_LIVE) {
            val vm: RoundScoresViewModel = viewModel(factory = AppViewModelFactory(repository, null))
            RoundScoresScreen(
              viewModel = vm,
              onBack = { navController.popBackStack() },
            )
          }

          composable(
            route = Routes.ROUND_SCORES_GAME,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
          ) { entry ->
            val gameId = entry.arguments?.getString("gameId")
            val vm: RoundScoresViewModel = viewModel(factory = AppViewModelFactory(repository, gameId))
            RoundScoresScreen(
              viewModel = vm,
              onBack = { navController.popBackStack() },
            )
          }

          composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory(repository))
            SettingsScreen(
              viewModel = vm,
              onBack = { navController.popBackStack() },
            )
          }

          composable(Routes.SESSION_DETAILS) {
            val vm: SessionsViewModel = viewModel(factory = AppViewModelFactory(repository))
            SessionDetailsScreen(
              viewModel = vm,
              onBack = { navController.popBackStack() },
              onOpenGameRounds = { gameId ->
                navController.navigate(Routes.roundScoresForGame(gameId))
              },
              onOpenPlayerStats = { playerId ->
                navController.navigate(Routes.playerStats(playerId))
              },
            )
          }

          composable(Routes.SESSION_HISTORY) {
            val vm: SessionsViewModel = viewModel(factory = AppViewModelFactory(repository))
            SessionHistoryScreen(
              viewModel = vm,
              onBack = { navController.popBackStack() },
              onOpenGameRounds = { gameId ->
                navController.navigate(Routes.roundScoresForGame(gameId))
              },
              onOpenPlayerStats = { playerId ->
                navController.navigate(Routes.playerStats(playerId))
              },
            )
          }

          composable(
            route = Routes.PLAYER_STATS,
            arguments = listOf(navArgument("playerId") { type = NavType.StringType }),
          ) { entry ->
            val playerId = entry.arguments?.getString("playerId")
            val vm: PlayersViewModel = viewModel(factory = AppViewModelFactory(repository, playerId))
            PlayerStatsScreen(
              viewModel = vm,
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}

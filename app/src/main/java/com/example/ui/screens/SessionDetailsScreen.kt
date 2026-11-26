package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RankingLeaderboardCard
import com.example.ui.components.TichuTopBar
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.SessionGameRowItem
import com.example.ui.viewmodel.SessionsViewModel

@Composable
fun SessionDetailsScreen(
  viewModel: SessionsViewModel,
  onBack: () -> Unit,
  onOpenGameRounds: (String) -> Unit,
  onOpenPlayerStats: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  var gameToDelete by remember { mutableStateOf<SessionGameRowItem?>(null) }

  if (gameToDelete != null) {
    ConfirmActionDialog(
      title = "Delete game?",
      message = "Delete this game? Its scores and statistics will be removed from all participating players.",
      confirmLabel = "Delete",
      isDanger = true,
      onConfirm = {
        val target = gameToDelete!!
        gameToDelete = null
        viewModel.deleteGame(target.gameId)
      },
      onDismiss = { gameToDelete = null },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    TichuTopBar(title = "Session Details", onBack = onBack)

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        val detail = state.activeSessionDetail
        if (detail == null) {
          EmptyStateCard(
            icon = Icons.Rounded.Info,
            title = "No active session",
            subtitle = "Start a session on the Sessions tab to track games and player rankings.",
            actionLabel = "Back to sessions",
            onAction = onBack,
          )
        } else {
          Text(
            text = detail.playerNamesLine,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SummaryMetricCard(
              label = "Games played",
              value = "${detail.gamesPlayedCount}",
              modifier = Modifier.weight(1f),
            )
            SummaryMetricCard(
              label = "Duration",
              value = detail.durationText,
              modifier = Modifier.weight(1f),
            )
          }

          RankingLeaderboardCard(
            title = "Player statistics",
            rankings = detail.rankings,
            selectedMetric = detail.rankingSort,
            onSelectMetric = viewModel::setSessionRankingSort,
            onPlayerClick = onOpenPlayerStats,
            testTagPrefix = "session_detail_ranking",
          )

          Text(
            text = "Game history",
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
          )

          if (detail.games.isEmpty()) {
            Text(
              text = "No completed games yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textMuted,
            )
          } else {
            SessionGamesListCard(
              games = detail.games,
              onOpenGameRounds = onOpenGameRounds,
              onDeleteGame = { gameToDelete = it },
            )
          }
        }
      }
    }
  }
}

@Composable
fun SummaryMetricCard(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textFaint,
      )
      Text(
        text = value,
        style = MonoTabularStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
        color = colors.text,
      )
    }
  }
}

@Composable
fun SessionGamesListCard(
  games: List<SessionGameRowItem>,
  onOpenGameRounds: (String) -> Unit,
  onDeleteGame: (SessionGameRowItem) -> Unit,
) {
  val colors = TichuTheme.colors
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column {
      games.forEachIndexed { idx, game ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .let { mod ->
              if (game.canViewRounds) {
                mod.clickable { onOpenGameRounds(game.gameId) }
              } else {
                mod
              }
            }
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
            .testTag("session_game_row_${game.gameId}"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = game.summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
          )
          if (game.canDelete) {
            IconButton(
              onClick = { onDeleteGame(game) },
              modifier = Modifier
                .size(48.dp)
                .testTag("delete_game_${game.gameId}"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete game",
                tint = colors.textFaint,
              )
            }
          }
          if (game.canViewRounds) {
            Box(
              modifier = Modifier.size(36.dp),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "View rounds",
                tint = colors.textFaint,
              )
            }
          }
        }
        if (idx < games.lastIndex) {
          HorizontalDivider(color = colors.border)
        }
      }
    }
  }
}

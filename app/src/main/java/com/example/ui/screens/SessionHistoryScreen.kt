package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RankingLeaderboardCard
import com.example.ui.components.TichuTopBar
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.SessionDetailUiModel
import com.example.ui.viewmodel.SessionGameRowItem
import com.example.ui.viewmodel.SessionsViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun SessionHistoryScreen(
  viewModel: SessionsViewModel,
  onBack: () -> Unit,
  onOpenGameRounds: (String) -> Unit,
  onOpenPlayerStats: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  var sessionToDelete by remember { mutableStateOf<SessionDetailUiModel?>(null) }
  var gameToDelete by remember { mutableStateOf<SessionGameRowItem?>(null) }

  if (sessionToDelete != null) {
    val s = sessionToDelete!!
    val count = s.games.size
    ConfirmActionDialog(
      title = "Delete session?",
      message = "Delete this session and its $count game${if (count == 1) "" else "s"}? All related scores and statistics will be removed from participating players.",
      confirmLabel = "Delete session",
      isDanger = true,
      onConfirm = {
        sessionToDelete = null
        viewModel.deleteSession(s.sessionId)
      },
      onDismiss = { sessionToDelete = null },
    )
  }

  if (gameToDelete != null) {
    val g = gameToDelete!!
    ConfirmActionDialog(
      title = "Delete game?",
      message = "Delete this game? Its scores and statistics will be removed from all participating players.",
      confirmLabel = "Delete",
      isDanger = true,
      onConfirm = {
        gameToDelete = null
        viewModel.deleteGame(g.gameId)
      },
      onDismiss = { gameToDelete = null },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    TichuTopBar(title = "Session History", onBack = onBack)

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (state.historySessions.isEmpty()) {
          EmptyStateCard(
            icon = Icons.Rounded.History,
            title = "No sessions yet",
            subtitle = "Ended sessions will appear here with per-session leaderboards and archived games.",
          )
        } else {
          state.historySessions.forEach { session ->
            val expanded = session.sessionId in state.expandedHistorySessionIds
            val formattedDate = remember(session.startedAt) {
              DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(session.startedAt))
            }

            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              color = colors.surface,
              border = BorderStroke(1.dp, colors.border),
            ) {
              Column {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleHistoryExpanded(session.sessionId) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("history_session_${session.sessionId}"),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                      Text(
                        text = "Session · $formattedDate",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                      )
                      if (!session.isOwnedByCurrentUser) {
                        Surface(
                          shape = RoundedCornerShape(999.dp),
                          color = colors.surface2,
                          border = BorderStroke(1.dp, colors.borderStrong),
                        ) {
                          Text(
                            text = "Shared",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                          )
                        }
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "${session.games.size} game${if (session.games.size == 1) "" else "s"} · ${session.durationText}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = colors.textMuted,
                    )
                  }
                  Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse session" else "Expand session",
                    tint = colors.textFaint,
                  )
                }

                if (session.isOwnedByCurrentUser) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.End,
                  ) {
                    TextButton(
                      onClick = { sessionToDelete = session },
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("delete_session_${session.sessionId}"),
                    ) {
                      Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Delete session", color = colors.danger, fontWeight = FontWeight.SemiBold)
                    }
                  }
                }

                AnimatedVisibility(visible = expanded) {
                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    HorizontalDivider(color = colors.border)
                    Text(
                      text = session.playerNamesLine,
                      style = MaterialTheme.typography.bodyMedium,
                      color = colors.textMuted,
                    )
                    RankingLeaderboardCard(
                      title = "Player statistics",
                      rankings = session.rankings,
                      selectedMetric = session.rankingSort,
                      onSelectMetric = viewModel::setSessionRankingSort,
                      onPlayerClick = onOpenPlayerStats,
                      testTagPrefix = "history_ranking_${session.sessionId}",
                    )
                    if (session.games.isNotEmpty()) {
                      SessionGamesListCard(
                        games = session.games,
                        onOpenGameRounds = onOpenGameRounds,
                        onDeleteGame = { gameToDelete = it },
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

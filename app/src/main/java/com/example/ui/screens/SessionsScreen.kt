package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.SessionStatus
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.LineupPreviewSection
import com.example.ui.components.TichuTopBar
import com.example.ui.components.TopBarLabeledAction
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.SessionsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionsScreen(
  viewModel: SessionsViewModel,
  onNavigateToScoreboard: () -> Unit,
  onOpenSessionDetails: () -> Unit,
  onOpenSessionHistory: () -> Unit,
  onGoToPlayersTab: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val snackbarHostState = remember { SnackbarHostState() }
  var lastHandledTrigger by remember { mutableIntStateOf(state.navigateToScoreboardTrigger) }
  var showEndSessionDialog by remember { mutableStateOf(false) }

  LaunchedEffect(state.navigateToScoreboardTrigger) {
    if (state.navigateToScoreboardTrigger > lastHandledTrigger) {
      lastHandledTrigger = state.navigateToScoreboardTrigger
      onNavigateToScoreboard()
    }
  }

  LaunchedEffect(state.errorMessage) {
    val msg = state.errorMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeError()
    }
  }

  if (showEndSessionDialog) {
    val hasUnfinishedGame = state.currentSessionGame != null
    ConfirmActionDialog(
      title = "End session?",
      message = if (hasUnfinishedGame) {
        "End session and discard the current game? Completed games will be kept."
      } else {
        "End session? Completed games will be kept."
      },
      confirmLabel = "End session",
      isDanger = true,
      onConfirm = {
        showEndSessionDialog = false
        viewModel.endSession()
      },
      onDismiss = { showEndSessionDialog = false },
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      TichuTopBar(
        title = "Sessions",
        trailingActions = {
          TopBarLabeledAction(
            icon = Icons.Rounded.History,
            label = "History",
            onClick = onOpenSessionHistory,
            testTag = "session_history_button",
          )
        },
      )

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
          val activeSession = state.activeSession
          if (activeSession != null) {
            // Active or Paused session view
            val isPaused = activeSession.status == SessionStatus.PAUSED
            val isHost = state.isActiveSessionHost
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isHost) "Current session" else "Live shared session",
                  style = MaterialTheme.typography.headlineMedium,
                  color = colors.text,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = activeSession.playerIds.joinToString(" · ") {
                    state.playersById[it]?.name ?: "(removed player)"
                  },
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textMuted,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isHost) {
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surface2,
                    border = BorderStroke(1.dp, colors.borderStrong),
                  ) {
                    Text(
                      text = "SHARED · LIVE",
                      style = MaterialTheme.typography.labelSmall,
                      color = colors.textMuted,
                      modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("shared_live_badge"),
                    )
                  }
                }
                Surface(
                  shape = RoundedCornerShape(999.dp),
                  color = if (isPaused) colors.teamASoft else colors.brand.copy(alpha = 0.14f),
                ) {
                  Text(
                    text = if (isPaused) "PAUSED" else "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPaused) colors.teamA else colors.brand,
                    modifier = Modifier
                      .padding(horizontal = 10.dp, vertical = 5.dp)
                      .testTag("session_status_badge"),
                  )
                }
              }
            }

            if (!isHost) {
              Text(
                text = "Hosted by another player — live scores sync automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.testTag("shared_session_readonly_note"),
              )
            }

            if (state.currentSessionGame != null) {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = "GAME IN PROGRESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                  )
                  Text(
                    text = state.currentGameSummary.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Button(
                    onClick = onNavigateToScoreboard,
                    colors = ButtonDefaults.buttonColors(
                      containerColor = colors.brand,
                      contentColor = colors.onBrand,
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("back_to_scoring_button"),
                  ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = if (isHost) "Back to scoring" else "View scoreboard",
                      fontWeight = FontWeight.Bold,
                    )
                  }
                }
              }
            } else if (isHost) {
              state.previewLineup?.let { lineup ->
                LineupPreviewSection(
                  lineup = lineup,
                  playersById = state.playersById,
                  swapFirstPlayerId = state.swapFirstPlayerId,
                  onSwapTap = viewModel::tapPlayerForSwap,
                  onReshuffle = viewModel::reshuffleLineup,
                  note = if (state.hasPriorGamesInActiveSession) {
                    "Pairings are balanced against earlier games."
                  } else {
                    null
                  },
                )
              }
              Button(
                onClick = viewModel::startNextGame,
                colors = ButtonDefaults.buttonColors(
                  containerColor = colors.brand,
                  contentColor = colors.onBrand,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("start_next_game_button"),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start next game", fontWeight = FontWeight.Bold)
              }
            } else {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = "BETWEEN GAMES",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                  )
                  Text(
                    text = "Waiting for the host to start the next game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                  )
                }
              }
            }

            HorizontalDivider(color = colors.border)

            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              if (isHost) {
                OutlinedButton(
                  onClick = {
                    if (isPaused) viewModel.resumeSession() else viewModel.pauseSession()
                  },
                  border = BorderStroke(1.dp, colors.borderStrong),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pause_resume_session_button"),
                ) {
                  Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (isPaused) "Resume" else "Pause")
                }
              }

              OutlinedButton(
                onClick = onOpenSessionDetails,
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brand),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("view_session_details_button"),
              ) {
                Icon(Icons.Rounded.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View details")
              }

              if (isHost) {
                OutlinedButton(
                  onClick = { showEndSessionDialog = true },
                  border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.55f)),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("end_session_button"),
                ) {
                  Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("End session")
                }
              }
            }
          } else {
            // New session setup view
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "New session",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
              )
              Text(
                text = "Choose 4, 5 or 6 players to get started.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
              )
            }

            if (state.activePlayers.size < 4) {
              EmptyStateCard(
                icon = Icons.Rounded.Group,
                title = "Not enough players yet",
                subtitle = "Add at least 4 active players on the Players tab to start a session.",
                actionLabel = "Go to Players",
                onAction = onGoToPlayersTab,
              )
            } else {
              Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
              ) {
                Column {
                  state.activePlayers.forEachIndexed { idx, player ->
                    val isSelected = player.id in state.selectedPlayerIds
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .background(if (isSelected) colors.teamASoft else Color.Transparent)
                        .clickable { viewModel.togglePlayerSelection(player.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("pick_player_${player.id}"),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Checkbox(
                        checked = isSelected,
                        onCheckedChange = { viewModel.togglePlayerSelection(player.id) },
                        colors = CheckboxDefaults.colors(
                          checkedColor = colors.brand,
                          checkmarkColor = colors.onBrand,
                          uncheckedColor = colors.textFaint,
                        ),
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = player.name,
                        style = MaterialTheme.typography.bodyLarge.copy(
                          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = colors.text,
                      )
                    }
                    if (idx < state.activePlayers.lastIndex) {
                      HorizontalDivider(color = colors.border)
                    }
                  }
                }
              }

              val pickedCount = state.activePlayers.count { it.id in state.selectedPlayerIds }
              Text(
                text = "$pickedCount selected · need 4, 5 or 6",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.testTag("pick_status_text"),
              )

              state.previewLineup?.let { lineup ->
                LineupPreviewSection(
                  lineup = lineup,
                  playersById = state.playersById,
                  swapFirstPlayerId = state.swapFirstPlayerId,
                  onSwapTap = viewModel::tapPlayerForSwap,
                  onReshuffle = viewModel::reshuffleLineup,
                )
              }

              Button(
                onClick = viewModel::startSession,
                enabled = pickedCount in 4..6 && state.previewLineup != null,
                colors = ButtonDefaults.buttonColors(
                  containerColor = colors.brand,
                  contentColor = colors.onBrand,
                  disabledContainerColor = colors.surface3,
                  disabledContentColor = colors.textFaint,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(52.dp)
                  .testTag("start_session_button"),
              ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start session", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(12.dp),
    )
  }
}

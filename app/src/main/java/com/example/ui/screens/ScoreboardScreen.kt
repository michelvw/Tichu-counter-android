package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.TichuCall
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.ThreeTeamSummaryItemCard
import com.example.ui.components.TichuTopBar
import com.example.ui.components.TopBarLabeledAction
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.ScoreDisplayStyle
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.ScoreboardUiState
import com.example.ui.viewmodel.ScoreboardViewModel

@Composable
fun ScoreboardScreen(
  viewModel: ScoreboardViewModel,
  onOpenRoundScores: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenSessionsTab: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val snackbarHostState = remember { SnackbarHostState() }
  var showResetDialog by remember { mutableStateOf(false) }

  val view = LocalView.current
  DisposableEffect(view) {
    val previous = view.keepScreenOn
    view.keepScreenOn = true
    onDispose {
      view.keepScreenOn = previous
    }
  }

  LaunchedEffect(state.toastMessage) {
    val msg = state.toastMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeToast()
    }
  }

  if (showResetDialog) {
    val message = when {
      state.isThreeTeamMode -> "Reset this game? All three scores and rounds will be cleared."
      state.isSessionMode -> "This session's game is still in progress. Reset the scoreboard anyway?"
      else -> "Reset the whole game? The round history and both teams' scores will be cleared."
    }
    ConfirmActionDialog(
      title = "Reset game?",
      message = message,
      confirmLabel = "Reset",
      isDanger = true,
      onConfirm = {
        showResetDialog = false
        viewModel.confirmReset()
      },
      onDismiss = { showResetDialog = false },
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      TichuTopBar(
        leadingActions = {
          TopBarLabeledAction(
            icon = Icons.AutoMirrored.Rounded.Undo,
            label = "Undo",
            enabled = state.canUndo,
            onClick = viewModel::undo,
            testTag = "undo_button",
          )
          TopBarLabeledAction(
            icon = Icons.Rounded.RestartAlt,
            label = "Reset",
            enabled = !state.isFreshBoard,
            onClick = {
              if (state.isThreeTeamFinished) {
                viewModel.confirmReset()
              } else {
                showResetDialog = true
              }
            },
            testTag = "reset_button",
          )
        },
        trailingActions = {
          TopBarLabeledAction(
            icon = Icons.Rounded.ShowChart,
            label = "Scores",
            onClick = onOpenRoundScores,
            testTag = "round_scores_button",
          )
          TopBarLabeledAction(
            icon = Icons.Rounded.Settings,
            label = "Settings",
            onClick = onOpenSettings,
            testTag = "settings_button",
          )
        },
      )

      ScoreboardInfoBar(state = state)

      AnimatedVisibility(visible = state.showQuickGameHint) {
        QuickGameHintBanner(
          onGoToSessions = onOpenSessionsTab,
          onDismiss = viewModel::dismissModeHint,
        )
      }

      AnimatedVisibility(visible = state.isSessionPaused) {
        PausedSessionBanner(onResume = viewModel::resumePausedSession)
      }

      AnimatedVisibility(visible = state.winBannerVisible && state.winBannerMessage != null) {
        WinBanner(
          message = state.winBannerMessage.orEmpty(),
          showNextGame = state.showWinBannerFinishAction,
          showClose = state.showWinBannerCloseAction,
          onNextGame = viewModel::finishGameAndStartNext,
          onClose = viewModel::dismissWinBanner,
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(
          modifier = Modifier.widthIn(max = 680.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (state.isThreeTeamMode) {
            ThreeTeamProgressGrid(
              goal = state.winThreshold,
              items = state.threeTeamCards,
            )
          } else {
            TwoTeamProgressCard(
              teamAName = state.teamAName,
              teamBName = state.teamBName,
              scoreA = state.teamAScore,
              scoreB = state.teamBScore,
              winThreshold = state.winThreshold,
            )
          }

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            val colorA = colors.teamColor(state.activeIndices.getOrElse(0) { 0 })
            val colorB = colors.teamColor(state.activeIndices.getOrElse(1) { 1 })
            val reserveSubtitleSlot = !state.teamBSittingOutText.isNullOrBlank()

            TeamScoringCard(
              sideTag = "team_a",
              teamName = state.teamAName,
              subtitle = null,
              reserveSubtitleSpace = reserveSubtitleSlot,
              editableName = !state.isSessionMode,
              onNameChange = { viewModel.updateQuickTeamName(true, it) },
              totalScore = state.teamAScore,
              previewDelta = state.previewDeltaA,
              tichuCall = state.draft.teamA.tichuCall,
              doubleWin = state.draft.teamA.doubleWin,
              accentColor = colorA,
              onMinus = { viewModel.adjustPoints(true, -1) },
              onPlus = { viewModel.adjustPoints(true, 1) },
              onSelectTichu = { viewModel.selectTichuCall(true, it) },
              onToggleDoubleWin = { viewModel.toggleDoubleWin(true, it) },
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            )

            TeamScoringCard(
              sideTag = "team_b",
              teamName = state.teamBName,
              subtitle = state.teamBSittingOutText,
              reserveSubtitleSpace = reserveSubtitleSlot,
              editableName = !state.isSessionMode,
              onNameChange = { viewModel.updateQuickTeamName(false, it) },
              totalScore = state.teamBScore,
              previewDelta = state.previewDeltaB,
              tichuCall = state.draft.teamB.tichuCall,
              doubleWin = state.draft.teamB.doubleWin,
              accentColor = colorB,
              onMinus = { viewModel.adjustPoints(false, -1) },
              onPlus = { viewModel.adjustPoints(false, 1) },
              onSelectTichu = { viewModel.selectTichuCall(false, it) },
              onToggleDoubleWin = { viewModel.toggleDoubleWin(false, it) },
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            )
          }

          Button(
            onClick = viewModel::submitNextRound,
            enabled = state.nextRoundEnabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = colors.brand,
              contentColor = colors.onBrand,
              disabledContainerColor = colors.surface3,
              disabledContentColor = colors.textFaint,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("next_round_button"),
          ) {
            Text(
              text = if (state.isThreeTeamFinished) "Game finished" else "Next round",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
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

@Composable
private fun ScoreboardInfoBar(state: ScoreboardUiState) {
  val colors = TichuTheme.colors
  val leftLabel = buildString {
    if (!state.sessionTagText.isNullOrBlank()) {
      append(state.sessionTagText.uppercase())
      append(" · ")
    }
    append(if (state.isThreeTeamFinished) "FINAL" else "ROUND ${state.roundNumber}")
  }

  Surface(
    color = colors.surface,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = leftLabel,
          style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
          ),
          color = colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1f, fill = false)
            .testTag("round_counter_label"),
        )
        if (!state.dealerText.isNullOrBlank()) {
          Spacer(modifier = Modifier.width(8.dp))
          Surface(
            color = colors.brand.copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = state.dealerText,
              style = MaterialTheme.typography.labelSmall,
              color = colors.brand,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag("dealer_tag_label"),
            )
          }
        }
      }
      HorizontalDivider(color = colors.border)
    }
  }
}

@Composable
private fun QuickGameHintBanner(
  onGoToSessions: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = TichuTheme.colors
  Surface(
    color = colors.surface2,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Tracking players or sessions?",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textMuted,
          modifier = Modifier.weight(1f),
        )
        TextButton(
          onClick = onGoToSessions,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("hint_sessions_link"),
        ) {
          Text("Start a session", color = colors.brand, fontWeight = FontWeight.Bold)
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(48.dp)
            .testTag("dismiss_hint_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss hint",
            tint = colors.textFaint,
          )
        }
      }
      HorizontalDivider(color = colors.border)
    }
  }
}

@Composable
private fun PausedSessionBanner(onResume: () -> Unit) {
  val colors = TichuTheme.colors
  Surface(
    color = colors.teamASoft,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.PauseCircle,
        contentDescription = null,
        tint = colors.teamA,
      )
      Text(
        text = "Session paused. Resume to continue scoring.",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = colors.text,
        modifier = Modifier.weight(1f),
      )
      Button(
        onClick = onResume,
        colors = ButtonDefaults.buttonColors(
          containerColor = colors.brand,
          contentColor = colors.onBrand,
        ),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("resume_session_banner_button"),
      ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Resume")
      }
    }
  }
}

@Composable
private fun WinBanner(
  message: String,
  showNextGame: Boolean,
  showClose: Boolean,
  onNextGame: () -> Unit,
  onClose: () -> Unit,
) {
  val colors = TichuTheme.colors
  Surface(
    color = colors.brand,
    contentColor = colors.onBrand,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("win_banner"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.EmojiEvents,
        contentDescription = null,
        tint = colors.onBrand,
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = colors.onBrand,
        modifier = Modifier.weight(1f),
      )
      if (showNextGame) {
        OutlinedButton(
          onClick = onNextGame,
          border = BorderStroke(1.dp, colors.onBrand.copy(alpha = 0.65f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onBrand),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("next_game_banner_button"),
        ) {
          Text("Next game", fontWeight = FontWeight.Bold)
        }
      }
      if (showClose) {
        IconButton(
          onClick = onClose,
          modifier = Modifier
            .size(48.dp)
            .testTag("close_win_banner_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss win banner",
            tint = colors.onBrand,
          )
        }
      }
    }
  }
}

@Composable
private fun TwoTeamProgressCard(
  teamAName: String,
  teamBName: String,
  scoreA: Int,
  scoreB: Int,
  winThreshold: Int,
) {
  val colors = TichuTheme.colors
  val goal = winThreshold.coerceAtLeast(1)
  val ratioA = (scoreA.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
  val ratioB = (scoreB.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
  val remainingA = (goal - scoreA).coerceAtLeast(0)
  val remainingB = (goal - scoreB).coerceAtLeast(0)

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Goal: $winThreshold",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = colors.brand,
        )
        Text(
          text = "$scoreA – $scoreB",
          style = MaterialTheme.typography.labelMedium,
          color = colors.textFaint,
          maxLines = 1,
        )
      }

      // Opposed dual progress bar meeting at center goal marker
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .height(12.dp)
          .clip(RoundedCornerShape(999.dp))
          .background(colors.surface3),
      ) {
        val halfWidth = maxWidth / 2f
        if (ratioA > 0f) {
          Box(
            modifier = Modifier
              .align(Alignment.CenterStart)
              .width(halfWidth * ratioA)
              .fillMaxHeight()
              .background(colors.teamAFill),
          )
        }
        if (ratioB > 0f) {
          Box(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .width(halfWidth * ratioB)
              .fillMaxHeight()
              .background(colors.teamBFill),
          )
        }
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .width(2.dp)
            .fillMaxHeight()
            .background(colors.surface),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "$teamAName: ${if (remainingA > 0) "$remainingA left" else "Goal reached"}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.teamA,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "$teamBName: ${if (remainingB > 0) "$remainingB left" else "Goal reached"}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.teamB,
          textAlign = TextAlign.End,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ThreeTeamProgressGrid(
  goal: Int,
  items: List<com.example.ui.viewmodel.ThreeTeamSummaryItem>,
) {
  val colors = TichuTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      text = "Goal: $goal",
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
      color = colors.textMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Max),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      items.forEach { item ->
        ThreeTeamSummaryItemCard(
          item = item,
          accentColor = colors.teamColor(item.teamIndex),
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        )
      }
    }
  }
}

@Composable
private fun TeamScoringCard(
  sideTag: String,
  teamName: String,
  subtitle: String?,
  reserveSubtitleSpace: Boolean,
  editableName: Boolean,
  onNameChange: (String) -> Unit,
  totalScore: Int,
  previewDelta: Int,
  tichuCall: TichuCall,
  doubleWin: Boolean,
  accentColor: Color,
  onMinus: () -> Unit,
  onPlus: () -> Unit,
  onSelectTichu: (TichuCall) -> Unit,
  onToggleDoubleWin: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  var tichuMenuOpen by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .background(accentColor),
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (editableName) {
          var localName by remember { mutableStateOf(teamName) }
          var isFocused by remember { mutableStateOf(false) }
          LaunchedEffect(teamName, isFocused) {
            if (!isFocused && localName != teamName) {
              localName = teamName
            }
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .border(
                width = 1.dp,
                color = colors.border,
                shape = RoundedCornerShape(8.dp),
              )
              .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            BasicTextField(
              value = localName,
              onValueChange = { updated ->
                localName = updated
                onNameChange(updated)
              },
              singleLine = true,
              textStyle = MaterialTheme.typography.titleMedium.copy(
                color = colors.text,
                textAlign = TextAlign.Center,
              ),
              cursorBrush = SolidColor(accentColor),
              modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                  val nowFocused = focusState.isFocused
                  if (isFocused && !nowFocused && localName.isBlank()) {
                    val fallback = if (sideTag == "team_a") "Team A" else "Team B"
                    localName = fallback
                    onNameChange(fallback)
                  }
                  isFocused = nowFocused
                }
                .testTag("${sideTag}_name_input"),
            )
            Icon(
              imageVector = Icons.Rounded.Edit,
              contentDescription = "Editable team name",
              tint = colors.textFaint,
              modifier = Modifier.size(15.dp),
            )
          }
        } else {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = teamName,
              style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
              color = colors.text,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .testTag("${sideTag}_name_label"),
            )
            if (reserveSubtitleSpace) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 20.dp),
                contentAlignment = Alignment.Center,
              ) {
                if (!subtitle.isNullOrBlank()) {
                  Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("${sideTag}_sitting_out_label"),
                  )
                }
              }
            }
          }
        }

        Text(
          text = "$totalScore",
          style = ScoreDisplayStyle,
          color = accentColor,
          maxLines = 1,
          modifier = Modifier.testTag("${sideTag}_total_points"),
        )

        // Linked +/-5 stepper pill
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = colors.surface2,
          border = BorderStroke(1.dp, colors.border),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Surface(
              shape = CircleShape,
              color = colors.surface,
              border = BorderStroke(1.dp, colors.border),
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onMinus)
                .testTag("${sideTag}_minus_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Remove,
                  contentDescription = "Decrease points by 5",
                  tint = colors.text,
                )
              }
            }

            Text(
              text = "$previewDelta",
              style = MonoTabularStyle.copy(fontSize = 18.sp),
              color = colors.text,
              textAlign = TextAlign.Center,
              modifier = Modifier
                .weight(1f)
                .testTag("${sideTag}_delta_points"),
            )

            Surface(
              shape = CircleShape,
              color = colors.surface,
              border = BorderStroke(1.dp, colors.border),
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onPlus)
                .testTag("${sideTag}_plus_button"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Rounded.Add,
                  contentDescription = "Increase points by 5",
                  tint = colors.text,
                )
              }
            }
          }
        }

        // Tichu modifier selector
        Box(modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = { tichuMenuOpen = true },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = colors.surface2,
              contentColor = colors.text,
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("${sideTag}_tichu_dropdown"),
          ) {
            Text(
              text = tichuCall.label,
              style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Icon(
              imageVector = Icons.Rounded.ArrowDropDown,
              contentDescription = "Choose Tichu call",
              tint = colors.textMuted,
            )
          }

          DropdownMenu(
            expanded = tichuMenuOpen,
            onDismissRequest = { tichuMenuOpen = false },
            containerColor = colors.surface,
          ) {
            TichuCall.entries.forEach { option ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = option.label,
                    fontWeight = if (option == tichuCall) FontWeight.Bold else FontWeight.Normal,
                    color = if (option == tichuCall) colors.brand else colors.text,
                  )
                },
                onClick = {
                  tichuMenuOpen = false
                  onSelectTichu(option)
                },
              )
            }
          }
        }

        // Double Win toggle row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onToggleDoubleWin(!doubleWin) }
            .padding(horizontal = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Checkbox(
            checked = doubleWin,
            onCheckedChange = onToggleDoubleWin,
            colors = CheckboxDefaults.colors(
              checkedColor = colors.brand,
              checkmarkColor = colors.onBrand,
              uncheckedColor = colors.textFaint,
            ),
            modifier = Modifier.testTag("${sideTag}_double_win_checkbox"),
          )
          Text(
            text = "Double win",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.textMuted,
          )
        }
      }
    }
  }
}

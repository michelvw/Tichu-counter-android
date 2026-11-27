package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.GoogleAuthHelper
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TichuTopBar
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.PlayersViewModel
import kotlinx.coroutines.launch

@Composable
fun PlayerStatsScreen(
  viewModel: PlayersViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val snackbarHostState = remember { SnackbarHostState() }
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val scope = rememberCoroutineScope()

  var showRenameDialog by remember { mutableStateOf(false) }
  var renameDraft by remember { mutableStateOf("") }
  var showResetStatsDialog by remember { mutableStateOf(false) }
  var showArchiveDialog by remember { mutableStateOf(false) }
  var showUnlinkDialog by remember { mutableStateOf(false) }
  var showGuestSignInDialog by remember { mutableStateOf(false) }
  var isSigningIn by remember { mutableStateOf(false) }
  var signInError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(state.archivedSuccessNavigateBack) {
    if (state.archivedSuccessNavigateBack) {
      onBack()
    }
  }

  LaunchedEffect(state.errorMessage) {
    val msg = state.errorMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeError()
    }
  }

  LaunchedEffect(state.statusMessage) {
    val msg = state.statusMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeStatus()
    }
  }

  val player = state.selectedPlayer
  val stats = state.selectedPlayerStats
  val isHost = player != null && (state.isGuestMode || player.ownerUid.isBlank() || player.ownerUid == state.currentUserId)
  val isSelfLinked = !state.isGuestMode && player != null && isHost && player.linkedUid == state.currentUserId

  val invite = state.generatedInvite
  if (invite != null) {
    AlertDialog(
      onDismissRequest = viewModel::dismissGeneratedInvite,
      containerColor = colors.surface,
      title = { Text("Share invite code", color = colors.text) },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Ask ${invite.playerName} to open Players → Claim in their signed-in app and enter this single-use code (valid 7 days):",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
          )
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.surface2,
            border = BorderStroke(1.dp, colors.borderStrong),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = invite.code,
              style = MonoTabularStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
              color = colors.brand,
              modifier = Modifier
                .padding(vertical = 14.dp)
                .align(Alignment.CenterHorizontally)
                .testTag("generated_invite_code"),
            )
          }
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(invite.code))
              scope.launch { snackbarHostState.showSnackbar("Invite code copied.") }
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("copy_invite_code_button"),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy code", fontWeight = FontWeight.SemiBold)
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = viewModel::dismissGeneratedInvite,
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("Done", color = colors.brand, fontWeight = FontWeight.Bold)
        }
      },
    )
  }

  if (showRenameDialog && player != null) {
    AlertDialog(
      onDismissRequest = { showRenameDialog = false },
      containerColor = colors.surface,
      title = { Text("Rename player", color = colors.text) },
      text = {
        OutlinedTextField(
          value = renameDraft,
          onValueChange = { renameDraft = it },
          label = { Text("Player name") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("rename_player_input"),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showRenameDialog = false
            viewModel.renameSelectedPlayer(renameDraft)
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("rename_player_confirm"),
        ) {
          Text("Save", color = colors.brand, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showRenameDialog = false },
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("Cancel", color = colors.textMuted)
        }
      },
    )
  }

  if (showResetStatsDialog && player != null) {
    ConfirmActionDialog(
      title = "Reset statistics?",
      message = "Reset ${player.name}'s statistics? Their player profile and future games will be kept.",
      confirmLabel = "Reset statistics",
      isDanger = true,
      onConfirm = {
        showResetStatsDialog = false
        viewModel.resetSelectedPlayerStats()
      },
      onDismiss = { showResetStatsDialog = false },
    )
  }

  if (showArchiveDialog && player != null) {
    ConfirmActionDialog(
      title = "Archive player?",
      message = "${player.name} will be hidden from future sessions but keeps their history. Archive them?",
      confirmLabel = "Archive",
      isDanger = true,
      onConfirm = {
        showArchiveDialog = false
        viewModel.archiveSelectedPlayer()
      },
      onDismiss = { showArchiveDialog = false },
    )
  }

  if (showUnlinkDialog && player != null) {
    ConfirmActionDialog(
      title = if (isSelfLinked || !isHost) "Unlink your account?" else "Unlink account?",
      message = when {
        isSelfLinked ->
          "Remove your account link from ${player.name}? They will remain in your roster as a guest player."
        isHost ->
          "Remove the linked user account from ${player.name}? They will return to guest status, and the other user will lose live sync access."
        else ->
          "Unlink your account from ${player.name}? You will no longer see sessions hosted by this group."
      },
      confirmLabel = "Unlink",
      isDanger = true,
      onConfirm = {
        showUnlinkDialog = false
        viewModel.unlinkSelectedPlayer()
      },
      onDismiss = { showUnlinkDialog = false },
    )
  }

  if (showGuestSignInDialog && player != null) {
    AlertDialog(
      onDismissRequest = { if (!isSigningIn) showGuestSignInDialog = false },
      containerColor = colors.surface,
      title = { Text("Sign in to link player", color = colors.text) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Sign in with Google to generate a single-use invite code and link ${player.name} to their account.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
          )
          if (!signInError.isNullOrBlank()) {
            Text(
              text = signInError.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = colors.danger,
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (isSigningIn) return@TextButton
            isSigningIn = true
            signInError = null
            GoogleAuthHelper.onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isSigningIn = false
                showGuestSignInDialog = false
              },
              onAuthError = { msg ->
                isSigningIn = false
                signInError = msg
              },
              scope = scope,
              onAuthCancelled = {
                isSigningIn = false
              },
            )
          },
          enabled = !isSigningIn,
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          if (isSigningIn) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Text("Sign in with Google", color = colors.brand, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showGuestSignInDialog = false },
          enabled = !isSigningIn,
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("Cancel", color = colors.textMuted)
        }
      },
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      TichuTopBar(title = "Player Statistics", onBack = onBack)

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
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (player == null || stats == null) {
            EmptyStateCard(
              icon = Icons.Rounded.PersonOff,
              title = "Player not found",
              subtitle = "That player could not be found.",
              actionLabel = "Back to players",
              onAction = onBack,
            )
          } else {
            // Identity header
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(colors.brand),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = player.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                  style = MaterialTheme.typography.headlineMedium,
                  color = colors.onBrand,
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = player.name,
                  style = MaterialTheme.typography.headlineMedium,
                  color = colors.text,
                  modifier = Modifier.testTag("player_stats_name"),
                )
                val statusSubtitle = when {
                  state.isGuestMode -> "Local profile · Saved on this device"
                  !isHost -> "Linked to your account · Managed by host"
                  isSelfLinked -> "Linked to your account · ${player.linkedLabel ?: "You"}"
                  player.linkedUid != null -> "Linked to ${player.linkedLabel ?: "user account"}"
                  else -> "Guest player · Not linked to an account"
                }
                Text(
                  text = statusSubtitle,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (player.linkedUid != null) colors.brand else colors.textMuted,
                )
              }
              if (isHost) {
                IconButton(
                  onClick = {
                    renameDraft = player.name
                    showRenameDialog = true
                  },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("rename_player_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Rename player",
                    tint = colors.brand,
                  )
                }
              }
            }

            // Win rate hero card
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              color = colors.surface,
              border = BorderStroke(1.dp, colors.border),
            ) {
              Column {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(colors.brand),
                )
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = "WIN RATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint,
                  )
                  val pctStr = if (stats.winPct % 1.0 == 0.0) "${stats.winPct.toInt()}" else "${stats.winPct}"
                  Text(
                    text = "$pctStr%",
                    style = MonoTabularStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold),
                    color = colors.brand,
                  )
                  Text(
                    text = "${stats.gamesWon} wins · ${stats.gamesLost} losses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                  )
                }
              }
            }

            // 2x2 metric grid
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SummaryMetricCard(
                label = "Games played",
                value = "${stats.gamesPlayed}",
                modifier = Modifier.weight(1f),
              )
              SummaryMetricCard(
                label = "Total points",
                value = "${stats.totalPoints}",
                modifier = Modifier.weight(1f),
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              SummaryMetricCard(
                label = "Point average",
                value = "${stats.avgPointsPerGame}",
                modifier = Modifier.weight(1f),
              )
              SummaryMetricCard(
                label = "Best game",
                value = stats.bestGameScore?.toString() ?: "—",
                modifier = Modifier.weight(1f),
              )
            }

            // Special plays & sessions card
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              color = colors.surface,
              border = BorderStroke(1.dp, colors.border),
            ) {
              Column {
                StatDetailRow(
                  label = "Tichu calls",
                  value = "${stats.tichuWon}W · ${stats.tichuLost}L",
                )
                HorizontalDivider(color = colors.border)
                StatDetailRow(
                  label = "Grand Tichu calls",
                  value = "${stats.grandTichuWon}W · ${stats.grandTichuLost}L",
                )
                HorizontalDivider(color = colors.border)
                StatDetailRow(
                  label = "Double wins",
                  value = "${stats.doubleWins}",
                )
                HorizontalDivider(color = colors.border)
                StatDetailRow(
                  label = "Sessions attended",
                  value = "${stats.sessionsAttended}",
                )
              }
            }

            // Account linking actions
            if (state.isGuestMode) {
              OutlinedButton(
                onClick = {
                  signInError = null
                  showGuestSignInDialog = true
                },
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brand),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("guest_link_sign_in_button"),
              ) {
                Icon(Icons.Rounded.Link, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in to link account", fontWeight = FontWeight.SemiBold)
              }
            } else if (isHost && player.linkedUid == null) {
              Button(
                onClick = viewModel::generateInviteForSelectedPlayer,
                enabled = !state.isInviteBusy,
                colors = ButtonDefaults.buttonColors(
                  containerColor = colors.brand,
                  contentColor = colors.onBrand,
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("link_player_account_button"),
              ) {
                if (state.isInviteBusy) {
                  CircularProgressIndicator(
                    color = colors.onBrand,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                  )
                } else {
                  Icon(Icons.Rounded.Link, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Link to user account", fontWeight = FontWeight.Bold)
                }
              }
              if (state.selfLinkedPlayerId == null) {
                OutlinedButton(
                  onClick = viewModel::linkSelfToSelectedPlayer,
                  enabled = !state.isInviteBusy,
                  border = BorderStroke(1.dp, colors.borderStrong),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brand),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("link_self_account_button"),
                ) {
                  Icon(Icons.Rounded.Person, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("This is me (link my account)", fontWeight = FontWeight.SemiBold)
                }
              }
            } else if (player.linkedUid != null) {
              OutlinedButton(
                onClick = { showUnlinkDialog = true },
                enabled = !state.isInviteBusy,
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("unlink_player_account_button"),
              ) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  if (isSelfLinked || !isHost) "Unlink my account" else "Unlink user account",
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }

            if (isHost) {
              OutlinedButton(
                onClick = { showResetStatsDialog = true },
                border = BorderStroke(1.dp, colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("reset_player_stats_button"),
              ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset statistics", fontWeight = FontWeight.SemiBold)
              }

              if (player.active) {
                OutlinedButton(
                  onClick = { showArchiveDialog = true },
                  border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.55f)),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("archive_player_button"),
                ) {
                  Icon(Icons.Rounded.Archive, contentDescription = null)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Archive player", fontWeight = FontWeight.SemiBold)
                }
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

@Composable
private fun StatDetailRow(
  label: String,
  value: String,
) {
  val colors = TichuTheme.colors
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      color = colors.textMuted,
    )
    Text(
      text = value,
      style = MonoTabularStyle.copy(fontSize = 15.sp),
      color = colors.text,
    )
  }
}

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.GoogleAuthHelper
import com.example.domain.Player
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.RankingLeaderboardCard
import com.example.ui.components.TichuTopBar
import com.example.ui.components.TopBarLabeledAction
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.PlayersViewModel

@Composable
fun PlayersScreen(
  viewModel: PlayersViewModel,
  onOpenPlayerStats: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val context = LocalContext.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }

  var newPlayerName by remember { mutableStateOf("") }
  var showArchived by remember { mutableStateOf(false) }
  var playerToDelete by remember { mutableStateOf<Player?>(null) }
  var showClaimDialog by remember { mutableStateOf(false) }
  var showSignInRequiredDialog by remember { mutableStateOf(false) }
  var isSigningIn by remember { mutableStateOf(false) }
  var signInError by remember { mutableStateOf<String?>(null) }
  var claimCodeInput by remember { mutableStateOf("") }

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

  if (playerToDelete != null) {
    val target = playerToDelete!!
    ConfirmActionDialog(
      title = "Delete player permanently?",
      message = "Permanently delete ${target.name}? Past sessions will still keep their scores, but their name will appear as a removed player and their profile cannot be restored.",
      confirmLabel = "Delete",
      isDanger = true,
      onConfirm = {
        playerToDelete = null
        viewModel.deleteArchivedPlayer(target.id)
      },
      onDismiss = { playerToDelete = null },
    )
  }

  if (showClaimDialog) {
    AlertDialog(
      onDismissRequest = { if (!state.isInviteBusy) showClaimDialog = false },
      containerColor = colors.surface,
      title = { Text("Claim guest player", color = colors.text) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Enter the invite code shared by your session host to link your account to your existing guest profile and stats.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
          )
          OutlinedTextField(
            value = claimCodeInput,
            onValueChange = { claimCodeInput = it.uppercase() },
            label = { Text("Invite code (e.g. TC7K9M2P)") },
            singleLine = true,
            enabled = !state.isInviteBusy,
            keyboardOptions = KeyboardOptions(
              capitalization = KeyboardCapitalization.Characters,
              imeAction = ImeAction.Done,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("claim_invite_code_input"),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.claimPlayerInvite(claimCodeInput) {
              claimCodeInput = ""
              showClaimDialog = false
            }
          },
          enabled = !state.isInviteBusy && claimCodeInput.trim().isNotEmpty(),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("claim_invite_confirm_button"),
        ) {
          if (state.isInviteBusy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Text("Claim", color = colors.brand, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showClaimDialog = false },
          enabled = !state.isInviteBusy,
          modifier = Modifier.heightIn(min = 48.dp),
        ) {
          Text("Cancel", color = colors.textMuted)
        }
      },
    )
  }

  if (showSignInRequiredDialog) {
    AlertDialog(
      onDismissRequest = { if (!isSigningIn) showSignInRequiredDialog = false },
      containerColor = colors.surface,
      title = { Text("Sign in with Google", color = colors.text) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "Guest mode saves players locally on this device. Sign in with Google to claim an invite code and sync shared games and statistics.",
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
                showSignInRequiredDialog = false
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
            Text("Sign in", color = colors.brand, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showSignInRequiredDialog = false },
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
      TichuTopBar(
        title = "Players",
        trailingActions = {
          TopBarLabeledAction(
            icon = Icons.Rounded.Link,
            label = "Claim",
            onClick = {
              if (state.isGuestMode) {
                signInError = null
                showSignInRequiredDialog = true
              } else {
                claimCodeInput = ""
                showClaimDialog = true
              }
            },
            testTag = "claim_player_top_button",
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
          // Add player card
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              OutlinedTextField(
                value = newPlayerName,
                onValueChange = { newPlayerName = it },
                label = { Text("Add guest or roster player") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                  capitalization = KeyboardCapitalization.Words,
                  imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                  onDone = {
                    viewModel.addPlayer(newPlayerName) { newPlayerName = "" }
                  },
                ),
                modifier = Modifier
                  .weight(1f)
                  .testTag("new_player_name_input"),
              )
              Button(
                onClick = {
                  viewModel.addPlayer(newPlayerName) { newPlayerName = "" }
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = colors.brand,
                  contentColor = colors.onBrand,
                ),
                modifier = Modifier
                  .height(54.dp)
                  .testTag("add_player_button"),
              ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add", fontWeight = FontWeight.Bold)
              }
            }
          }

          RankingLeaderboardCard(
            title = "Player leaderboard",
            rankings = state.rankedActivePlayers,
            selectedMetric = state.rankingSort,
            onSelectMetric = viewModel::setRankingSort,
            onPlayerClick = onOpenPlayerStats,
            emptyText = if (state.isGuestMode) {
              "No players to rank yet. Add players above to get started."
            } else {
              "No players to rank yet. Add players above or claim a guest profile with an invite code."
            },
            testTagPrefix = "players_ranking",
            badgeForPlayer = if (state.isGuestMode) {
              null
            } else {
              { playerId ->
                val p = state.playersById[playerId]
                when {
                  p == null -> null
                  p.linkedUid == state.currentUserId -> "You"
                  p.ownerUid.isNotBlank() && p.ownerUid != state.currentUserId -> "You"
                  p.linkedUid != null -> "Linked"
                  else -> "Guest"
                }
              }
            },
          )

          // Archived players expandable section
          if (state.archivedPlayers.isNotEmpty()) {
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
                    .heightIn(min = 50.dp)
                    .clickable { showArchived = !showArchived }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("toggle_archived_players_button"),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = "Archived players (${state.archivedPlayers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textMuted,
                  )
                  Icon(
                    imageVector = if (showArchived) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle archived players",
                    tint = colors.textFaint,
                  )
                }

                AnimatedVisibility(visible = showArchived) {
                  Column {
                    state.archivedPlayers.forEach { archived ->
                      val isHost = archived.ownerUid.isBlank() || archived.ownerUid == state.currentUserId
                      HorizontalDivider(color = colors.border)
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .heightIn(min = 50.dp)
                          .padding(start = 14.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          text = archived.name,
                          style = MaterialTheme.typography.bodyLarge,
                          color = colors.textMuted,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                          modifier = Modifier.weight(1f),
                        )
                        if (isHost) {
                          TextButton(
                            onClick = { viewModel.unarchivePlayer(archived.id) },
                            modifier = Modifier
                              .heightIn(min = 48.dp)
                              .testTag("restore_player_${archived.id}"),
                          ) {
                            Text("Restore", color = colors.brand, fontWeight = FontWeight.Bold)
                          }
                          IconButton(
                            onClick = { playerToDelete = archived },
                            modifier = Modifier
                              .size(48.dp)
                              .testTag("delete_player_${archived.id}"),
                          ) {
                            Icon(
                              imageVector = Icons.Rounded.Delete,
                              contentDescription = "Delete ${archived.name}",
                              tint = colors.danger,
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
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(12.dp),
    )
  }
}

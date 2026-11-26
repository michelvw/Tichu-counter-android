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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.RankingLeaderboardCard
import com.example.ui.components.TichuTopBar
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.PlayersViewModel

@Composable
fun PlayersScreen(
  viewModel: PlayersViewModel,
  onOpenPlayerStats: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val snackbarHostState = remember { SnackbarHostState() }

  var newPlayerName by remember { mutableStateOf("") }
  var showArchived by remember { mutableStateOf(false) }

  LaunchedEffect(state.errorMessage) {
    val msg = state.errorMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeError()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      TichuTopBar(title = "Players")

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
                label = { Text("Player name") },
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
            emptyText = "No players to rank yet. Add players above to start tracking sessions.",
            testTagPrefix = "players_ranking",
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
                          modifier = Modifier.weight(1f),
                        )
                        TextButton(
                          onClick = { viewModel.unarchivePlayer(archived.id) },
                          modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("restore_player_${archived.id}"),
                        ) {
                          Text("Restore", color = colors.brand, fontWeight = FontWeight.Bold)
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

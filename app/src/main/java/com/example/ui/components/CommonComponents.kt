package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Lineup
import com.example.domain.Player
import com.example.domain.RankedPlayerEntry
import com.example.domain.RankingMetric
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.NavForeground
import com.example.ui.theme.TichuTheme

@Composable
fun TichuTopBar(
  title: String? = null,
  onBack: (() -> Unit)? = null,
  leadingActions: @Composable RowScope.() -> Unit = {},
  trailingActions: @Composable RowScope.() -> Unit = {},
) {
  val colors = TichuTheme.colors
  Surface(
    color = colors.navBg,
    contentColor = NavForeground,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .height(60.dp)
        .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (onBack != null) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(48.dp)
            .testTag("top_bar_back"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = NavForeground,
          )
        }
      }
      leadingActions()
      if (title != null) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleLarge,
          color = NavForeground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = if (onBack != null) 4.dp else 10.dp),
        )
      } else {
        Spacer(modifier = Modifier.weight(1f))
      }
      trailingActions()
    }
  }
}

@Composable
fun TopBarLabeledAction(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  testTag: String,
) {
  val alpha = if (enabled) 1f else 0.42f
  Column(
    modifier = Modifier
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
      .testTag(testTag)
      .semantics { contentDescription = label },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = NavForeground.copy(alpha = alpha),
      modifier = Modifier.size(21.dp),
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label.uppercase(),
      style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.5.sp,
        letterSpacing = 0.5.sp,
      ),
      color = NavForeground.copy(alpha = alpha),
      maxLines = 1,
    )
  }
}

@Composable
fun EmptyStateCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.borderStrong),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 22.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colors.brand.copy(alpha = 0.78f),
        modifier = Modifier.size(40.dp),
      )
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colors.text,
        textAlign = TextAlign.Center,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMuted,
        textAlign = TextAlign.Center,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(modifier = Modifier.height(2.dp))
        Button(
          onClick = onAction,
          modifier = Modifier.heightIn(min = 48.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = colors.brand,
            contentColor = colors.onBrand,
          ),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
fun RankingLeaderboardCard(
  title: String,
  rankings: List<RankedPlayerEntry>,
  selectedMetric: RankingMetric,
  onSelectMetric: (RankingMetric) -> Unit,
  onPlayerClick: (String) -> Unit,
  emptyText: String = "No players to rank yet.",
  testTagPrefix: String = "ranking",
  badgeForPlayer: ((String) -> String?)? = null,
) {
  val colors = TichuTheme.colors
  var menuExpanded by remember { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colors.text,
      )
      Box {
        OutlinedButton(
          onClick = { menuExpanded = true },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("${testTagPrefix}_sort_button"),
          border = BorderStroke(1.dp, colors.borderStrong),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surface,
            contentColor = colors.text,
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(
            text = selectedMetric.label,
            style = MaterialTheme.typography.labelMedium,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = Icons.Rounded.ArrowDropDown,
            contentDescription = "Sort ranking by",
          )
        }
        DropdownMenu(
          expanded = menuExpanded,
          onDismissRequest = { menuExpanded = false },
          containerColor = colors.surface,
        ) {
          RankingMetric.entries.forEach { metric ->
            DropdownMenuItem(
              text = {
                Text(
                  text = metric.label,
                  fontWeight = if (metric == selectedMetric) FontWeight.Bold else FontWeight.Normal,
                  color = if (metric == selectedMetric) colors.brand else colors.text,
                )
              },
              onClick = {
                menuExpanded = false
                onSelectMetric(metric)
              },
            )
          }
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      color = colors.surface,
      border = BorderStroke(1.dp, colors.border),
    ) {
      if (rankings.isEmpty()) {
        Text(
          text = emptyText,
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textMuted,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(22.dp),
        )
      } else {
        Column {
          rankings.forEachIndexed { index, entry ->
            val badgeText = badgeForPlayer?.invoke(entry.playerId)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable { onPlayerClick(entry.playerId) }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("${testTagPrefix}_row_${entry.playerId}"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(if (index == 0) colors.brand.copy(alpha = 0.14f) else colors.surface2),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "${index + 1}",
                  style = MonoTabularStyle.copy(fontSize = 13.sp),
                  color = if (index == 0) colors.brand else colors.textFaint,
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = entry.playerName,
                  style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                  color = colors.text,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f, fill = false),
                )
                if (!badgeText.isNullOrBlank()) {
                  Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surface2,
                    border = BorderStroke(1.dp, colors.border),
                  ) {
                    Text(
                      text = badgeText,
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      color = colors.textMuted,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = entry.formattedValue,
                style = MonoTabularStyle.copy(fontSize = 13.sp),
                color = colors.textMuted,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "View statistics",
                tint = colors.textFaint,
                modifier = Modifier.size(20.dp),
              )
            }
            if (index < rankings.lastIndex) {
              HorizontalDivider(color = colors.border)
            }
          }
        }
      }
    }
  }
}

@Composable
fun LineupPreviewSection(
  lineup: Lineup,
  playersById: Map<String, Player>,
  swapFirstPlayerId: String?,
  onSwapTap: (String) -> Unit,
  onReshuffle: () -> Unit,
  note: String? = null,
) {
  val colors = TichuTheme.colors
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    when (lineup) {
      is Lineup.Fixed -> {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          TeamPreviewCard(
            label = "Team 1",
            playerIds = lineup.teams[0],
            accentColor = colors.teamA,
            playersById = playersById,
            swapFirstPlayerId = swapFirstPlayerId,
            onSwapTap = onSwapTap,
            modifier = Modifier.weight(1f),
          )
          TeamPreviewCard(
            label = "Team 2",
            playerIds = lineup.teams[1],
            accentColor = colors.teamB,
            playersById = playersById,
            swapFirstPlayerId = swapFirstPlayerId,
            onSwapTap = onSwapTap,
            modifier = Modifier.weight(1f),
          )
        }
      }

      is Lineup.Rotation -> {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          TeamPreviewCard(
            label = "Fixed team",
            playerIds = lineup.fixedTeam,
            accentColor = colors.teamA,
            playersById = playersById,
            swapFirstPlayerId = swapFirstPlayerId,
            onSwapTap = onSwapTap,
            modifier = Modifier.weight(1f),
          )
          TeamPreviewCard(
            label = "Rotating pool",
            playerIds = lineup.pool,
            accentColor = colors.teamB,
            playersById = playersById,
            swapFirstPlayerId = swapFirstPlayerId,
            onSwapTap = onSwapTap,
            modifier = Modifier.weight(1f),
          )
        }
        Text(
          text = "One pool player sits out each round so everyone sits out equally often.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textMuted,
        )
      }

      is Lineup.ThreeTeams -> {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          lineup.teams.forEachIndexed { idx, team ->
            TeamPreviewCard(
              label = "Team ${idx + 1}",
              playerIds = team,
              accentColor = colors.teamColor(idx),
              playersById = playersById,
              swapFirstPlayerId = swapFirstPlayerId,
              onSwapTap = onSwapTap,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
        Text(
          text = "Two teams play each round while the third sits out. Each team keeps its own score.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textMuted,
        )
      }
    }

    if (note != null) {
      Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMuted,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Rounded.SwapHoriz,
          contentDescription = null,
          tint = if (swapFirstPlayerId != null) colors.brand else colors.textFaint,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = if (swapFirstPlayerId != null) {
            "Tap another player to swap"
          } else {
            "Tap two players to swap manually"
          },
          style = MaterialTheme.typography.labelMedium,
          color = if (swapFirstPlayerId != null) colors.brand else colors.textMuted,
        )
      }
      TextButton(
        onClick = onReshuffle,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("reshuffle_lineup_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Shuffle,
          contentDescription = null,
          tint = colors.brand,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Reshuffle", color = colors.brand)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamPreviewCard(
  label: String,
  playerIds: List<String>,
  accentColor: Color,
  playersById: Map<String, Player>,
  swapFirstPlayerId: String?,
  onSwapTap: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(14.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(4.dp)
          .background(accentColor),
      )
      Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = label.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          color = colors.textFaint,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          playerIds.forEach { pid ->
            val selected = swapFirstPlayerId == pid
            val name = playersById[pid]?.name ?: "?"
            Surface(
              shape = RoundedCornerShape(999.dp),
              color = if (selected) colors.brand.copy(alpha = 0.16f) else colors.surface2,
              border = BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.brand else colors.border,
              ),
              modifier = Modifier
                .heightIn(min = 40.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { onSwapTap(pid) }
                .testTag("swap_player_$pid"),
            ) {
              Box(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = name,
                  style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = if (selected) colors.brand else colors.text,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ConfirmActionDialog(
  title: String,
  message: String,
  confirmLabel: String = "Confirm",
  isDanger: Boolean = false,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = TichuTheme.colors
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surface,
    title = {
      Text(text = title, color = colors.text, style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Text(text = message, color = colors.textMuted, style = MaterialTheme.typography.bodyMedium)
    },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("dialog_confirm_button"),
      ) {
        Text(
          text = confirmLabel,
          color = if (isDanger) colors.danger else colors.brand,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("dialog_cancel_button"),
      ) {
        Text("Cancel", color = colors.textMuted)
      }
    },
  )
}

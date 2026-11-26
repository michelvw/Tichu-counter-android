package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ChartBitmapRenderer
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoundChartCard
import com.example.ui.components.TichuTopBar
import com.example.ui.components.TopBarLabeledAction
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.RoundDisplayRow
import com.example.ui.viewmodel.RoundScoresUiState
import com.example.ui.viewmodel.RoundScoresViewModel
import com.example.ui.viewmodel.TichuBadgeDisplay
import kotlinx.coroutines.launch

@Composable
fun RoundScoresScreen(
  viewModel: RoundScoresViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      TichuTopBar(
        title = "Round Scores",
        onBack = onBack,
        trailingActions = {
          if (state.isGraphView && state.rows.isNotEmpty()) {
            TopBarLabeledAction(
              icon = if (state.chipsVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
              label = "Labels",
              onClick = viewModel::toggleChips,
              testTag = "toggle_chips_button",
            )
            TopBarLabeledAction(
              icon = Icons.Rounded.Share,
              label = "Share",
              onClick = {
                ChartBitmapRenderer.shareChartPng(context, state, colors)
                  .onFailure {
                    scope.launch {
                      snackbarHostState.showSnackbar("Could not share chart image.")
                    }
                  }
              },
              testTag = "share_chart_button",
            )
          }
          TopBarLabeledAction(
            icon = if (state.isGraphView) Icons.AutoMirrored.Rounded.List else Icons.Rounded.ShowChart,
            label = if (state.isGraphView) "Table" else "Graph",
            onClick = viewModel::toggleView,
            testTag = "toggle_graph_table_button",
          )
        },
      )

      // Summary info bar
      Surface(
        color = colors.surface,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = state.roundCounterText,
              style = MaterialTheme.typography.labelLarge,
              color = colors.text,
            )
            Text(
              text = state.thresholdOrFinalText,
              style = MaterialTheme.typography.labelLarge,
              color = colors.brand,
            )
          }
          HorizontalDivider(color = colors.border)
        }
      }

      if (state.rows.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(16.dp),
          contentAlignment = Alignment.TopCenter,
        ) {
          EmptyStateCard(
            icon = Icons.Rounded.ShowChart,
            title = "No rounds recorded yet",
            subtitle = "Play a round on the Scoreboard to see cumulative totals and your progress chart.",
          )
        }
      } else if (state.isGraphView) {
        BoxWithConstraints(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(12.dp),
        ) {
          val chartHeight = (maxHeight).coerceAtLeast(320.dp)
          RoundChartCard(
            state = state,
            modifier = Modifier
              .fillMaxWidth()
              .height(chartHeight)
              .testTag("round_scores_chart"),
          )
        }
      } else {
        RoundScoresTable(
          state = state,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("round_scores_table"),
        )
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
private fun RoundScoresTable(
  state: RoundScoresUiState,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  val hScroll = rememberScrollState()
  val vScroll = rememberScrollState()

  val roundColW = 54.dp
  val numColW = 64.dp
  val tichuColW = 64.dp
  val dealerColW = 104.dp

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(hScroll)
        .verticalScroll(vScroll),
    ) {
      // Group header row
      Row(verticalAlignment = Alignment.CenterVertically) {
        TableHeaderCell(text = "", width = roundColW, background = colors.surface)
        TableHeaderCell(
          text = state.teamAName,
          width = numColW + numColW + tichuColW,
          background = colors.teamASoft,
          textColor = colors.teamA,
        )
        TableHeaderCell(
          text = state.teamBName,
          width = numColW + numColW + tichuColW,
          background = colors.teamBSoft,
          textColor = colors.teamB,
        )
        if (state.isThreeTeams && state.teamCName != null) {
          TableHeaderCell(
            text = state.teamCName,
            width = numColW + numColW + tichuColW,
            background = colors.teamCSoft,
            textColor = colors.teamC,
          )
        }
        if (state.anyDealers) {
          TableHeaderCell(text = "Dealer", width = dealerColW, background = colors.surface)
        }
      }

      // Sub-header row
      Row(verticalAlignment = Alignment.CenterVertically) {
        TableHeaderCell(text = "#", width = roundColW, background = colors.surface, textColor = colors.textMuted)
        listOf(colors.teamASoft, colors.teamBSoft).forEach { bg ->
          TableHeaderCell(text = "Total", width = numColW, background = bg, textColor = colors.textMuted)
          TableHeaderCell(text = "Round", width = numColW, background = bg, textColor = colors.textMuted)
          TableHeaderCell(text = "Tichu", width = tichuColW, background = bg, textColor = colors.textMuted)
        }
        if (state.isThreeTeams) {
          TableHeaderCell(text = "Total", width = numColW, background = colors.teamCSoft, textColor = colors.textMuted)
          TableHeaderCell(text = "Round", width = numColW, background = colors.teamCSoft, textColor = colors.textMuted)
          TableHeaderCell(text = "Tichu", width = tichuColW, background = colors.teamCSoft, textColor = colors.textMuted)
        }
        if (state.anyDealers) {
          TableHeaderCell(text = "", width = dealerColW, background = colors.surface)
        }
      }

      HorizontalDivider(color = colors.border)

      state.rows.forEachIndexed { idx, row ->
        val stripe = if (idx % 2 == 0) colors.surface2.copy(alpha = 0.55f) else Color.Transparent
        RoundTableRow(
          row = row,
          isThreeTeams = state.isThreeTeams,
          anyDealers = state.anyDealers,
          stripeOverlay = stripe,
          roundColW = roundColW,
          numColW = numColW,
          tichuColW = tichuColW,
          dealerColW = dealerColW,
        )
        if (idx < state.rows.lastIndex) {
          HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
        }
      }
    }
  }
}

@Composable
private fun RoundTableRow(
  row: RoundDisplayRow,
  isThreeTeams: Boolean,
  anyDealers: Boolean,
  stripeOverlay: Color,
  roundColW: Dp,
  numColW: Dp,
  tichuColW: Dp,
  dealerColW: Dp,
) {
  val colors = TichuTheme.colors
  Row(
    modifier = Modifier.background(stripeOverlay),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TableTextCell("${row.roundNumber}", roundColW, Color.Transparent, isNumeric = true)
    TableTextCell("${row.totalA}", numColW, colors.teamASoft, isNumeric = true, bold = true)
    TableTextCell(
      text = if (isThreeTeams && 0 !in row.activeTeams) "—" else "${row.deltaA}",
      width = numColW,
      background = colors.teamASoft,
      isNumeric = true,
    )
    TichuBadgeCell(row.badgeA, tichuColW, colors.teamASoft)

    TableTextCell("${row.totalB}", numColW, colors.teamBSoft, isNumeric = true, bold = true)
    TableTextCell(
      text = if (isThreeTeams && 1 !in row.activeTeams) "—" else "${row.deltaB}",
      width = numColW,
      background = colors.teamBSoft,
      isNumeric = true,
    )
    TichuBadgeCell(row.badgeB, tichuColW, colors.teamBSoft)

    if (isThreeTeams) {
      TableTextCell("${row.totalC ?: 0}", numColW, colors.teamCSoft, isNumeric = true, bold = true)
      TableTextCell(
        text = if (2 !in row.activeTeams) "—" else "${row.deltaC ?: 0}",
        width = numColW,
        background = colors.teamCSoft,
        isNumeric = true,
      )
      TichuBadgeCell(row.badgeC ?: TichuBadgeDisplay("", false, false), tichuColW, colors.teamCSoft)
    }

    if (anyDealers) {
      TableTextCell(row.dealerName.orEmpty(), dealerColW, Color.Transparent)
    }
  }
}

@Composable
private fun TableHeaderCell(
  text: String,
  width: Dp,
  background: Color,
  textColor: Color = TichuTheme.colors.text,
) {
  Box(
    modifier = Modifier
      .width(width)
      .background(background)
      .padding(horizontal = 6.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
      color = textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun TableTextCell(
  text: String,
  width: Dp,
  background: Color,
  isNumeric: Boolean = false,
  bold: Boolean = false,
) {
  val colors = TichuTheme.colors
  Box(
    modifier = Modifier
      .width(width)
      .background(background)
      .padding(horizontal = 6.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = if (isNumeric) {
        MonoTabularStyle.copy(fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium)
      } else {
        MaterialTheme.typography.bodyMedium
      },
      color = colors.text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      textAlign = TextAlign.Center,
    )
  }
}

@Composable
private fun TichuBadgeCell(
  badge: TichuBadgeDisplay,
  width: Dp,
  background: Color,
) {
  val colors = TichuTheme.colors
  val formatted = buildAnnotatedString {
    if (badge.doubleWin) append("⇉ ")
    if (badge.code.isNotEmpty()) {
      if (badge.isLost) {
        withStyle(
          SpanStyle(
            textDecoration = TextDecoration.LineThrough,
            color = colors.danger,
            fontWeight = FontWeight.Bold,
          ),
        ) {
          append(badge.code)
        }
      } else {
        withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold)) {
          append(badge.code)
        }
      }
    }
  }
  Box(
    modifier = Modifier
      .width(width)
      .background(background)
      .padding(horizontal = 6.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = formatted,
      style = MaterialTheme.typography.labelMedium,
      maxLines = 1,
      textAlign = TextAlign.Center,
    )
  }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MonoTabularStyle
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.ThreeTeamSummaryItem

@Composable
fun ThreeTeamSummaryItemCard(
  item: ThreeTeamSummaryItem,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(modifier = Modifier.fillMaxHeight()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(3.dp)
          .background(accentColor),
      )
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = item.teamName,
          style = MaterialTheme.typography.labelMedium,
          color = colors.text,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.heightIn(min = 34.dp),
        )
        Text(
          text = "${item.score}",
          style = MonoTabularStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
          color = accentColor,
        )
        Text(
          text = if (item.remaining > 0) "${item.remaining} left" else "Goal reached",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(colors.surface3),
        ) {
          if (item.progressFraction > 0f) {
            Box(
              modifier = Modifier
                .fillMaxWidth(item.progressFraction)
                .fillMaxHeight()
                .background(accentColor),
            )
          }
        }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 18.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (item.isResting) {
            Text(
              text = if (item.isGameFinished) "Sat out" else "Sitting out",
              style = MaterialTheme.typography.labelSmall,
              color = colors.textMuted,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}

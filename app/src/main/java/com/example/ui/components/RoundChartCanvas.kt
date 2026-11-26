package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.RoundScoresUiState
import kotlin.math.roundToInt

@Composable
fun RoundChartCard(
  state: RoundScoresUiState,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  val textMeasurer = rememberTextMeasurer()

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = BorderStroke(1.dp, colors.border),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Legend row matching Chart.js legend at top
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LegendChip(name = state.teamAName, color = colors.teamA)
        LegendChip(
          name = state.teamBName,
          color = colors.teamB,
          modifier = Modifier.padding(start = 14.dp),
        )
        if (state.isThreeTeams && state.teamCName != null) {
          LegendChip(
            name = state.teamCName,
            color = colors.teamC,
            modifier = Modifier.padding(start = 14.dp),
          )
        }
      }

      Canvas(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
      ) {
        val rows = state.rows
        if (rows.isEmpty()) return@Canvas

        val leftPad = 42.dp.toPx()
        val rightPad = if (state.isThreeTeams && state.chipsVisible) 44.dp.toPx() else 22.dp.toPx()
        val topPad = 34.dp.toPx()
        val bottomPad = 34.dp.toPx()

        val plotW = (size.width - leftPad - rightPad).coerceAtLeast(40f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(40f)

        val yMin = state.yMin.toFloat()
        val yMax = state.yMax.toFloat()
        val ySpan = (yMax - yMin).coerceAtLeast(1f)

        fun mapY(value: Int): Float {
          val ratio = (value - yMin) / ySpan
          return topPad + plotH * (1f - ratio)
        }

        fun mapX(index: Int): Float {
          return if (rows.size == 1) {
            leftPad + plotW * 0.5f
          } else {
            leftPad + (plotW * index.toFloat() / (rows.size - 1).toFloat())
          }
        }

        val axisStyle = TextStyle(
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          color = colors.textMuted,
        )

        // Horizontal Y grid lines (5 ticks)
        val tickCount = 5
        for (i in 0..tickCount) {
          val fraction = i.toFloat() / tickCount.toFloat()
          val yVal = (yMin + ySpan * fraction).roundToInt()
          val yPx = topPad + plotH * (1f - fraction)
          drawLine(
            color = colors.border,
            start = Offset(leftPad, yPx),
            end = Offset(leftPad + plotW, yPx),
            strokeWidth = 1.dp.toPx(),
          )
          val measured = textMeasurer.measure("$yVal", style = axisStyle)
          drawText(
            textLayoutResult = measured,
            topLeft = Offset(
              x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
              y = yPx - measured.size.height / 2f,
            ),
          )
        }

        // X-axis vertical grid lines + round numbers
        rows.forEachIndexed { idx, row ->
          val xPx = mapX(idx)
          drawLine(
            color = colors.border.copy(alpha = 0.55f),
            start = Offset(xPx, topPad),
            end = Offset(xPx, topPad + plotH),
            strokeWidth = 0.8.dp.toPx(),
          )
          val measured = textMeasurer.measure("${row.roundNumber}", style = axisStyle)
          drawText(
            textLayoutResult = measured,
            topLeft = Offset(
              x = xPx - measured.size.width / 2f,
              y = topPad + plotH + 8.dp.toPx(),
            ),
          )
        }

        val pointsA = rows.mapIndexed { i, r -> Offset(mapX(i), mapY(r.totalA)) }
        val pointsB = rows.mapIndexed { i, r -> Offset(mapX(i), mapY(r.totalB)) }
        val pointsC = if (state.isThreeTeams) {
          rows.mapIndexed { i, r -> Offset(mapX(i), mapY(r.totalC ?: 0)) }
        } else {
          emptyList()
        }

        fun buildSmoothPath(pts: List<Offset>): Path {
          val path = Path()
          if (pts.isEmpty()) return path
          path.moveTo(pts.first().x, pts.first().y)
          if (pts.size == 1) return path
          for (i in 0 until pts.lastIndex) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            val ctrlX = (p0.x + p1.x) / 2f
            path.cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
          }
          return path
        }

        fun drawAreaFill(pts: List<Offset>, linePath: Path, tint: Color) {
          if (pts.size < 2) return
          val baseLineY = topPad + plotH
          val fillPath = Path().apply {
            addPath(linePath)
            lineTo(pts.last().x, baseLineY)
            lineTo(pts.first().x, baseLineY)
            close()
          }
          drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
              colors = listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.02f)),
              startY = topPad,
              endY = baseLineY,
            ),
          )
        }

        val pathA = buildSmoothPath(pointsA)
        val pathB = buildSmoothPath(pointsB)
        drawAreaFill(pointsA, pathA, colors.teamAFill)
        drawAreaFill(pointsB, pathB, colors.teamBFill)

        val lineStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (pointsA.size >= 2) drawPath(path = pathA, color = colors.teamA, style = lineStroke)
        if (pointsB.size >= 2) drawPath(path = pathB, color = colors.teamB, style = lineStroke)
        if (pointsC.size >= 2) {
          val pathC = buildSmoothPath(pointsC)
          drawPath(path = pathC, color = colors.teamC, style = lineStroke)
        }

        val pointRadius = 4.dp.toPx()
        pointsA.forEach { pt ->
          drawCircle(color = colors.surface, radius = pointRadius + 1.5.dp.toPx(), center = pt)
          drawCircle(color = colors.teamA, radius = pointRadius, center = pt)
        }
        pointsB.forEach { pt ->
          drawCircle(color = colors.surface, radius = pointRadius + 1.5.dp.toPx(), center = pt)
          drawCircle(color = colors.teamB, radius = pointRadius, center = pt)
        }
        pointsC.forEach { pt ->
          drawCircle(color = colors.surface, radius = pointRadius + 1.5.dp.toPx(), center = pt)
          drawCircle(color = colors.teamC, radius = pointRadius, center = pt)
        }

        // Diverging point chips when chipsVisible is enabled
        if (state.chipsVisible) {
          fun drawChip(
            anchor: Offset,
            total: Int,
            delta: Int,
            tichuText: String,
            textColor: Color,
            placement: ChipPlacement,
          ) {
            val deltaStr = if (delta >= 0) "+$delta" else "$delta"
            val labelText = buildString {
              append(total)
              append("\n")
              append(deltaStr)
              if (tichuText.isNotEmpty()) {
                append("\n")
                append(tichuText)
              }
            }
            val chipStyle = TextStyle(
              fontSize = 9.sp,
              lineHeight = 10.5.sp,
              fontWeight = FontWeight.Bold,
              color = textColor,
            )
            val measured = textMeasurer.measure(labelText, style = chipStyle)
            val padH = 4.dp.toPx()
            val padV = 2.dp.toPx()
            val boxW = measured.size.width + padH * 2f
            val boxH = measured.size.height + padV * 2f
            val gap = 7.dp.toPx()

            val rawLeft = when (placement) {
              ChipPlacement.ABOVE, ChipPlacement.BELOW -> anchor.x - boxW / 2f
              ChipPlacement.RIGHT -> anchor.x + gap
            }
            val rawTop = when (placement) {
              ChipPlacement.ABOVE -> anchor.y - gap - boxH
              ChipPlacement.BELOW -> anchor.y + gap
              ChipPlacement.RIGHT -> anchor.y - boxH / 2f
            }

            val clampedLeft = rawLeft.coerceIn(2f, (size.width - boxW - 2f).coerceAtLeast(2f))
            val clampedTop = rawTop.coerceIn(2f, (size.height - boxH - 2f).coerceAtLeast(2f))

            drawRoundRect(
              color = colors.surface.copy(alpha = 0.90f),
              topLeft = Offset(clampedLeft, clampedTop),
              size = Size(boxW, boxH),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            drawRoundRect(
              color = colors.border,
              topLeft = Offset(clampedLeft, clampedTop),
              size = Size(boxW, boxH),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
              style = Stroke(width = 0.7.dp.toPx()),
            )
            drawText(
              textLayoutResult = measured,
              topLeft = Offset(clampedLeft + padH, clampedTop + padV),
            )
          }

          rows.forEachIndexed { idx, row ->
            val aHigherOrEqual = row.totalA >= row.totalB
            drawChip(
              anchor = pointsA[idx],
              total = row.totalA,
              delta = row.deltaA,
              tichuText = row.badgeA.chartText,
              textColor = colors.teamA,
              placement = if (aHigherOrEqual) ChipPlacement.ABOVE else ChipPlacement.BELOW,
            )
            drawChip(
              anchor = pointsB[idx],
              total = row.totalB,
              delta = row.deltaB,
              tichuText = row.badgeB.chartText,
              textColor = colors.teamB,
              placement = if (aHigherOrEqual) ChipPlacement.BELOW else ChipPlacement.ABOVE,
            )
            if (state.isThreeTeams && row.totalC != null && row.deltaC != null && idx in pointsC.indices) {
              drawChip(
                anchor = pointsC[idx],
                total = row.totalC,
                delta = row.deltaC,
                tichuText = row.badgeC?.chartText.orEmpty(),
                textColor = colors.teamC,
                placement = ChipPlacement.RIGHT,
              )
            }
          }
        }
      }
    }
  }
}

private enum class ChipPlacement {
  ABOVE,
  BELOW,
  RIGHT,
}

@Composable
private fun LegendChip(
  name: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  val colors = TichuTheme.colors
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Box(
      modifier = Modifier
        .size(10.dp)
        .clip(CircleShape)
        .background(color),
    )
    Text(
      text = name,
      style = MaterialTheme.typography.labelMedium,
      color = colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.example.ui.theme.TichuSemanticColors
import com.example.ui.viewmodel.RoundScoresUiState
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object ChartBitmapRenderer {

  fun shareChartPng(
    context: Context,
    state: RoundScoresUiState,
    colors: TichuSemanticColors,
  ): Result<Unit> = runCatching {
    val bitmap = renderToBitmap(state, colors)
    val dir = File(context.cacheDir, "shared_charts").apply { mkdirs() }
    val roundCount = state.rows.size
    val outFile = File(dir, "tichu-scores-round-$roundCount.png")
    FileOutputStream(outFile).use { stream ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    }
    bitmap.recycle()

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.fileprovider",
      outFile,
    )
    val shareSummary = if (state.isThreeTeams && state.teamCName != null) {
      "${state.teamAName} vs ${state.teamBName} vs ${state.teamCName}"
    } else {
      "${state.teamAName} vs ${state.teamBName}"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "image/png"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "Tichu Scores")
      putExtra(Intent.EXTRA_TEXT, shareSummary)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Tichu Scores"))
  }

  private fun renderToBitmap(
    state: RoundScoresUiState,
    colors: TichuSemanticColors,
  ): Bitmap {
    val width = 1440
    val height = 960
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(colors.surface.toArgb())

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.text.toArgb()
      textSize = 36f
      typeface = Typeface.DEFAULT_BOLD
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.textMuted.toArgb()
      textSize = 26f
      typeface = Typeface.DEFAULT_BOLD
    }
    val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.textMuted.toArgb()
      textSize = 24f
    }
    val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = colors.border.toArgb()
      strokeWidth = 2f
    }

    canvas.drawText("Tichu Scores — ${state.roundCounterText}", 64f, 68f, titlePaint)
    canvas.drawText(state.thresholdOrFinalText, 64f, 106f, subPaint)

    // Legend
    var lx = 64f
    fun drawLegendItem(name: String, colorInt: Int) {
      val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
      canvas.drawCircle(lx + 10f, 146f, 10f, dotPaint)
      lx += 28f
      canvas.drawText(name, lx, 155f, subPaint)
      lx += subPaint.measureText(name) + 34f
    }
    drawLegendItem(state.teamAName, colors.teamA.toArgb())
    drawLegendItem(state.teamBName, colors.teamB.toArgb())
    if (state.isThreeTeams && state.teamCName != null) {
      drawLegendItem(state.teamCName, colors.teamC.toArgb())
    }

    val rows = state.rows
    if (rows.isEmpty()) return bitmap

    val leftPad = 120f
    val rightPad = if (state.isThreeTeams && state.chipsVisible) 130f else 64f
    val topPad = 210f
    val bottomPad = 90f
    val plotW = width - leftPad - rightPad
    val plotH = height - topPad - bottomPad

    val yMin = state.yMin.toFloat()
    val yMax = state.yMax.toFloat()
    val ySpan = (yMax - yMin).coerceAtLeast(1f)

    fun mapY(v: Int): Float = topPad + plotH * (1f - (v - yMin) / ySpan)
    fun mapX(i: Int): Float = if (rows.size == 1) {
      leftPad + plotW * 0.5f
    } else {
      leftPad + plotW * (i.toFloat() / (rows.size - 1).toFloat())
    }

    for (i in 0..5) {
      val frac = i / 5f
      val yVal = (yMin + ySpan * frac).roundToInt()
      val yPx = topPad + plotH * (1f - frac)
      canvas.drawLine(leftPad, yPx, leftPad + plotW, yPx, gridPaint)
      val label = "$yVal"
      canvas.drawText(label, leftPad - axisPaint.measureText(label) - 16f, yPx + 8f, axisPaint)
    }

    rows.forEachIndexed { idx, row ->
      val xPx = mapX(idx)
      canvas.drawLine(xPx, topPad, xPx, topPad + plotH, gridPaint)
      val lbl = "${row.roundNumber}"
      canvas.drawText(lbl, xPx - axisPaint.measureText(lbl) / 2f, topPad + plotH + 38f, axisPaint)
    }

    fun buildPath(xs: List<Float>, ys: List<Float>): Path {
      val p = Path()
      if (xs.isEmpty()) return p
      p.moveTo(xs[0], ys[0])
      for (i in 0 until xs.lastIndex) {
        val cx = (xs[i] + xs[i + 1]) / 2f
        p.cubicTo(cx, ys[i], cx, ys[i + 1], xs[i + 1], ys[i + 1])
      }
      return p
    }

    val xs = rows.indices.map(::mapX)
    val ysA = rows.map { mapY(it.totalA) }
    val ysB = rows.map { mapY(it.totalB) }
    val ysC = if (state.isThreeTeams) rows.map { mapY(it.totalC ?: 0) } else emptyList()

    fun drawSeries(ys: List<Float>, lineColor: Int, fillColor: Int?) {
      if (ys.isEmpty()) return
      val linePath = buildPath(xs, ys)
      if (fillColor != null && xs.size >= 2) {
        val fillPath = Path(linePath).apply {
          lineTo(xs.last(), topPad + plotH)
          lineTo(xs.first(), topPad + plotH)
          close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          shader = LinearGradient(
            0f,
            topPad,
            0f,
            topPad + plotH,
            (fillColor and 0x00FFFFFF) or (0x40 shl 24),
            (fillColor and 0x00FFFFFF) or (0x08 shl 24),
            Shader.TileMode.CLAMP,
          )
        }
        canvas.drawPath(fillPath, fillPaint)
      }
      if (xs.size >= 2) {
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = lineColor
          style = Paint.Style.STROKE
          strokeWidth = 6f
          strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(linePath, strokePaint)
      }
      val ptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surface.toArgb() }
      xs.indices.forEach { i ->
        canvas.drawCircle(xs[i], ys[i], 11f, bgPaint)
        canvas.drawCircle(xs[i], ys[i], 8f, ptPaint)
      }
    }

    drawSeries(ysA, colors.teamA.toArgb(), colors.teamAFill.toArgb())
    drawSeries(ysB, colors.teamB.toArgb(), colors.teamBFill.toArgb())
    if (state.isThreeTeams) {
      drawSeries(ysC, colors.teamC.toArgb(), null)
    }

    if (state.chipsVisible) {
      val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = (colors.surface.toArgb() and 0x00FFFFFF) or (0xE8 shl 24)
      }
      val chipBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colors.border.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
      }
      fun drawChip(x: Float, y: Float, total: Int, delta: Int, code: String, colorInt: Int, alignMode: Int) {
        val lines = buildList {
          add("$total")
          add(if (delta >= 0) "+$delta" else "$delta")
          if (code.isNotEmpty()) add(code)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = colorInt
          textSize = 20f
          typeface = Typeface.DEFAULT_BOLD
        }
        val maxW = lines.maxOf { textPaint.measureText(it) }
        val lineH = 24f
        val padH = 10f
        val padV = 6f
        val boxW = maxW + padH * 2f
        val boxH = lines.size * lineH + padV * 2f
        val left = when (alignMode) {
          2 -> x + 16f
          else -> x - boxW / 2f
        }.coerceIn(8f, width - boxW - 8f)
        val top = when (alignMode) {
          0 -> y - 16f - boxH
          1 -> y + 16f
          else -> y - boxH / 2f
        }.coerceIn(8f, height - boxH - 8f)

        val rect = RectF(left, top, left + boxW, top + boxH)
        canvas.drawRoundRect(rect, 8f, 8f, chipBg)
        canvas.drawRoundRect(rect, 8f, 8f, chipBorder)
        lines.forEachIndexed { idx, line ->
          canvas.drawText(line, left + padH, top + padV + (idx + 1) * lineH - 4f, textPaint)
        }
      }

      rows.forEachIndexed { i, row ->
        val aAbove = row.totalA >= row.totalB
        drawChip(xs[i], ysA[i], row.totalA, row.deltaA, row.badgeA.chartText, colors.teamA.toArgb(), if (aAbove) 0 else 1)
        drawChip(xs[i], ysB[i], row.totalB, row.deltaB, row.badgeB.chartText, colors.teamB.toArgb(), if (aAbove) 1 else 0)
        if (state.isThreeTeams && row.totalC != null && row.deltaC != null && i in ysC.indices) {
          drawChip(xs[i], ysC[i], row.totalC, row.deltaC, row.badgeC?.chartText.orEmpty(), colors.teamC.toArgb(), 2)
        }
      }
    }

    return bitmap
  }
}

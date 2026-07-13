package com.kickpredict.presentation.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Pre-resolved (already-localised) text for a shareable prediction card. */
data class PredictionShareData(
    val league: String,
    val matchup: String,
    val predictionLabel: String,
    val confidenceLabel: String,
    /** Outcome bars in order: home, draw, away — label + whole-percent. */
    val bars: List<Pair<String, Int>>,
    val chooserTitle: String,
    val footer: String,
)

private const val SIZE = 1080
private const val PAD = 72f

private const val GROUND = 0xFF0E1420.toInt()
private const val TRACK = 0xFF1E2A3D.toInt()
private const val ACCENT = 0xFF35C2F5.toInt()
private const val TEXT_PRIMARY = 0xFFF2F5F8.toInt()
private const val TEXT_SECONDARY = 0xFF9AA7B8.toInt()
private val BAR_COLORS = intArrayOf(0xFF35C2F5.toInt(), 0xFFF3C34B.toInt(), 0xFFF25C8A.toInt())

/** Render the prediction to a branded square card and open the system share sheet with it. */
fun sharePredictionCard(context: Context, data: PredictionShareData) {
    val bitmap = renderCard(data)
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "prediction.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, data.chooserTitle))
}

private fun renderCard(data: PredictionShareData): Bitmap {
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(GROUND)

    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val black = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)

    // Brand.
    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 68f }
    brand.color = TEXT_PRIMARY
    canvas.drawText("KICK", PAD, 150f, brand)
    val kickWidth = brand.measureText("KICK")
    brand.color = ACCENT
    canvas.drawText("PREDICT", PAD + kickWidth + 6f, 150f, brand)

    // League.
    val league = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 34f; color = ACCENT }
    canvas.drawText(data.league, PAD, 230f, league)

    // Matchup (truncated to fit).
    val matchup = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 58f; color = TEXT_PRIMARY }
    val matchupText = TextUtils.ellipsize(data.matchup, matchup, SIZE - 2 * PAD, TextUtils.TruncateAt.END)
    canvas.drawText(matchupText, 0, matchupText.length, PAD, 340f, matchup)

    // Prediction + confidence.
    val prediction = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 50f; color = ACCENT }
    val predText = TextUtils.ellipsize(data.predictionLabel, TextPaint(prediction), SIZE - 2 * PAD, TextUtils.TruncateAt.END)
    canvas.drawText(predText, 0, predText.length, PAD, 430f, prediction)
    val conf = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 34f; color = TEXT_SECONDARY }
    canvas.drawText(data.confidenceLabel, PAD, 490f, conf)

    // Outcome bars.
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 40f }
    val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 40f; textAlign = Paint.Align.RIGHT }
    data.bars.take(3).forEachIndexed { i, (label, percent) ->
        val top = 600f + i * 150f
        val color = BAR_COLORS[i]
        labelPaint.color = TEXT_PRIMARY
        canvas.drawText(label, PAD, top + 34f, labelPaint)
        pctPaint.color = color
        canvas.drawText("$percent%", SIZE - PAD, top + 34f, pctPaint)
        val barTop = top + 56f
        val barBottom = barTop + 30f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = TRACK }
        canvas.drawRoundRect(RectF(PAD, barTop, SIZE - PAD, barBottom), 16f, 16f, track)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val width = (SIZE - 2 * PAD) * (percent.coerceIn(0, 100) / 100f)
        canvas.drawRoundRect(RectF(PAD, barTop, PAD + width.coerceAtLeast(32f), barBottom), 16f, 16f, fill)
    }

    // Footer.
    val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 30f; color = TEXT_SECONDARY }
    canvas.drawText(data.footer, PAD, SIZE - 60f, footer)

    return bitmap
}

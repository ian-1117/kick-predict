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

/** Pre-resolved (already-localised) text for a shareable parlay slip. */
data class ParlayShareData(
    val title: String,
    /** One entry per leg: the pick label + its price string (e.g. "@2.48"). */
    val legs: List<Pair<String, String>>,
    val comboOddsLabel: String,
    val edgeLabel: String,
    /** Settlement banner (e.g. "Hit · +4.26u"), or null while the parlay is still open. */
    val statusLabel: String?,
    /** true won, false lost, null pending — tints the status banner. */
    val won: Boolean?,
    val chooserTitle: String,
    val footer: String,
)

private const val SIZE = 1080
private const val PAD = 72f

private const val GROUND = 0xFF0E1420.toInt()
private const val CARD = 0xFF1E2A3D.toInt()
private const val ACCENT = 0xFF35C2F5.toInt()
private const val WIN = 0xFF3DD68C.toInt()
private const val LOSS = 0xFFF25C8A.toInt()
private const val TEXT_PRIMARY = 0xFFF2F5F8.toInt()
private const val TEXT_SECONDARY = 0xFF9AA7B8.toInt()

/** Render the parlay to a branded square card and open the system share sheet with it. */
fun shareParlayCard(context: Context, data: ParlayShareData) {
    val bitmap = renderSlip(data)
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "parlay.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, data.chooserTitle))
}

private fun renderSlip(data: ParlayShareData): Bitmap {
    val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(GROUND)

    val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val black = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)

    // Brand.
    val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 68f; color = TEXT_PRIMARY }
    canvas.drawText("KICK", PAD, 150f, brand)
    val kickWidth = brand.measureText("KICK")
    brand.color = ACCENT
    canvas.drawText("PREDICT", PAD + kickWidth + 6f, 150f, brand)

    // Title.
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 56f; color = TEXT_PRIMARY }
    canvas.drawText(data.title, PAD, 250f, title)

    // Status banner (right-aligned), tinted by outcome.
    data.statusLabel?.let { label ->
        val color = when (data.won) {
            true -> WIN
            false -> LOSS
            null -> ACCENT
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 40f; this.color = color; textAlign = Paint.Align.RIGHT }
        canvas.drawText(label, SIZE - PAD, 250f, statusPaint)
    }

    // Legs — each on its own row inside a soft card, pick left / price right.
    val legLabel = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 42f; color = TEXT_PRIMARY }
    val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 42f; color = TEXT_SECONDARY; textAlign = Paint.Align.RIGHT }
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD }
    val rowH = 108f
    val gap = 18f
    var top = 330f
    // Cap the number of drawn legs so a very long parlay can't overflow the card.
    data.legs.take(6).forEach { (pick, price) ->
        canvas.drawRoundRect(RectF(PAD, top, SIZE - PAD, top + rowH), 24f, 24f, cardPaint)
        val textY = top + rowH / 2 + 15f
        val priceW = pricePaint.measureText(price)
        val pickText = TextUtils.ellipsize(pick, legLabel, SIZE - 2 * PAD - 48f - priceW - 24f, TextUtils.TruncateAt.END)
        canvas.drawText(pickText, 0, pickText.length, PAD + 32f, textY, legLabel)
        canvas.drawText(price, SIZE - PAD - 32f, textY, pricePaint)
        top += rowH + gap
    }

    // Combo odds + edge summary, bottom-anchored above the footer.
    val comboPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 96f; color = ACCENT }
    canvas.drawText(data.comboOddsLabel, PAD, SIZE - 190f, comboPaint)
    val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 44f; color = TEXT_SECONDARY }
    canvas.drawText(data.edgeLabel, PAD, SIZE - 130f, edgePaint)

    // Footer.
    val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; textSize = 30f; color = TEXT_SECONDARY }
    canvas.drawText(data.footer, PAD, SIZE - 60f, footer)

    return bitmap
}

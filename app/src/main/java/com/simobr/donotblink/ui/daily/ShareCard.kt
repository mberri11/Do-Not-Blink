package com.simobr.donotblink.ui.daily

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.simobr.donotblink.R
import com.simobr.donotblink.game.DailyResult
import com.simobr.donotblink.game.DailyTrial
import java.io.File
import java.time.LocalDate

/**
 * The trial result as a PNG, drawn with plain [android.graphics] rather than captured from the
 * composition.
 *
 * Capturing a composable means a real window, a real measure pass and a PixelCopy round trip — all
 * of which can fail on exactly the low-end hardware this game targets, and none of which is needed:
 * the card is nine shapes and five strings. Drawing it by hand is deterministic, testable off-device
 * and cannot be affected by whatever the player's screen happens to be doing.
 *
 * The marks are drawn as RINGS, not as text glyphs. The game is a ring; a share card of ▮ and ▯
 * would be a card for some other game.
 */
object ShareCard {

    /** Square, because every social surface accepts a square and crops nothing out of it. */
    const val SIZE_PX = 1080

    private const val PHOSPHOR = 0xFFFFB000.toInt()
    private const val HOT = 0xFFFFD97A.toInt()
    private const val DIM = 0xFF5C4200.toInt()
    private const val LABEL_MID = 0xFF8A6A1A.toInt()

    /**
     * Renders the card. [phosphor] and [dim] let the card wear whichever palette the player
     * unlocked, so a P1 GREEN player shares a green card.
     */
    fun render(
        context: Context,
        result: DailyResult,
        phosphor: Int = PHOSPHOR,
        hot: Int = HOT,
        dim: Int = DIM,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val mono = ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium)
            ?: Typeface.MONOSPACE
        val centreX = SIZE_PX / 2f

        // The glow the whole app sits on, behind the grid.
        val glowRadius = SIZE_PX * 0.42f
        canvas.drawCircle(
            centreX,
            SIZE_PX * 0.52f,
            glowRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    centreX,
                    SIZE_PX * 0.52f,
                    glowRadius,
                    intArrayOf(withAlpha(phosphor, 0.13f), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = mono
            textAlign = Paint.Align.CENTER
        }

        // Wordmark, then the date it belongs to. A share card with no date is not a daily.
        text.color = phosphor
        text.textSize = 44f
        text.letterSpacing = 0.34f
        canvas.drawText("DO NOT BLINK", centreX, 150f, text)

        text.color = LABEL_MID
        text.textSize = 26f
        text.letterSpacing = 0.30f
        canvas.drawText("TRIAL ${LocalDate.ofEpochDay(result.epochDay)}", centreX, 208f, text)

        drawMarkGrid(canvas, result, centreX, phosphor = phosphor, dim = dim)

        // The score.
        text.color = hot
        text.textSize = 150f
        text.letterSpacing = 0.02f
        canvas.drawText("${result.hits}/${DailyTrial.RINGS}", centreX, 760f, text)

        text.color = LABEL_MID
        text.textSize = 28f
        text.letterSpacing = 0.24f
        canvas.drawText("MEAN ERROR ${result.meanAbsErrorMs}MS", centreX, 828f, text)

        text.color = withAlpha(phosphor, 0.55f)
        text.textSize = 24f
        text.letterSpacing = 0.28f
        canvas.drawText("SAME TEN RINGS FOR EVERYONE, TODAY ONLY", centreX, 980f, text)

        return bitmap
    }

    /**
     * Two rows of five rings. A hit is a lit ring with its glow; a miss is the dead dashed ring the
     * fail screen draws, so the card reads exactly like the game that produced it.
     */
    private fun drawMarkGrid(
        canvas: Canvas,
        result: DailyResult,
        centreX: Float,
        phosphor: Int,
        dim: Int,
    ) {
        val perRow = DailyTrial.RINGS / 2
        val spacing = 168f
        val radius = 46f
        val rowY = floatArrayOf(370f, 552f)
        val firstX = centreX - spacing * (perRow - 1) / 2f

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        result.marks.forEachIndexed { index, hit ->
            val x = firstX + spacing * (index % perRow)
            val y = rowY[index / perRow]

            if (hit) {
                // The glow stack, thinned to three passes — the same concentric-stroke trick the
                // play surface uses instead of a blur.
                ring.color = withAlpha(phosphor, 0.10f)
                ring.strokeWidth = 26f
                canvas.drawCircle(x, y, radius, ring)
                ring.color = withAlpha(phosphor, 0.26f)
                ring.strokeWidth = 12f
                canvas.drawCircle(x, y, radius, ring)
                ring.color = phosphor
                ring.strokeWidth = 5f
                canvas.drawCircle(x, y, radius, ring)
            } else {
                ring.color = dim
                ring.strokeWidth = 4f
                ring.pathEffect = android.graphics.DashPathEffect(floatArrayOf(17f, 13f), 0f)
                canvas.drawCircle(x, y, radius, ring)
                ring.pathEffect = null
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    /**
     * The one-line caption that rides with the image. Plain text with the glyph grid: a receiver
     * whose app strips the image still gets a readable result, and the text is what actually gets
     * pasted into a group chat.
     */
    fun caption(result: DailyResult): String = buildString {
        append("DO NOT BLINK · TRIAL ")
        append(LocalDate.ofEpochDay(result.epochDay))
        append('\n')
        append(result.grid())
        append("  ")
        append(result.hits)
        append('/')
        append(DailyTrial.RINGS)
        append('\n')
        append("MEAN ERROR ")
        append(result.meanAbsErrorMs)
        append("MS")
    }

    /**
     * Writes the card into the app's own cache and hands back a content:// URI a receiving app may
     * read. Returns null if anything at all went wrong — a share that cannot be built is a share
     * button that does nothing, never a crash.
     */
    fun writeForSharing(context: Context, bitmap: Bitmap, result: DailyResult): android.net.Uri? =
        runCatching {
            // One fixed directory, cleared each time: the cache must not accumulate a PNG per day
            // forever, and the player never sees these files.
            val directory = File(context.cacheDir, SHARE_DIR)
            directory.deleteRecursively()
            directory.mkdirs()
            val file = File(directory, "do-not-blink-${result.epochDay}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        }.getOrNull()

    /** Builds the chooser. Null [imageUri] degrades to a text-only share rather than failing. */
    fun shareIntent(result: DailyResult, imageUri: android.net.Uri?): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, caption(result))
            if (imageUri == null) {
                type = "text/plain"
            } else {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private const val SHARE_DIR = "shares"
}

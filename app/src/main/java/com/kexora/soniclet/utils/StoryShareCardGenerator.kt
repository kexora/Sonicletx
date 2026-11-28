package com.kexora.soniclet.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.Path
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.DrawableCompat
import com.kexora.soniclet.R
import com.kexora.soniclet.database.Song
import java.io.File
import java.io.FileOutputStream

object StoryShareCardGenerator {
    fun generateAndShare(context: Context, song: Song, lyrics: String? = null) {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 1. Draw Background
        val bgPaint = Paint().apply { color = Color.parseColor("#121212") }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // 2. Load strict per-song album art (custom or track's own embedded picture)
        val albumArt: Bitmap? = CustomArtworkManager.loadStrictSongArtworkBitmap(
            context = context,
            songId = song.id,
            audioUri = song.contentUri,
            maxDimPx = 800,
            preferredConfig = Bitmap.Config.ARGB_8888
        )
        
        // 3. Draw Album Art
        val artSize = 800f
        val artX = (width - artSize) / 2f
        val artY = (height - artSize) / 2f - 200f
        
        canvas.save()
        val path = Path()
        path.addRoundRect(RectF(artX, artY, artX + artSize, artY + artSize), 40f, 40f, Path.Direction.CW)
        canvas.clipPath(path)
        
        if (albumArt != null) {
            val scaledArt = Bitmap.createScaledBitmap(albumArt, artSize.toInt(), artSize.toInt(), true)
            canvas.drawBitmap(scaledArt, artX, artY, null)
        } else {
            val bgP = Paint().apply {
                isAntiAlias = true
                shader = android.graphics.LinearGradient(
                    artX, artY, artX, artY + artSize,
                    Color.parseColor("#242428"),
                    Color.parseColor("#131315"),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(RectF(artX, artY, artX + artSize, artY + artSize), 40f, 40f, bgP)

            val grooveP = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = Color.argb(14, 255, 255, 255)
                strokeWidth = 3f
            }
            val cx = artX + artSize / 2f
            val cy = artY + artSize / 2f
            canvas.drawCircle(cx, cy, artSize * 0.44f, grooveP)
            grooveP.strokeWidth = 2f
            canvas.drawCircle(cx, cy, artSize * 0.32f, grooveP)

            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_soniclet_app_logo)
            if (drawable != null) {
                val iconSize = (artSize * 0.52f).toInt()
                val left = (artX + (artSize - iconSize) / 2).toInt()
                val top = (artY + (artSize - iconSize) / 2).toInt()
                drawable.setBounds(left, top, left + iconSize, top + iconSize)
                DrawableCompat.setTint(drawable, Color.parseColor("#EDEDF2"))
                drawable.draw(canvas)
            }
        }
        canvas.restore()
        
        // 4. Draw Title & Artist
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 70f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B3B3B3")
            textSize = 50f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        
        val textY = artY + artSize + 120f
        canvas.drawText(song.title, width / 2f, textY, titlePaint)
        canvas.drawText(song.artist, width / 2f, textY + 80f, artistPaint)
        
        // Draw lyrics if provided
        if (!lyrics.isNullOrBlank()) {
            val lyricsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 55f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("\"$lyrics\"", width / 2f, textY + 220f, lyricsPaint)
        }

        // 5. Draw Watermark
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E676")
            textSize = 45f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Soniclet", width / 2f, height - 120f, watermarkPaint)
        
        // Save and Share
        try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val file = File(cachePath, "story_share.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Story to..."))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

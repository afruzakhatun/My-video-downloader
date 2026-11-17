package com.afruza.videodownloader

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var btn: Button
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "ভিডিও ডাউনলোডার"
        title.textSize = 24f
        root.addView(title)

        urlInput = EditText(this)
        urlInput.hint = "ভিডিওর লিংক এখানে পেস্ট করুন"
        root.addView(urlInput)

        btn = Button(this)
        btn.text = "ডাউনলোড"
        root.addView(btn)

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        root.addView(bar)

        status = TextView(this)
        status.text = "অ্যাপ প্রস্তুত হচ্ছে, একটু অপেক্ষা করুন..."
        root.addView(status)

        setContentView(root)

        btn.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isEmpty()) {
                status.text = "আগে লিংক পেস্ট করুন"
            } else if (!ready) {
                status.text = "অ্যাপ এখনো প্রস্তুত হচ্ছে, কিছুক্ষণ পরে চেষ্টা করুন"
            } else {
                startDownload(url)
            }
        }

        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread { status.text = "প্রস্তুত। লিংক দিয়ে ডাউনলোড চাপুন।" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "শুরু করতে সমস্যা: " + e.message }
            }
        }.start()
    }

    private fun startDownload(url: String) {
        btn.isEnabled = false
        bar.progress = 0
        status.text = "ডাউনলোড শুরু হচ্ছে..."
        Thread {
            try {
                val dir = File(getExternalFilesDir(null), "dl")
                dir.deleteRecursively()
                dir.mkdirs()

                val req = YoutubeDLRequest(url)
                req.addOption("-f", "b[ext=mp4]/b")
                req.addOption("--no-playlist")
                req.addOption("-o", dir.absolutePath + "/%(title).60s.%(ext)s")

                YoutubeDL.getInstance().execute(req) { p, _, _ ->
                    runOnUiThread {
                        bar.progress = p.toInt().coerceIn(0, 100)
                        status.text = "ডাউনলোড হচ্ছে... " + p.toInt().coerceIn(0, 100) + "%"
                    }
                }

                val video = dir.listFiles()?.firstOrNull {
                    it.isFile && it.extension.lowercase() in listOf("mp4", "mkv", "webm", "mov")
                }
                if (video == null) {
                    runOnUiThread { status.text = "ভিডিও ফাইল পাওয়া যায়নি"; btn.isEnabled = true }
                    return@Thread
                }

                runOnUiThread { status.text = "সেরা মুহূর্তের ছবি বাছা হচ্ছে..." }
                val image = File(dir, video.nameWithoutExtension + ".jpg")
                val gotImage = makeBestFrame(video, image)

                val vPlace = saveMedia(video, "video/mp4", Environment.DIRECTORY_MOVIES)
                var msg = "সম্পন্ন! ভিডিও: " + vPlace
                if (gotImage) {
                    val iPlace = saveMedia(image, "image/jpeg", Environment.DIRECTORY_PICTURES)
                    msg += "\nছবি: " + iPlace
                } else {
                    msg += "\n(ছবি বানানো যায়নি)"
                }
                runOnUiThread { bar.progress = 100; status.text = msg; btn.isEnabled = true }
            } catch (e: Exception) {
                runOnUiThread { status.text = "সমস্যা: " + e.message; btn.isEnabled = true }
            }
        }.start()
    }

    private fun makeBestFrame(video: File, out: File): Boolean {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(video.absolutePath)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return false
            var bestT = durMs * 500L
            var best = -1.0
            for (i in 1..12) {
                val t = durMs * 1000L * (5 + i * 7) / 100
                val bmp = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                val s = score(bmp)
                if (s > best) {
                    best = s
                    bestT = t
                }
                bmp.recycle()
            }
            val frame = r.getFrameAtTime(bestT, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return false
            out.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            return true
        } catch (e: Exception) {
            return false
        } finally {
            r.release()
        }
    }

    private fun score(b: Bitmap): Double {
        val w = 96
        val h = (b.height * w / b.width).coerceAtLeast(2)
        val s = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h)
        s.getPixels(px, 0, w, 0, 0, w, h)
        s.recycle()
        fun lum(c: Int): Double =
            0.299 * ((c shr 16) and 255) + 0.587 * ((c shr 8) and 255) + 0.114 * (c and 255)
        var sharp = 0.0
        var bright = 0.0
        for (y in 0 until h - 1) {
            for (x in 0 until w - 1) {
                val l = lum(px[y * w + x])
                sharp += Math.abs(l - lum(px[y * w + x + 1])) + Math.abs(l - lum(px[(y + 1) * w + x]))
                bright += l
            }
        }
        val avg = bright / ((w - 1) * (h - 1))
        val penalty = if (avg < 40 || avg > 220) 0.3 else 1.0
        return sharp * penalty
    }

    private fun saveMedia(src: File, mime: String, folder: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, folder + "/VideoDownloader")
            val collection = if (mime.startsWith("video"))
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = contentResolver.insert(collection, values) ?: return src.absolutePath
            contentResolver.openOutputStream(uri)?.use { o ->
                src.inputStream().use { it.copyTo(o) }
            }
            return folder + "/VideoDownloader"
        }
        return src.absolutePath
    }
}

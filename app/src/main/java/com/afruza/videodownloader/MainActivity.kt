package com.afruza.videodownloader

import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var pasteBtn: Button
    private lateinit var preview: LinearLayout
    private lateinit var thumb: ImageView
    private lateinit var titleText: TextView
    private lateinit var infoText: TextView
    private lateinit var btn: Button
    private lateinit var status: TextView
    private lateinit var bar: ProgressBar

    @Volatile private var ready = false
    private var pendingUrl: String? = null
    private var currentUrl = ""
    private var fetchId = 0
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(20))

        val title = TextView(this)
        title.text = "ভিডিও ডাউনলোডার"
        title.textSize = 24f
        root.addView(title)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        urlInput = EditText(this)
        urlInput.hint = "ভিডিওর লিংক এখানে পেস্ট করুন"
        row.addView(urlInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pasteBtn = Button(this)
        pasteBtn.text = "পেস্ট"
        row.addView(pasteBtn)
        root.addView(row)

        preview = LinearLayout(this)
        preview.orientation = LinearLayout.VERTICAL
        preview.visibility = View.GONE
        thumb = ImageView(this)
        thumb.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.addView(thumb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(200)))
        titleText = TextView(this)
        titleText.textSize = 16f
        preview.addView(titleText)
        infoText = TextView(this)
        preview.addView(infoText)
        root.addView(preview)

        btn = Button(this)
        btn.text = "ডাউনলোড"
        btn.isEnabled = false
        root.addView(btn)

        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        root.addView(bar)

        status = TextView(this)
        status.text = "অ্যাপ প্রস্তুত হচ্ছে, একটু অপেক্ষা করুন..."
        root.addView(status)

        setContentView(root)

        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val m = Regex("https?://\\S+").find(s.toString())
                val t = m?.value ?: ""
                handler.removeCallbacksAndMessages(null)
                if (t.isNotEmpty() && t != currentUrl) {
                    handler.postDelayed({ requestInfo(t) }, 600)
                }
            }
        })

        pasteBtn.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (!txt.isNullOrEmpty()) {
                urlInput.setText(txt)
            } else {
                status.text = "কপি করা কোনো লিংক নেই"
            }
        }

        btn.setOnClickListener {
            val m = Regex("https?://\\S+").find(urlInput.text.toString())
            val url = m?.value ?: ""
            if (url.isEmpty()) {
                status.text = "আগে লিংক পেস্ট করুন"
            } else {
                startDownload(url)
            }
        }

        Thread {
            try {
                YoutubeDL.getInstance().init(application)
                FFmpeg.getInstance().init(application)
                ready = true
                runOnUiThread {
                    status.text = "প্রস্তুত। লিংক পেস্ট করুন।"
                    val p = pendingUrl
                    if (p != null) {
                        pendingUrl = null
                        requestInfo(p)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "শুরু করতে সমস্যা: " + e.message }
            }
        }.start()
    }

    private fun requestInfo(url: String) {
        currentUrl = url
        if (!ready) {
            pendingUrl = url
            status.text = "অ্যাপ প্রস্তুত হচ্ছে, তারপর লিংক দেখা হবে..."
            return
        }
        val id = ++fetchId
        preview.visibility = View.GONE
        btn.isEnabled = false
        bar.isIndeterminate = true
        status.text = "লিংক দেখা হচ্ছে..."
        Thread {
            try {
                val req = YoutubeDLRequest(url)
                req.addOption("--no-playlist")
                req.addOption("--socket-timeout", "15")
                val info = YoutubeDL.getInstance().getInfo(req)
                val title = info.title ?: ""
                val thumbUrl = info.thumbnail
                val dur = (info.duration as Number?)?.toLong() ?: 0L
                var bmp: Bitmap? = null
                if (!thumbUrl.isNullOrEmpty()) {
                    try {
                        bmp = BitmapFactory.decodeStream(URL(thumbUrl).openStream())
                    } catch (e: Exception) {
                    }
                }
                runOnUiThread {
                    if (id == fetchId) {
                        bar.isIndeterminate = false
                        titleText.text = title
                        infoText.text = if (dur > 0) "সময়: " + (dur / 60) + ":" + String.format("%02d", dur % 60) else ""
                        if (bmp != null) thumb.setImageBitmap(bmp) else thumb.setImageDrawable(null)
                        preview.visibility = View.VISIBLE
                        btn.isEnabled = true
                        status.text = "ডাউনলোড চাপুন"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (id == fetchId) {
                        bar.isIndeterminate = false
                        btn.isEnabled = true
                        status.text = "প্রিভিউ আনা যায়নি, তবু ডাউনলোড চেষ্টা করতে পারেন। (" + e.message + ")"
                    }
                }
            }
        }.start()
    }

    private fun startDownload(url: String) {
        btn.isEnabled = false
        bar.isIndeterminate = true
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
                req.addOption("-N", "8")
                req.addOption("--socket-timeout", "15")
                req.addOption("-o", dir.absolutePath + "/%(title).60s.%(ext)s")

                YoutubeDL.getInstance().execute(req) { p, _, _ ->
                    runOnUiThread {
                        if (p >= 0) {
                            bar.isIndeterminate = false
                            bar.progress = p.toInt().coerceIn(0, 100)
                            status.text = "ডাউনলোড হচ্ছে... " + p.toInt().coerceIn(0, 100) + "%"
                        }
                    }
                }

                val video = dir.listFiles()?.firstOrNull {
                    it.isFile && it.extension.lowercase() in listOf("mp4", "mkv", "webm", "mov")
                }
                if (video == null) {
                    runOnUiThread {
                        bar.isIndeterminate = false
                        status.text = "ভিডিও ফাইল পাওয়া যায়নি"
                        btn.isEnabled = true
                    }
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
                runOnUiThread {
                    bar.isIndeterminate = false
                    bar.progress = 100
                    status.text = msg
                    btn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    bar.isIndeterminate = false
                    status.text = "সমস্যা: " + e.message
                    btn.isEnabled = true
                }
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

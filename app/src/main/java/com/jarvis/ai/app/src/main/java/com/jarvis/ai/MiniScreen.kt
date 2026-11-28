package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors
import kotlin.math.min

// ---------- कोणत्या कमांडला कोणती स्क्रीन ----------

class MiniCmd(
    val kind: Int,
    val app: String,
    val query: String,
    val play: Boolean,
    val folder: String,
    val wantVideo: Boolean,
    val wantImage: Boolean,
    val latest: Boolean
)

private class WebSpec(val app: String, val names: List<String>, val url: String, val home: String)

private val MINI_WEB = listOf(
    WebSpec(
        "YouTube",
        listOf("youtube", "यूट्यूब", "युट्यूब", "यु ट्यूब", "यूट्युब", "युट्युब", "यु-ट्यूब"),
        "https://m.youtube.com/results?search_query=", "https://m.youtube.com"
    ),
    WebSpec(
        "Maps", listOf("maps", "मॅप", "मॅप्स", "नकाशा"),
        "https://www.google.com/maps/search/", "https://www.google.com/maps"
    ),
    WebSpec(
        "Translate", listOf("translate", "ट्रान्सलेट", "भाषांतर"),
        "https://translate.google.com/?sl=auto&tl=mr&op=translate&text=", "https://translate.google.com"
    ),
    WebSpec(
        "Play Store", listOf("play store", "playstore", "प्ले स्टोअर", "प्लेस्टोअर"),
        "https://play.google.com/store/search?c=apps&q=", "https://play.google.com/store"
    ),
    WebSpec("Amazon", listOf("amazon", "अमेझॉन", "ऍमेझॉन"), "https://www.amazon.in/s?k=", "https://www.amazon.in"),
    WebSpec("Meesho", listOf("meesho", "मीशो", "मिशो"), "https://www.meesho.com/search?q=", "https://www.meesho.com"),
    WebSpec(
        "Pinterest", listOf("pinterest", "पिंटरेस्ट"),
        "https://www.pinterest.com/search/pins/?q=", "https://www.pinterest.com"
    ),
    WebSpec(
        "Google", listOf("google", "गुगल", "गूगल", "chrome", "क्रोम", "browser", "ब्राउझर", "ब्राऊझर"),
        "https://www.google.com/search?q=", "https://www.google.com"
    )
)

private val MINI_CMD = listOf(
    "open", "launch", "उघड", "ओपन", "लाँच", "खोल", "play", "लाव", "लावा", "चालू", "वाजव", "search", "सर्च",
    "शोध", "दाखव", "बघ", "show", "find", "kholo", "chalu", "सुरू", "लगाव"
)
private val MINI_PLAY = listOf("play", "लाव", "लावा", "वाजव", "चालू", "लगाव", "chalu")
private val MINI_GALLERY_KEYS = listOf(
    "gallery", "गॅलरी", "गैलरी", "album", "अल्बम", "folder", "फोल्डर", "photos", "फोटो", "फोटोज"
)
private val MINI_GALLERY_ALL = listOf(
    "gallery", "गॅलरी", "गैलरी", "album", "अल्बम", "folder", "फोल्डर", "photos", "फोटो", "फोटोज", "photo",
    "video", "व्हिडिओ", "व्हिडीओ", "विडिओ", "विडीओ", "images", "image", "pictures", "picture", "पिक्चर", "इमेज", "reel", "रील"
)
private val MINI_VIDEO = listOf("video", "व्हिडिओ", "व्हिडीओ", "विडिओ", "विडीओ", "reel", "रील")
private val MINI_PHOTO = listOf("photo", "फोटो", "image", "इमेज", "picture", "पिक्चर", "pic")
private val MINI_LATEST = listOf("latest", "last", "recent", "नवीन", "शेवटचा", "शेवटची", "ताजा")
private val MINI_NOISE = setOf(
    "वर", "वरती", "वरून", "var", "varti", "मध्ये", "madhye", "मधला", "मधील", "मधली", "मधून", "मधे", "साठी",
    "for", "on", "in", "from", "of", "the", "a", "please", "plz", "pls", "jarvis", "जार्विस", "जारविस", "सर", "sir",
    "hey", "ok", "okay", "कर", "करा", "kar", "करून", "दे", "द्या", "ला", "ना", "मला", "माझ्या", "my", "me",
    "चे", "चा", "ची", "च्या", "चं", "che", "cha", "chi", "एक", "तो", "ते", "ती", "this", "that", "to"
)

fun jarvisParseMini(t: String): MiniCmd? {
    if (gHasAny(t, G_QUESTION)) return null
    if (!gHasAny(t, MINI_CMD)) return null
    val words = gWords(t)

    for (spec in MINI_WEB) {
        if (gHasAny(t, spec.names)) {
            val keep = ArrayList<String>()
            for (w in words) {
                if (gHasAny(w, spec.names) || gHasAny(w, MINI_CMD) || MINI_NOISE.contains(w) || G_STOP.contains(w)) continue
                keep.add(w)
            }
            val play = spec.app == "YouTube" && gHasAny(t, MINI_PLAY)
            return MiniCmd(1, spec.app, keep.joinToString(" "), play, "", false, false, false)
        }
    }

    if (gHasAny(t, MINI_GALLERY_KEYS)) {
        var video = gHasAny(t, MINI_VIDEO)
        var photo = gHasAny(t, MINI_PHOTO)
        if (!video && !photo) {
            video = true
            photo = true
        }
        val keep = ArrayList<String>()
        for (w in words) {
            if (gHasAny(w, MINI_GALLERY_ALL) || gHasAny(w, MINI_CMD) || MINI_NOISE.contains(w) || G_STOP.contains(w)) continue
            keep.add(w)
        }
        return MiniCmd(2, "Gallery", "", false, keep.joinToString(" "), video, photo, gHasAny(t, MINI_LATEST))
    }
    return null
}

fun jarvisRunMini(act: Activity, cmd: MiniCmd, say: (String) -> Unit, mr: Boolean) {
    if (cmd.kind == 1) {
        runWeb(act, cmd, say, mr)
    } else {
        runGallery(act, cmd, say, mr)
    }
}

// ---------- चिन्हे ----------

private class MiniIcon(private val type: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        val s = min(b.width(), b.height()) / 24f
        canvas.save()
        canvas.translate(b.left + (b.width() - 24f * s) / 2f, b.top + (b.height() - 24f * s) / 2f)
        canvas.scale(s, s)
        p.color = Color.parseColor("#C9D6E4")
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = 2f
        when (type) {
            0 -> {
                canvas.drawLine(6f, 6f, 18f, 18f, p)
                canvas.drawLine(18f, 6f, 6f, 18f, p)
            }
            1 -> {
                canvas.drawLine(6f, 15f, 12f, 9f, p)
                canvas.drawLine(12f, 9f, 18f, 15f, p)
            }
            2 -> {
                canvas.drawLine(6f, 9f, 12f, 15f, p)
                canvas.drawLine(12f, 15f, 18f, 9f, p)
            }
            else -> {
                canvas.drawLine(19f, 12f, 5f, 12f, p)
                canvas.drawLine(5f, 12f, 11f, 6f, p)
                canvas.drawLine(5f, 12f, 11f, 18f, p)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        p.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        p.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun miniIconBtn(b: Button, type: Int, d: Float) {
    b.setAllCaps(false)
    b.minWidth = 0
    b.minHeight = 0
    b.minimumWidth = 0
    b.minimumHeight = 0
    b.stateListAnimator = null
    b.text = ""
    b.setPadding(0, 0, 0, 0)
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(Color.TRANSPARENT)
    val layers = LayerDrawable(arrayOf<Drawable>(g, MiniIcon(type)))
    val inset = (9f * d).toInt()
    layers.setLayerInset(1, inset, inset, inset, inset)
    val mask = GradientDrawable()
    mask.shape = GradientDrawable.OVAL
    mask.setColor(Color.WHITE)
    b.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), layers, mask)
}

// ---------- फोटो / व्हिडिओ ----------

private class MiniItem(val uri: Uri, val name: String, val isVideo: Boolean, val added: Long) {
    val key: String = uri.toString()
}

private val thumbPool = Executors.newFixedThreadPool(3)

private class MediaAdapter(
    private val act: Activity,
    private val items: List<MiniItem>,
    private val cell: Int
) : BaseAdapter() {
    private val cache = LruCache<String, Bitmap>(80)
    private val ui = Handler(Looper.getMainLooper())

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val cellView: FrameLayout
        val iv: ImageView
        val badge: TextView
        if (convertView is FrameLayout) {
            cellView = convertView
            iv = cellView.getChildAt(0) as ImageView
            badge = cellView.getChildAt(1) as TextView
        } else {
            cellView = FrameLayout(act)
            cellView.layoutParams = AbsListView.LayoutParams(cell, cell)
            iv = ImageView(act)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setBackgroundColor(Color.parseColor("#1A222C"))
            cellView.addView(iv, FrameLayout.LayoutParams(cell, cell))
            badge = TextView(act)
            badge.textSize = 10f
            badge.setTextColor(Color.WHITE)
            badge.setBackgroundColor(Color.parseColor("#99000000"))
            badge.setPadding(6, 2, 6, 2)
            cellView.addView(
                badge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                )
            )
        }
        val item = items[position]
        badge.text = "VIDEO"
        badge.visibility = if (item.isVideo) View.VISIBLE else View.GONE
        iv.tag = item.key
        iv.setImageBitmap(null)
        val cached = cache.get(item.key)
        if (cached != null) {
            iv.setImageBitmap(cached)
        } else if (Build.VERSION.SDK_INT >= 29) {
            thumbPool.execute {
                try {
                    val bmp = act.contentResolver.loadThumbnail(item.uri, Size(cell, cell), null)
                    cache.put(item.key, bmp)
                    ui.post {
                        if (iv.tag == item.key) {
                            iv.setImageBitmap(bmp)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
        return cellView
    }
}

// ---------- खालून वर येणारी एक-हाती स्क्रीन ----------

private class MiniSheet(private val act: Activity, title: String) {
    private val d = act.resources.displayMetrics.density
    private val screenH = act.resources.displayMetrics.heightPixels
    val root = FrameLayout(act)
    private val sheet = LinearLayout(act)
    private val titleView = TextView(act)
    private val subView = TextView(act)
    private val body = FrameLayout(act)
    private val chipsBox = LinearLayout(act)
    private val chipsScroll = HorizontalScrollView(act)
    private val progress = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
    private val backBtn = Button(act)
    private val expandBtn = Button(act)
    private var expanded = false
    private var web: WebView? = null
    private var video: VideoView? = null
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var clicked = false
    private var backFn: (() -> Unit)? = null

    private fun dp(v: Int): Int = (v * d + 0.5f).toInt()

    init {
        root.setBackgroundColor(Color.parseColor("#B3000000"))
        root.isClickable = true
        root.isFocusableInTouchMode = true
        root.setOnClickListener { close() }
        root.setOnKeyListener { _, keyCode, ev ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (ev.action == KeyEvent.ACTION_UP) {
                    onBack()
                }
                true
            } else {
                false
            }
        }

        sheet.orientation = LinearLayout.VERTICAL
        sheet.isClickable = true
        val r = 24f * d
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#10151C"))
        bg.cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        sheet.background = bg

        val handle = View(act)
        val hb = GradientDrawable()
        hb.setColor(Color.parseColor("#3A4452"))
        hb.cornerRadius = 4f * d
        handle.background = hb
        val hp = LinearLayout.LayoutParams(dp(40), dp(4))
        hp.gravity = Gravity.CENTER_HORIZONTAL
        hp.setMargins(0, dp(8), 0, dp(4))
        sheet.addView(handle, hp)

        val header = LinearLayout(act)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(12), dp(2), dp(8), dp(2))
        miniIconBtn(backBtn, 3, d)
        backBtn.visibility = View.GONE
        backBtn.setOnClickListener { onBack() }
        titleView.text = title
        titleView.textSize = 18f
        titleView.setTextColor(Color.WHITE)
        subView.textSize = 13f
        subView.setTextColor(Color.parseColor("#8FA3B8"))
        subView.maxLines = 1
        subView.ellipsize = android.text.TextUtils.TruncateAt.END
        val col = LinearLayout(act)
        col.orientation = LinearLayout.VERTICAL
        col.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(subView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        miniIconBtn(expandBtn, 1, d)
        expandBtn.setOnClickListener { toggleExpand() }
        val closeBtn = Button(act)
        miniIconBtn(closeBtn, 0, d)
        closeBtn.setOnClickListener { close() }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(expandBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        sheet.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        chipsBox.orientation = LinearLayout.HORIZONTAL
        chipsBox.setPadding(dp(12), dp(4), dp(12), dp(8))
        chipsScroll.isHorizontalScrollBarEnabled = false
        chipsScroll.addView(chipsBox, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        chipsScroll.visibility = View.GONE
        sheet.addView(chipsScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        sheet.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        progress.max = 100
        progress.visibility = View.GONE

        root.addView(
            sheet,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (screenH * 0.68f).toInt(), Gravity.BOTTOM)
        )
    }

    fun show() {
        val content = act.findViewById<ViewGroup>(android.R.id.content)
        content.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.requestFocus()
    }

    fun close() {
        try {
            (root.parent as? ViewGroup)?.removeView(root)
        } catch (e: Exception) {
        }
        try {
            video?.stopPlayback()
        } catch (e: Exception) {
        }
        video = null
        val w = web
        web = null
        if (w != null) {
            try {
                w.stopLoading()
                w.loadUrl("about:blank")
                w.destroy()
            } catch (e: Exception) {
            }
        }
    }

    private fun toggleExpand() {
        expanded = !expanded
        val lp = sheet.layoutParams as FrameLayout.LayoutParams
        lp.height = (screenH * (if (expanded) 0.92f else 0.68f)).toInt()
        sheet.layoutParams = lp
        miniIconBtn(expandBtn, if (expanded) 2 else 1, d)
    }

    private fun hideCustom() {
        val v = customView
        if (v != null) {
            root.removeView(v)
        }
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
    }

    private fun onBack() {
        if (customView != null) {
            hideCustom()
            return
        }
        val fn = backFn
        if (fn != null) {
            backBtn.visibility = View.GONE
            backFn = null
            fn()
            return
        }
        val w = web
        if (w != null && w.canGoBack()) {
            w.goBack()
            return
        }
        close()
    }

    fun setSubtitle(s: String) {
        subView.text = s
    }

    fun setChips(items: List<String>, onTap: (String) -> Unit) {
        chipsBox.removeAllViews()
        if (items.isEmpty()) {
            chipsScroll.visibility = View.GONE
            return
        }
        val label = TextView(act)
        label.text = "मागचे सर्च:"
        label.textSize = 12f
        label.setTextColor(Color.parseColor("#8FA3B8"))
        label.gravity = Gravity.CENTER_VERTICAL
        chipsBox.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        for (q in items) {
            val c = TextView(act)
            c.text = q
            c.textSize = 13f
            c.setTextColor(Color.parseColor("#C9D6E4"))
            c.setPadding(dp(12), dp(6), dp(12), dp(6))
            val cb = GradientDrawable()
            cb.setColor(Color.parseColor("#1A222C"))
            cb.cornerRadius = 100f * d
            c.background = cb
            c.setOnClickListener { onTap(q) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(6), 0, 0, 0)
            chipsBox.addView(c, lp)
        }
        chipsScroll.visibility = View.VISIBLE
    }

    private fun stopMedia() {
        try {
            video?.stopPlayback()
        } catch (e: Exception) {
        }
        video = null
    }

    fun showWeb(url: String, autoplay: Boolean) {
        stopMedia()
        body.removeAllViews()
        val old = web
        if (old != null) {
            try {
                old.stopLoading()
                old.destroy()
            } catch (e: Exception) {
            }
        }
        clicked = false
        val w = WebView(act)
        web = w
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.setBackgroundColor(Color.parseColor("#10151C"))
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                return !(u.startsWith("http://") || u.startsWith("https://"))
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (autoplay && !clicked && (url ?: "").contains("results")) {
                    clicked = true
                    view?.evaluateJavascript(
                        "(function(){var a=document.querySelector('a[href^=\"/watch\"]');if(a){a.click();}})();",
                        null
                    )
                }
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customCb = callback
                root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            override fun onHideCustomView() {
                hideCustom()
            }
        }
        body.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        (progress.parent as? ViewGroup)?.removeView(progress)
        body.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP))
        w.loadUrl(url)
    }

    fun showMedia(items: List<MiniItem>, openFirst: Boolean) {
        if (openFirst && items.isNotEmpty()) {
            showItem(items[0], items)
        } else {
            showGrid(items)
        }
    }

    private fun showGrid(items: List<MiniItem>) {
        stopMedia()
        body.removeAllViews()
        val gv = GridView(act)
        val cell = (act.resources.displayMetrics.widthPixels - dp(8)) / 3
        gv.numColumns = 3
        gv.columnWidth = cell
        gv.horizontalSpacing = dp(2)
        gv.verticalSpacing = dp(2)
        gv.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        gv.adapter = MediaAdapter(act, items, cell)
        gv.setOnItemClickListener { _, _, pos, _ -> showItem(items[pos], items) }
        body.addView(gv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showItem(item: MiniItem, items: List<MiniItem>) {
        stopMedia()
        body.removeAllViews()
        backFn = { showGrid(items) }
        backBtn.visibility = View.VISIBLE
        subView.text = item.name
        if (item.isVideo) {
            val v = VideoView(act)
            video = v
            val mc = MediaController(act)
            mc.setAnchorView(v)
            v.setMediaController(mc)
            v.setVideoURI(item.uri)
            v.setOnPreparedListener { mp -> mp.start() }
            v.setOnErrorListener { _, _, _ -> true }
            body.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        } else {
            val iv = ImageView(act)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setImageURI(item.uri)
            body.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }
}

private val miniSheets = WeakHashMap<Activity, MiniSheet>()

private fun miniClose(act: Activity) {
    val s = miniSheets.remove(act)
    if (s != null) {
        s.close()
    }
}

// ---------- सर्च इतिहास (Incognito मध्ये सेव होत नाही) ----------

private fun miniIncognito(act: Activity): Boolean {
    val n = act.getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("notes", "") ?: ""
    return n.contains("INCOGNITO:")
}

private fun miniHist(act: Activity, app: String): List<String> {
    return try {
        val o = JSONObject(act.getSharedPreferences("jarvis", Context.MODE_PRIVATE).getString("mini_hist", "{}") ?: "{}")
        val a = o.optJSONArray(app) ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            out.add(a.getString(i))
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}

private fun miniSaveHist(act: Activity, app: String, q: String) {
    if (miniIncognito(act)) return
    try {
        val sp = act.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val o = JSONObject(sp.getString("mini_hist", "{}") ?: "{}")
        val old = o.optJSONArray(app)
        val list = ArrayList<String>()
        list.add(q)
        if (old != null) {
            for (i in 0 until old.length()) {
                val s = old.getString(i)
                if (!s.equals(q, true) && list.size < 8) list.add(s)
            }
        }
        o.put(app, JSONArray(list))
        sp.edit().putString("mini_hist", o.toString()).apply()
    } catch (e: Exception) {
    }
}

// ---------- वेबवरचे apps ----------

private fun openWebQuery(act: Activity, sheet: MiniSheet, sp: WebSpec, q: String, play: Boolean) {
    val url = if (q.isBlank()) sp.home else sp.url + Uri.encode(q)
    sheet.setSubtitle(if (q.isBlank()) "होम" else "सर्च: " + q)
    sheet.showWeb(url, play && q.isNotBlank())
    if (q.isNotBlank()) {
        miniSaveHist(act, sp.app, q)
    }
    sheet.setChips(miniHist(act, sp.app)) { c -> openWebQuery(act, sheet, sp, c, false) }
}

private fun runWeb(act: Activity, cmd: MiniCmd, say: (String) -> Unit, mr: Boolean) {
    var found: WebSpec? = null
    for (s in MINI_WEB) {
        if (s.app == cmd.app) found = s
    }
    val sp = found ?: return
    miniClose(act)
    val sheet = MiniSheet(act, sp.app)
    miniSheets[act] = sheet
    sheet.show()
    openWebQuery(act, sheet, sp, cmd.query, cmd.play)
    if (cmd.query.isBlank()) {
        say(if (mr) "ठीक आहे सर, " + sp.app + " दाखवतो." else "Okay Sir, showing " + sp.app + ".")
    } else {
        say(
            if (mr) "ठीक आहे सर, " + sp.app + " वर \"" + cmd.query + "\" दाखवतो."
            else "Okay Sir, showing \"" + cmd.query + "\" on " + sp.app + "."
        )
    }
}

// ---------- गॅलरी ----------

private fun miniPerms(): Array<String> {
    return if (Build.VERSION.SDK_INT >= 33) {
        arrayOf("android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_IMAGES")
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun miniEnsure(act: Activity, perms: Array<String>, onOk: () -> Unit, onFail: () -> Unit) {
    fun missing(): List<String> {
        val m = ArrayList<String>()
        for (p in perms) {
            if (act.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) m.add(p)
        }
        return m
    }
    if (missing().isEmpty()) {
        onOk()
        return
    }
    try {
        act.requestPermissions(missing().toTypedArray(), 4401)
    } catch (e: Exception) {
    }
    val h = Handler(Looper.getMainLooper())
    val start = System.currentTimeMillis()
    val r = object : Runnable {
        override fun run() {
            if (missing().isEmpty()) {
                onOk()
            } else if (System.currentTimeMillis() - start > 30000L) {
                onFail()
            } else {
                h.postDelayed(this, 400L)
            }
        }
    }
    h.postDelayed(r, 800L)
}

private fun miniBuckets(act: Activity, video: Boolean, image: Boolean): LinkedHashMap<String, Int> {
    val counts = HashMap<String, Int>()
    fun scan(base: Uri, col: String) {
        var c: Cursor? = null
        try {
            c = act.contentResolver.query(base, arrayOf(col), null, null, null)
            if (c != null) {
                var n = 0
                while (c.moveToNext() && n < 30000) {
                    n++
                    val b = c.getString(0) ?: continue
                    counts[b] = (counts[b] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
        } finally {
            try {
                c?.close()
            } catch (e: Exception) {
            }
        }
    }
    if (video) scan(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
    if (image) scan(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
    val out = LinkedHashMap<String, Int>()
    for (e in counts.entries.sortedByDescending { it.value }) {
        out[e.key] = e.value
    }
    return out
}

private fun miniMatchBuckets(names: List<String>, query: String): List<String> {
    val qLow = query.lowercase(Locale.ROOT).trim()
    val qSk = gSkeleton(qLow)
    var best = 99
    val hits = ArrayList<String>()
    for (n in names) {
        val nl = n.lowercase(Locale.ROOT)
        val sk = gSkeleton(nl)
        val score = when {
            nl == qLow -> 0
            sk.isNotEmpty() && sk == qSk -> 1
            qLow.length >= 3 && nl.contains(qLow) -> 1
            qSk.length >= 3 && sk.contains(qSk) -> 2
            qSk.length >= 4 && sk.isNotEmpty() && gLev(sk, qSk) <= 1 -> 3
            else -> 99
        }
        if (score < best) {
            best = score
            hits.clear()
        }
        if (score == best && score < 99) hits.add(n)
    }
    return hits
}

private fun miniAddFrom(act: Activity, base: Uri, video: Boolean, bucket: String, out: MutableList<MiniItem>) {
    var c: Cursor? = null
    try {
        val bucketCol = if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        c = act.contentResolver.query(
            base,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED),
            bucketCol + " = ?",
            arrayOf(bucket),
            MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )
        if (c != null) {
            var n = 0
            while (c.moveToNext() && n < 120) {
                n++
                val id = c.getLong(0)
                out.add(MiniItem(ContentUris.withAppendedId(base, id), c.getString(1) ?: "", video, c.getLong(2)))
            }
        }
    } catch (e: Exception) {
    } finally {
        try {
            c?.close()
        } catch (e: Exception) {
        }
    }
}

private fun miniMedia(act: Activity, bucket: String, video: Boolean, image: Boolean): List<MiniItem> {
    val out = ArrayList<MiniItem>()
    if (video) miniAddFrom(act, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, bucket, out)
    if (image) miniAddFrom(act, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, bucket, out)
    out.sortByDescending { it.added }
    return if (out.size > 90) ArrayList(out.subList(0, 90)) else out
}

private fun openBucket(act: Activity, bucket: String, cmd: MiniCmd, say: (String) -> Unit, mr: Boolean) {
    val items = miniMedia(act, bucket, cmd.wantVideo, cmd.wantImage)
    if (items.isEmpty()) {
        say(if (mr) "सर, त्या फोल्डरमध्ये काही सापडलं नाही." else "Sir, nothing found in that folder.")
        return
    }
    miniClose(act)
    val sheet = MiniSheet(act, bucket)
    miniSheets[act] = sheet
    sheet.show()
    sheet.setSubtitle(items.size.toString() + (if (mr) " फाईल्स" else " items"))
    val playNow = items[0].isVideo && (cmd.latest || (cmd.wantVideo && !cmd.wantImage && items.size == 1))
    sheet.showMedia(items, playNow)
    say(
        if (mr) "ठीक आहे सर, \"" + bucket + "\" फोल्डर उघडला."
        else "Okay Sir, opened the \"" + bucket + "\" folder."
    )
}

private fun chooseBucket(act: Activity, names: List<String>, counts: Map<String, Int>, cmd: MiniCmd, say: (String) -> Unit, mr: Boolean) {
    val shown = names.take(15)
    val labels = ArrayList<String>()
    for (n in shown) labels.add(n + "  (" + (counts[n] ?: 0) + ")")
    AlertDialog.Builder(act)
        .setTitle(if (mr) "कोणता फोल्डर?" else "Which folder?")
        .setItems(labels.toTypedArray()) { _, which -> openBucket(act, shown[which], cmd, say, mr) }
        .setNegativeButton("NO", null)
        .show()
}

private fun galleryProceed(act: Activity, cmd: MiniCmd, say: (String) -> Unit, mr: Boolean) {
    val counts = miniBuckets(act, cmd.wantVideo, cmd.wantImage)
    if (counts.isEmpty()) {
        say(if (mr) "सर, गॅलरीत काही सापडलं नाही." else "Sir, nothing found in the gallery.")
        return
    }
    val names = ArrayList<String>(counts.keys)
    if (cmd.folder.isBlank()) {
        chooseBucket(act, names, counts, cmd, say, mr)
        return
    }
    val hits = miniMatchBuckets(names, cmd.folder)
    if (hits.isEmpty()) {
        say(
            if (mr) "सर, \"" + cmd.folder + "\" नावाचा फोल्डर सापडला नाही. यादी दाखवतो."
            else "Sir, no folder named \"" + cmd.folder + "\". Showing the list."
        )
        chooseBucket(act, names, counts, cmd, say, mr)
    } else if (hits.size == 1) {
        openBucket(act, hits[0], cmd, say, mr)
    } else {
        chooseBucket(act, hits, counts, cmd, say, mr)
    }
}

private fun runGallery(act: Activity, cmd: MiniCmd, say: (String) -> Unit, mr: Boolean) {
    val label = if (cmd.folder.isBlank()) (if (mr) "गॅलरी" else "the gallery") else cmd.folder
    AlertDialog.Builder(act)
        .setTitle("Allow?")
        .setMessage(
            if (mr) "सर, गॅलरीतला \"" + label + "\" उघडू का?"
            else "Sir, open \"" + label + "\" from the gallery?"
        )
        .setPositiveButton("YES") { _, _ ->
            miniEnsure(act, miniPerms(), {
                galleryProceed(act, cmd, say, mr)
            }, {
                say(
                    if (mr) "सर, गॅलरीची परवानगी मिळाली नाही. सगळे फोटो आणि व्हिडिओ ची परवानगी द्या."
                    else "Sir, gallery permission was not given. Please allow all photos and videos."
                )
            })
        }
        .setNegativeButton("NO") { _, _ -> say(if (mr) "ठीक आहे सर, रद्द केलं." else "Okay Sir, cancelled.") }
        .show()
}

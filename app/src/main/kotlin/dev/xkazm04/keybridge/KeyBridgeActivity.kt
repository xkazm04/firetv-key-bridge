package dev.xkazm04.keybridge

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * One fullscreen WebView on a URL you choose. Every remote button in [KeyMap] goes to the page as a standard keyboard
 * event; Back goes to the page first and leaves the app only when the page does not take it (and the page has no
 * history to go back through), or when it is held.
 *
 * Point it once, either with the remote (the setup screen) or from a computer:
 *   adb shell am start -n dev.xkazm04.keybridge/.KeyBridgeActivity --es url "'https://example.com/app?x=1'"
 * The address is kept in SharedPreferences once the page has loaded. Other extras: `--ez reset true` forgets it,
 * `--es back backspace` sends Back as Backspace instead of Escape.
 */
class KeyBridgeActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var panel: LinearLayout
    private lateinit var title: TextView
    private lateinit var line: TextView
    private lateinit var body: TextView
    private lateinit var input: EditText
    private lateinit var row: LinearLayout
    private lateinit var footer: TextView
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null

    private val main = Handler(Looper.getMainLooper())
    private val backoff = Backoff()

    private enum class Mode { LOADING, WEB, ERROR, SETUP }
    private var mode = Mode.LOADING
    private var current: String? = null
    private var failed = false
    private var fromExtra = false
    private var why = ""
    private var cameFrom: Mode? = null
    private var backStyle = BackStyle.ESCAPE

    private var backDownAt = -1L
    private var backHeld = false

    private val loadTimeout = Runnable { if (mode == Mode.LOADING) fail(Reasons.describe(-8, null)) }
    private val retry = Runnable { if (mode == Mode.ERROR) current?.let { load(it, fromExtra, keepBackoff = true) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " FireTVKeyBridge/" + VERSION
            webChromeClient = object : WebChromeClient() {
                // video players ask for their own fullscreen view; give them the whole screen
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customView?.let { hideCustom() }
                    customView = view; customCallback = callback
                    root.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                override fun onHideCustomView() = hideCustom()
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(KeyMap.marker(VERSION), null)
                    if (mode == Mode.LOADING && !failed) {
                        mode = Mode.WEB
                        main.removeCallbacks(loadTimeout)
                        backoff.reset()
                        current?.let(::keep)
                        panel.visibility = View.GONE
                        web.requestFocus()
                        Log.i(TAG, "loaded ${Target.redact(current.orEmpty())}")
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) fail(Reasons.describe(error.errorCode, error.description?.toString()))
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (request.isForMainFrame && response.statusCode >= 500) fail(Reasons.http(response.statusCode))
                }
            }
        }

        fun text(sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(0f, 1.15f)
        }
        val kicker = text(16f, DIM, bold = true).apply { letterSpacing = 0.2f; text = "FIRE TV KEY BRIDGE" }
        title = text(34f, INK, bold = true)
        line = text(22f, AMBER).apply { typeface = Typeface.MONOSPACE }
        body = text(20f, INK)
        input = EditText(this).apply {
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(INK)
            setHintTextColor(DIM)
            hint = "https://your-app.example  or  10.0.0.5:3000/tv"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setPadding(32, 20, 32, 20)
            background = outline(false)
            setOnFocusChangeListener { v, f -> v.background = outline(f) }
            setOnEditorActionListener { _, _, _ -> connect(); true }
        }
        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        footer = text(16f, DIM)
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(PANEL)
            setPadding(120, 64, 120, 64)
            fun gap(v: View, top: Int) = addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = top })
            gap(kicker, 0); gap(title, 20); gap(line, 20); gap(body, 24); gap(input, 28); gap(row, 36); gap(footer, 36)
        }
        root = FrameLayout(this).apply {
            addView(web, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(panel, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        setContentView(root)
        point(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // the launcher tile on a running app only brings it forward; a new address or a reset acts
        if (intent.hasExtra(EXTRA_URL) || intent.hasExtra(EXTRA_RESET)) point(intent)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        web.destroy()
        super.onDestroy()
    }

    // -- which page

    private fun point(intent: Intent?) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (intent?.getBooleanExtra(EXTRA_RESET, false) == true) prefs.edit().remove(KEPT).apply()
        intent?.getStringExtra(EXTRA_BACK)?.let { prefs.edit().putString(BACK, it).apply() }
        backStyle = BackStyle.from(prefs.getString(BACK, null))
        when (val boot = Startup.decide(intent?.getStringExtra(EXTRA_URL), prefs.getString(KEPT, null))) {
            is Boot.Load -> load(boot.url, boot.fromExtra)
            is Boot.Setup -> showSetup(boot.note)
        }
    }

    private fun load(url: String, extra: Boolean, keepBackoff: Boolean = false) {
        main.removeCallbacks(retry)
        main.removeCallbacks(loadTimeout)
        if (!keepBackoff) backoff.reset()
        current = url; fromExtra = extra; failed = false; cameFrom = null
        mode = Mode.LOADING
        line.text = Target.label(url)
        Log.i(TAG, "loading ${Target.redact(url)}")
        if (Target.isDemo(url)) {
            val html = assets.open("index.html").bufferedReader().use { it.readText() }
            web.loadDataWithBaseURL("http://keybridge.demo/", html, "text/html", "utf-8", null)
        } else {
            web.loadUrl(url)
        }
        main.postDelayed(loadTimeout, LOAD_TIMEOUT_MS)
        // a page that is slow to start still shows the last frame; only a first load shows the splash
        title.text = "Loading"
        body.text = ""
        input.visibility = View.GONE
        row.removeAllViews()
        footer.text = "Back: leave"
        panel.visibility = if (web.url == null) View.VISIBLE else View.GONE
    }

    /** Only an address that loaded is kept, and never the built-in demo. */
    private fun keep(url: String) {
        if (Target.isDemo(url)) return
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEPT, url).apply()
    }

    private fun fail(reason: String) {
        if (mode == Mode.ERROR || mode == Mode.SETUP) return
        failed = true
        why = reason
        web.stopLoading()
        main.removeCallbacks(loadTimeout)
        val wait = backoff.nextMs()
        Log.i(TAG, "load failed: $reason; retrying in ${wait / 1000} s")
        mode = Mode.ERROR
        title.text = "Cannot open the page"
        line.text = Target.redact(current.orEmpty())
        body.text = "${reason.replaceFirstChar { it.uppercase() }}.\n\nThe app tries again by itself in ${wait / 1000} s."
        input.visibility = View.GONE
        row.removeAllViews()
        val retryNow = button("Retry now") { current?.let { load(it, fromExtra) } }
        row.addView(retryNow, chipParams())
        row.addView(button("Change address") { showSetup("") }, chipParams())
        footer.text = "Left/Right: choose    Select: press    Back: leave"
        panel.visibility = View.VISIBLE
        retryNow.requestFocus()
        main.postDelayed(retry, wait)
    }

    // -- setup

    private fun showSetup(note: String) {
        main.removeCallbacks(retry)
        main.removeCallbacks(loadTimeout)
        cameFrom = if (mode == Mode.ERROR || mode == Mode.WEB) mode else null
        mode = Mode.SETUP
        title.text = "Which page should open?"
        line.text = ""
        body.text = if (note.isEmpty()) "Type the address of your web app. Select on the box opens the keyboard." else note
        input.visibility = View.VISIBLE
        if (input.text.isNullOrEmpty()) input.setText(current ?: getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEPT, "").orEmpty())
        row.removeAllViews()
        row.addView(button("Open") { connect() }, chipParams())
        row.addView(button("Try the demo") { load(Target.DEMO_URL, false) }, chipParams())
        footer.text = "Up/Down: move    Select: press    Back: " + if (cameFrom != null) "return" else "leave"
        panel.visibility = View.VISIBLE
        input.requestFocus()
    }

    private fun connect() {
        val url = Target.parse(input.text?.toString())
        if (url == null) {
            body.text = "That is not an address this app can open. Use http:// or https://, or a host like 10.0.0.5:3000."
            input.requestFocus()
            return
        }
        load(url, false)
    }

    // -- screens' pieces

    private fun outline(lit: Boolean) = GradientDrawable().apply {
        cornerRadius = 18f
        setColor(CHIP)
        setStroke(if (lit) 6 else 3, if (lit) AMBER else DIM)
    }

    private fun button(label: String, onPress: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        setTextColor(INK)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(48, 20, 48, 20)
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = 18f; setColor(AMBER) })
            addState(intArrayOf(), outline(false))
        }
        setOnFocusChangeListener { v, f -> (v as Button).setTextColor(if (f) PANEL else INK) }
        setOnClickListener { onPress() }
    }

    private fun chipParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(20, 0, 20, 0) }

    private fun hideCustom() {
        customView?.let { root.removeView(it) }
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    // -- the remote

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        // a video player's fullscreen view: Back closes it before anything else
        if (customView != null && KeyMap.isBack(e.keyCode)) {
            if (e.action == KeyEvent.ACTION_UP) hideCustom()
            return true
        }
        if (mode == Mode.WEB) {
            if (KeyMap.isBack(e.keyCode)) return onBack(e)
            val k = KeyMap.pageKeyOf(e.keyCode) ?: return super.dispatchKeyEvent(e)
            if (e.action == KeyEvent.ACTION_DOWN) toPage(k, null)
            return true
        }
        if (KeyMap.isBack(e.keyCode)) {
            if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) {
                val back = cameFrom
                if (mode == Mode.SETUP && back == Mode.WEB) { Log.i(TAG, "setup: back"); mode = Mode.WEB; panel.visibility = View.GONE }
                else if (mode == Mode.SETUP && back == Mode.ERROR) current?.let { load(it, fromExtra) }
                else finish()
            }
            return true
        }
        // loading, error and setup screens use Android's own focus: the D-pad moves, Select presses
        return if (mode == Mode.LOADING) true else super.dispatchKeyEvent(e)
    }

    /** Back goes to the page on release; held (the system's long press, or BACK_HOLD_MS) it leaves at once. */
    private fun onBack(e: KeyEvent): Boolean {
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0) { backDownAt = e.eventTime; backHeld = false }
                else if (!backHeld && backDownAt >= 0 && (e.isLongPress || e.eventTime - backDownAt >= KeyMap.BACK_HOLD_MS)) {
                    backHeld = true
                    Log.i(TAG, "Back held: leaving")
                    finish()
                }
            }
            KeyEvent.ACTION_UP -> {
                val pressed = backDownAt >= 0 && !backHeld && !e.isCanceled
                backDownAt = -1L
                if (pressed) toPage(backStyle.pageKey) { taken ->
                    when {
                        taken -> {}
                        web.canGoBack() -> web.goBack()
                        else -> { Log.i(TAG, "Back not taken at the root: leaving"); finish() }
                    }
                }
            }
        }
        return true
    }

    private fun toPage(k: PageKey, then: ((Boolean) -> Unit)?) {
        web.evaluateJavascript(KeyMap.script(k)) { result ->
            val taken = result == "true"
            Log.i(TAG, "key ${k.key} taken=$taken")
            then?.invoke(taken)
        }
    }

    companion object {
        const val TAG = "KeyBridge"
        const val VERSION = "0.1.0"
        /** `--es url <address>`: open this page, and keep it once it has loaded. */
        const val EXTRA_URL = "url"
        /** `--ez reset true`: forget the kept address. */
        const val EXTRA_RESET = "reset"
        /** `--es back escape|backspace`: which key Back is sent as. */
        const val EXTRA_BACK = "back"
        private const val PREFS = "keybridge"
        private const val KEPT = "url"
        private const val BACK = "back"
        private const val LOAD_TIMEOUT_MS = 30_000L

        private val INK = Color.rgb(232, 228, 216)
        private val DIM = Color.rgb(140, 138, 130)
        private val AMBER = Color.rgb(232, 178, 74)
        private val PANEL = Color.rgb(20, 22, 28)
        private val CHIP = Color.rgb(40, 44, 54)
    }
}

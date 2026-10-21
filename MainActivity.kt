package com.shwan.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val targetUrl = "https://shwan.unaux.com/"
    private val offlineUrl = "file:///android_asset/offline.html"

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val dataUri = result.data?.data
            filePathCallback?.onReceiveValue(if (dataUri != null) arrayOf(dataUri) else null)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefreshLayout = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)

        // ١. چالاککردنی کوکییەکان (زۆر گرنگە بۆ تێپەڕاندنی سیستەمی پاراستنی unaux.com)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // ٢. ڕێکخستنی تایبەتمەندییەکانی WebView بۆ گونجاندن لەگەڵ مۆبایل
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            // ناساندنی ئەپەکە وەک وێبگەڕی مۆبایلی فەرمی بۆ وەرگرتنی وەشانی مۆبایلی سایتەکە
            userAgentString = userAgentString.replace("; wv", "")
        }

        // ٣. چاودێریکردنی بارکردنی پەڕەکان و تێکەڵکردنی کۆدی گونجاندنی مۆبایل (CSS & JS)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                CookieManager.getInstance().flush()

                if (url != null && !url.startsWith("file:///android_asset/")) {
                    injectMobileResponsiveAdapter()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // کردنەوەی لینکەکانی تەلەفۆن، واتسئەپ، تێلێگرام و ئیمەیڵ لەناو ئەپە فەرمییەکانی مۆبایل
                if (url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("whatsapp:") || url.startsWith("tg:") ||
                    url.contains("wa.me/") || url.contains("t.me/")
                ) {
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && !isInternetAvailable()) {
                    webView.loadUrl(offlineUrl)
                }
            }
        }

        // ٤. پشتگیریکردنی بارکردنی فایل (File Upload) و هێڵی پێشکەوتن
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                fileChooserLauncher.launch(intent)
                return true
            }
        }

        // ٥. پشتگیریکردنی دابەزاندنی فایلەکان لەناو وێبسایتەکەوە بۆ ناو مۆبایل
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("داگرتنی فایل...")
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "داگرتنی فایل دەستی پێکرد...", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        // ٦. نوێکردنەوەی پەڕە بە ڕاکێشان بۆ خوارەوە (Pull-to-Refresh)
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_bright)
        swipeRefreshLayout.setOnRefreshListener {
            if (isInternetAvailable()) {
                if (webView.url == offlineUrl) {
                    webView.loadUrl(targetUrl)
                } else {
                    webView.reload()
                }
            } else {
                swipeRefreshLayout.isRefreshing = false
                webView.loadUrl(offlineUrl)
            }
        }

        // ٧. کارکردنی دوگمەی گەڕانەوەی مۆبایل (Back Button)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack() && webView.url != offlineUrl) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // ٨. کردنەوەی وێبسایتەکە لە سەرەتادا
        if (savedInstanceState == null) {
            if (isInternetAvailable()) {
                webView.loadUrl(targetUrl)
            } else {
                webView.loadUrl(offlineUrl)
            }
        }
    }

    // فەنکشنی تێکەڵکردنی کۆدی CSS و JS بۆ گونجاندنی شاشەی مۆبایل بە شێوەی خۆکار
    private fun injectMobileResponsiveAdapter() {
        try {
            val cssBytes = assets.open("mobile_adapter.css").readBytes()
            val cssBase64 = Base64.encodeToString(cssBytes, Base64.NO_WRAP)
            val jsCode = assets.open("mobile_adapter.js").bufferedReader().use { it.readText() }

            val injectionScript = """
                (function() {
                    if (!document.getElementById('shwan-mobile-css')) {
                        var style = document.createElement('style');
                        style.id = 'shwan-mobile-css';
                        style.innerHTML = window.atob('$cssBase64');
                        document.head.appendChild(style);
                    }
                    $jsCode
                })();
            """.trimIndent()

            webView.evaluateJavascript(injectionScript, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

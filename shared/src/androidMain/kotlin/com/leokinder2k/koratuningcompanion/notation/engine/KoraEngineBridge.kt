package com.leokinder2k.koratuningcompanion.notation.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class KoraEngineBridge(context: Context) {

    private val pendingCalls = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val callIdGen = AtomicLong(0)
    private val readyDeferred = CompletableDeferred<Unit>()

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context.applicationContext).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.domStorageEnabled = false
        settings.databaseEnabled = false
        addJavascriptInterface(JsBridge(), "AndroidBridge")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith(ENGINE_ASSET_URL)

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (!url.startsWith(ENGINE_ASSET_URL)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                val path = request.url.path ?: return null
                val file = path.substringAfterLast("/")
                if (file.endsWith(".js")) {
                    return try {
                        val stream = context.assets.open("kora_engine/$file")
                        WebResourceResponse("application/javascript", "utf-8", stream)
                    } catch (e: Exception) { null }
                }
                return null
            }
        }
        loadUrl(ENGINE_ASSET_URL + "bridge.html")
    }

    inner class JsBridge {
        @JavascriptInterface
        fun onReady() {
            readyDeferred.complete(Unit)
        }

        @JavascriptInterface
        fun onResult(callbackId: String, resultJson: String?, error: String?) {
            val deferred = pendingCalls.remove(callbackId) ?: return
            if (error != null) {
                deferred.completeExceptionally(Exception(error))
            } else {
                deferred.complete(resultJson ?: "{}")
            }
        }
    }

    private suspend fun awaitReady() = readyDeferred.await()

    private suspend fun callJs(fn: String, paramsJson: String): String {
        awaitReady()
        val callId = callIdGen.incrementAndGet().toString()
        val deferred = CompletableDeferred<String>()
        pendingCalls[callId] = deferred

        val escaped = paramsJson.replace("\\", "\\\\").replace("'", "\\'")
        val js = "window._koraEngine.$fn('$escaped', '$callId')"
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(js, null)
        }
        return deferred.await()
    }

    suspend fun process(paramsJson: String): String = callJs("process", paramsJson)
    suspend fun edit(paramsJson: String): String = callJs("edit", paramsJson)
    suspend fun exportAudio(paramsJson: String): String = callJs("exportAudio", paramsJson)
    suspend fun exportPdf(paramsJson: String): String = callJs("exportPdf", paramsJson)

    private companion object {
        const val ENGINE_ASSET_URL = "file:///android_asset/kora_engine/"
    }
}

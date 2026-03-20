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
        settings.allowContentAccess = true
        addJavascriptInterface(JsBridge(), "AndroidBridge")
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
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
        loadUrl("file:///android_asset/kora_engine/bridge.html")
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
}

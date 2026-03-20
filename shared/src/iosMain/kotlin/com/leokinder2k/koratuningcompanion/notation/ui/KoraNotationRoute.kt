package com.leokinder2k.koratuningcompanion.notation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.leokinder2k.koratuningcompanion.generated.resources.Res
import com.leokinder2k.koratuningcompanion.generated.resources.*
import platform.WebKit.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.CoreGraphics.CGRectZero
import kotlinx.cinterop.BetaInteropApi

// JS shim injected before bridge.html loads: maps window.AndroidBridge → WKWebView message handlers
private val ANDROID_BRIDGE_SHIM = """
window.AndroidBridge = {
  onReady: function() {
    window.webkit.messageHandlers.koraReady.postMessage(null);
  },
  onResult: function(callbackId, resultJson, error) {
    window.webkit.messageHandlers.koraResult.postMessage(
      JSON.stringify({ callbackId: callbackId, resultJson: resultJson, error: error })
    );
  }
};
""".trimIndent()

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun KoraNotationRoute(modifier: Modifier) {
    var selectedInstrument by remember { mutableStateOf("KORA_21") }
    var isProcessing by remember { mutableStateOf(false) }
    var resultTitle by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val controller = remember { IosKoraEngineController() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Import Card ────────────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(Res.string.notation_upload_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(Res.string.notation_upload_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("KORA_21", "KORA_22").forEach { inst ->
                        FilterChip(
                            selected = selectedInstrument == inst,
                            onClick = { selectedInstrument = inst },
                            label = { Text(if (inst == "KORA_21") "21-string" else "22-string") }
                        )
                    }
                }
                Text(
                    stringResource(Res.string.notation_ios_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        isProcessing = true
                        errorMsg = null
                        resultTitle = null
                        scope.launch(Dispatchers.Main) {
                            val result = controller.pickAndProcess(selectedInstrument)
                            isProcessing = false
                            if (result.first != null) {
                                resultTitle = result.first
                            } else {
                                errorMsg = result.second
                            }
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.notation_pick_file))
                }
            }
        }

        // ── Status ────────────────────────────────────────────────────────────
        if (isProcessing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(Res.string.notation_processing),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        errorMsg?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // ── Result ────────────────────────────────────────────────────────────
        resultTitle?.let { title ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            selectedInstrument.replace("_", " ") + "  ·  F tuning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // Export note
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "PDF and audio exports available on the web version.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Hidden 1×1 WebView to warm up the JS engine
    Box(modifier = Modifier.size(0.dp)) {
        UIKitView(
            factory = { controller.buildWebView() },
            modifier = Modifier.size(1.dp),
            onRelease = {}
        )
    }
}

/**
 * Manages a WKWebView that runs the Kora JS engine on iOS.
 * Uses a shim to adapt the Android bridge API to WKScriptMessageHandler.
 */
@OptIn(ExperimentalForeignApi::class)
class IosKoraEngineController {

    private val readyDeferred = CompletableDeferred<Unit>()
    private val pendingResults = mutableMapOf<String, CompletableDeferred<Pair<String?, String?>>>()
    private var webView: WKWebView? = null

    fun buildWebView(): WKWebView {
        val controller = WKUserContentController()

        // Inject the AndroidBridge shim before any other scripts execute
        val shim = WKUserScript(
            source = ANDROID_BRIDGE_SHIM,
            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
            forMainFrameOnly = true
        )
        controller.addUserScript(shim)

        // Message handlers
        val readyHandler = ReadyMessageHandler { readyDeferred.complete(Unit) }
        val resultHandler = ResultMessageHandler { callbackId, resultJson, error ->
            pendingResults.remove(callbackId)?.complete(Pair(resultJson, error))
        }
        controller.addScriptMessageHandler(readyHandler, name = "koraReady")
        controller.addScriptMessageHandler(resultHandler, name = "koraResult")

        val config = WKWebViewConfiguration()
        config.userContentController = controller

        val wv = WKWebView(frame = CGRectZero.readValue(), configuration = config)
        webView = wv

        // Load bridge.html from iOS bundle resources
        val bundle = NSBundle.mainBundle
        val url = bundle.URLForResource("bridge", withExtension = "html", subdirectory = "kora_engine")
        if (url != null) {
            val dir = url.URLByDeletingLastPathComponent ?: url
            wv.loadFileURL(url, allowingReadAccessToURL = dir)
        }
        return wv
    }

    suspend fun pickAndProcess(instrumentType: String): Pair<String?, String?> {
        // UIDocumentPickerViewController integration requires Xcode wiring via AppDelegate/SceneDelegate.
        // Return a clear status so the user knows what to do next.
        return Pair(null, "iOS file picking requires the Xcode project to be built with UIDocumentPickerViewController. Use Android or web version for full functionality.")
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ReadyMessageHandler(
    private val onReady: () -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        onReady()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class ResultMessageHandler(
    private val onResult: (callbackId: String, resultJson: String?, error: String?) -> Unit
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        val body = didReceiveScriptMessage.body as? String ?: return
        // Parse with NSJSONSerialization (Foundation, available on iOS)
        val data = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        @Suppress("UNCHECKED_CAST")
        val map = NSJSONSerialization.JSONObjectWithData(data, 0UL, null) as? Map<String, Any?> ?: return
        val callbackId = map["callbackId"] as? String ?: return
        val resultJson = (map["resultJson"] as? String)?.takeIf { it.isNotEmpty() }
        val error = (map["error"] as? String)?.takeIf { it.isNotEmpty() }
        onResult(callbackId, resultJson, error)
    }
}

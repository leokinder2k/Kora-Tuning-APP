package com.leokinder2k.koratuningcompanion.platform

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val KORA_DIAGRAM_PDF_ASSET_NAME = "Kora_x22.pdf"
private const val KORA_DIAGRAM_PDF_PAGE_INDEX = 0
private const val KORA_DIAGRAM_PDF_SCALE = 2

@Composable
actual fun KoraDiagramBackground(contentDescription: String, modifier: Modifier) {
    val context = LocalContext.current
    val pdfBitmap by produceState<Bitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { renderKoraDiagramPdfBitmap(context) }.getOrNull()
        }
    }
    if (pdfBitmap != null) {
        Image(
            bitmap = pdfBitmap!!.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

private fun renderKoraDiagramPdfBitmap(context: android.content.Context): Bitmap {
    val cachedPdfFile = File(context.cacheDir, KORA_DIAGRAM_PDF_ASSET_NAME)
    if (!cachedPdfFile.exists() || cachedPdfFile.length() <= 0L) {
        context.assets.open(KORA_DIAGRAM_PDF_ASSET_NAME).use { input ->
            FileOutputStream(cachedPdfFile, false).use { output ->
                input.copyTo(output)
            }
        }
    }
    return ParcelFileDescriptor.open(cachedPdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(renderer.pageCount > KORA_DIAGRAM_PDF_PAGE_INDEX) {
                "Kora diagram PDF has no page index $KORA_DIAGRAM_PDF_PAGE_INDEX"
            }
            renderer.openPage(KORA_DIAGRAM_PDF_PAGE_INDEX).use { page ->
                val bitmap = Bitmap.createBitmap(
                    (page.width * KORA_DIAGRAM_PDF_SCALE).coerceAtLeast(1),
                    (page.height * KORA_DIAGRAM_PDF_SCALE).coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }
}

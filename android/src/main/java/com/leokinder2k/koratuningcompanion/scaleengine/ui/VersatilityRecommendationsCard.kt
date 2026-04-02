package com.leokinder2k.koratuningcompanion.scaleengine.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.leokinder2k.koratuningcompanion.R
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleRootReference
import com.leokinder2k.koratuningcompanion.scaleengine.model.ScaleType
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.LeverOnlyReachableState
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.LeverRouteStep
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.RouteTransitionType
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.TuningVersatilitySummary
import com.leokinder2k.koratuningcompanion.scaleengine.recommendation.VersatilityAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
internal fun VersatilityRecommendationsCard(
    analysis: VersatilityAnalysis,
    modifier: Modifier = Modifier
) {
    if (analysis.bestTuning == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.versatility_title, analysis.instrumentKey.symbol),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.versatility_scope,
                    analysis.evaluatedRoots,
                    analysis.evaluatedScaleTypes,
                    analysis.evaluatedReferences
                ),
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) {
                            buildVersatilityPdf(context, analysis)
                        }
                        sharePdf(
                            context = context,
                            bytes = bytes,
                            fileName = "chromatic-lever-analysis-${analysis.instrumentKey.symbol}.pdf"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.versatility_export_pdf))
            }
        }
    }
}

// ── PDF builder ───────────────────────────────────────────────────────────────

private const val PAGE_W = 595f
private const val PAGE_H = 842f
private const val MARGIN = 52f
private val CONTENT_W = PAGE_W - MARGIN * 2

private fun buildVersatilityPdf(context: Context, analysis: VersatilityAnalysis): ByteArray {
    val doc = PdfDocument()
    var pageNum = 1

    val titlePaint = Paint().apply {
        isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        textSize = 17f; color = android.graphics.Color.BLACK
    }
    val headPaint = Paint().apply {
        isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
        textSize = 12f; color = android.graphics.Color.BLACK
    }
    val bodyPaint = Paint().apply {
        isAntiAlias = true; typeface = Typeface.DEFAULT
        textSize = 11f; color = android.graphics.Color.BLACK
    }
    val dimPaint = Paint().apply {
        isAntiAlias = true; typeface = Typeface.DEFAULT
        textSize = 10f; color = android.graphics.Color.DKGRAY
    }

    var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNum).create())
    var canvas = page.canvas
    var y = MARGIN + titlePaint.textSize

    fun newPage() {
        doc.finishPage(page)
        pageNum++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNum).create())
        canvas = page.canvas
        y = MARGIN + bodyPaint.textSize
    }

    fun drawWrapped(text: String, paint: Paint, indent: Float = 0f) {
        val lineH = paint.textSize * 1.55f
        val maxW = CONTENT_W - indent
        val words = text.split(" ")
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxW) {
                if (y + lineH > PAGE_H - MARGIN) newPage()
                canvas.drawText(line, MARGIN + indent, y, paint)
                y += lineH
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) {
            if (y + lineH > PAGE_H - MARGIN) newPage()
            canvas.drawText(line, MARGIN + indent, y, paint)
            y += lineH
        }
    }

    fun gap(extra: Float = 8f) { y += extra }
    fun rule() {
        gap(4f)
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, dimPaint)
        gap(8f)
    }

    // ── Title ─────────────────────────────────────────────────────────────────
    drawWrapped("Chromatic Lever Analysis — Natural ${analysis.instrumentKey.symbol}", titlePaint)
    gap(2f)
    drawWrapped(
        "Scope: ${analysis.evaluatedRoots} roots × " +
        "${analysis.evaluatedScaleTypes} scale types × " +
        "${analysis.evaluatedReferences} references",
        dimPaint
    )
    rule()

    // ── Best tuning ───────────────────────────────────────────────────────────
    analysis.bestTuning?.let { best ->
        drawWrapped("Best tuning: ${best.tuningName} (${best.instrumentKey.symbol})", headPaint)
        drawWrapped(
            "${best.reachableStateCount} reachable states — " +
            "${best.distinctRoots} roots, ${best.distinctScaleTypes} scale types, " +
            "${best.distinctReferences} references",
            bodyPaint, indent = 8f
        )
        gap(4f)
    }

    // ── Tuning rankings ───────────────────────────────────────────────────────
    drawWrapped("Tuning Rankings", headPaint)
    gap(4f)
    analysis.tuningSummaries.forEach { s ->
        drawWrapped(
            "#${s.rank}  ${s.tuningName} (${s.instrumentKey.symbol})  |  " +
            "States: ${s.reachableStateCount}  " +
            "Roots: ${s.distinctRoots}  Scales: ${s.distinctScaleTypes}",
            bodyPaint
        )
    }
    rule()

    // ── Recommended lever route ───────────────────────────────────────────────
    drawWrapped("Recommended Lever Route", headPaint)
    gap(4f)
    analysis.recommendedRoute.forEach { step ->
        drawWrapped(routeStepLabel(context, step), bodyPaint)
        gap(2f)
    }

    doc.finishPage(page)
    val out = ByteArrayOutputStream()
    try {
        doc.writeTo(out)
    } finally {
        doc.close()
    }
    return out.toByteArray()
}

private fun sharePdf(context: Context, bytes: ByteArray, fileName: String) {
    val file = File(context.cacheDir, fileName)
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

// ── Text formatters (reused for PDF) ─────────────────────────────────────────

private fun tuningSummaryLabel(context: Context, summary: TuningVersatilitySummary): String =
    context.getString(
        R.string.versatility_tuning_line,
        summary.rank, summary.tuningName,
        summary.reachableStateCount, summary.distinctRoots,
        summary.distinctScaleTypes, summary.distinctReferences,
        summary.exampleStates.joinToString { stateLabel(context, it) }
    )

private fun routeStepLabel(context: Context, step: LeverRouteStep): String {
    val transition = when (step.transitionType) {
        RouteTransitionType.FROM_NATURAL_OPEN -> context.getString(
            R.string.versatility_route_from_natural, leverListLabel(context, step.leversToClose)
        )
        RouteTransitionType.FROM_PREVIOUS_STATE -> context.getString(
            R.string.versatility_route_from_previous,
            step.leverChangesFromPrevious,
            leverListLabel(context, step.leversToOpen),
            leverListLabel(context, step.leversToClose)
        )
        RouteTransitionType.SWITCH_TUNING_RESET -> context.getString(
            R.string.versatility_route_switch_tuning,
            step.state.tuningName,
            leverListLabel(context, step.leversToClose)
        )
    }
    return context.getString(
        R.string.versatility_route_line,
        step.stepNumber,
        step.state.tuningName, step.state.rootNote.symbol,
        scaleTypeLabelText(context, step.state.scaleType),
        rootReferenceLabel(context, step.state.rootReference),
        transition
    )
}

private fun stateLabel(context: Context, state: LeverOnlyReachableState): String =
    context.getString(
        R.string.versatility_target_label,
        state.rootNote.symbol,
        scaleTypeLabelText(context, state.scaleType),
        rootReferenceLabel(context, state.rootReference)
    )

private fun leverListLabel(context: Context, levers: List<String>): String =
    if (levers.isEmpty()) context.getString(R.string.versatility_lever_none)
    else levers.joinToString(", ")

private fun rootReferenceLabel(context: Context, reference: ScaleRootReference): String =
    when (reference) {
        ScaleRootReference.LEFT_1  -> context.getString(R.string.scale_root_reference_left_1)
        ScaleRootReference.LEFT_2  -> context.getString(R.string.scale_root_reference_left_2)
        ScaleRootReference.LEFT_3  -> context.getString(R.string.scale_root_reference_left_3)
        ScaleRootReference.LEFT_4  -> context.getString(R.string.scale_root_reference_left_4)
        ScaleRootReference.RIGHT_1 -> context.getString(R.string.scale_root_reference_right_1)
    }

private fun scaleTypeLabelText(context: Context, scaleType: ScaleType): String =
    when (scaleType) {
        ScaleType.MAJOR               -> context.getString(R.string.scale_type_major)
        ScaleType.NATURAL_MINOR       -> context.getString(R.string.scale_type_natural_minor)
        ScaleType.HARMONIC_MINOR      -> context.getString(R.string.scale_type_harmonic_minor)
        ScaleType.MELODIC_MINOR       -> context.getString(R.string.scale_type_melodic_minor)
        ScaleType.IONIAN              -> context.getString(R.string.scale_type_ionian)
        ScaleType.DORIAN              -> context.getString(R.string.scale_type_dorian)
        ScaleType.PHRYGIAN            -> context.getString(R.string.scale_type_phrygian)
        ScaleType.LYDIAN              -> context.getString(R.string.scale_type_lydian)
        ScaleType.MIXOLYDIAN          -> context.getString(R.string.scale_type_mixolydian)
        ScaleType.AEOLIAN             -> context.getString(R.string.scale_type_aeolian)
        ScaleType.LOCRIAN             -> context.getString(R.string.scale_type_locrian)
        ScaleType.MAJOR_PENTATONIC    -> context.getString(R.string.scale_type_major_pentatonic)
        ScaleType.MINOR_PENTATONIC    -> context.getString(R.string.scale_type_minor_pentatonic)
        ScaleType.MAJOR_HEXATONIC     -> context.getString(R.string.scale_type_major_hexatonic)
        ScaleType.MINOR_HEXATONIC     -> context.getString(R.string.scale_type_minor_hexatonic)
        ScaleType.WHOLE_TONE          -> context.getString(R.string.scale_type_whole_tone)
        ScaleType.MAJOR_BLUES         -> context.getString(R.string.scale_type_major_blues)
        ScaleType.MINOR_BLUES         -> context.getString(R.string.scale_type_minor_blues)
        ScaleType.BEEBOP_MAJOR        -> context.getString(R.string.scale_type_beebop_major)
        ScaleType.BEEBOP_DOMINANT     -> context.getString(R.string.scale_type_beebop_dominant)
        ScaleType.BEEBOP_DORIAN       -> context.getString(R.string.scale_type_beebop_dorian)
        ScaleType.DIMINISHED_WHOLE_HALF -> context.getString(R.string.scale_type_diminished_whole_half)
        ScaleType.DIMINISHED_HALF_WHOLE -> context.getString(R.string.scale_type_diminished_half_whole)
        ScaleType.CHROMATIC           -> context.getString(R.string.scale_type_chromatic)
    }

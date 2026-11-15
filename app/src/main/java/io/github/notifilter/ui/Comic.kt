package io.github.notifilter.ui

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * Building blocks for the app's tutorial "comics": a dialog of swipeable pages, each a looping
 * animation of a miniature phone above a caption. The animations are drawn rather than filmed so
 * they look like a generic phone instead of one brand's, and follow the app's theme.
 */

/**
 * A dialog that pages through [captions], drawing [panel] above each one. Next moves on, Skip and
 * the last page's "Got it" close it.
 */
@Composable
internal fun ComicDialog(
    title: String,
    intro: String?,
    captions: List<String>,
    onDismiss: () -> Unit,
    panel: @Composable (page: Int) -> Unit,
) {
    val pager = rememberPagerState { captions.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == captions.lastIndex
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (last) onDismiss() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
            ) { Text(if (last) "Got it" else "Next") }
        },
        dismissButton = {
            if (!last) TextButton(onClick = onDismiss) { Text("Skip") }
        },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (intro != null) {
                    Text(intro, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
                HorizontalPager(pager) { page ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        panel(page)
                        Spacer(Modifier.height(12.dp))
                        // Four lines keeps every page the same height, so the dialog doesn't jump while swiping.
                        Text(captions[page], textAlign = TextAlign.Center, minLines = 4)
                    }
                }
                PageDots(captions.size, pager.currentPage)
            }
        },
    )
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier
            .padding(top = 8.dp)
            .clearAndSetSemantics { contentDescription = "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { i ->
            val color by animateColorAsState(
                if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot",
            )
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
    }
}

/**
 * A miniature phone whose screen, filled with [background], is drawn by [draw] with the screen's
 * bounds and the loop's progress from 0 to 1. Drawing is clipped to the screen's rounded corners.
 */
@Composable
internal fun MiniPhone(
    millis: Int,
    stillAt: Float,
    background: Color,
    draw: DrawScope.(screen: Rect, t: Float) -> Unit,
) {
    val frame = MaterialTheme.colorScheme.onSurfaceVariant
    val progress = loopProgress(millis, stillAt)
    Canvas(Modifier.size(width = 136.dp, height = 228.dp)) {
        val corner = 16.dp.toPx()
        drawRoundRect(frame, cornerRadius = CornerRadius(corner), style = Stroke(2.5.dp.toPx()))
        val inset = 5.dp.toPx()
        val screen = Rect(inset, inset, size.width - inset, size.height - inset)
        val screenCorner = CornerRadius(corner - inset)
        drawRoundRect(background, screen.topLeft, screen.size, screenCorner)
        clipPath(Path().apply { addRoundRect(RoundRect(screen, screenCorner)) }) {
            draw(screen, progress.value)
        }
    }
}

/** Loops 0→1 forever, or holds a single representative frame if the user has turned animations off. */
@Composable
private fun loopProgress(durationMillis: Int, stillAt: Float): State<Float> {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    if (!animate) return rememberUpdatedState(stillAt)
    return rememberInfiniteTransition(label = "tutorial").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "progress",
    )
}

/** A translucent fingertip, with an expanding ring when [ripple] is between 0 and 1 to show a tap. */
internal fun DrawScope.drawFinger(color: Color, center: Offset, alpha: Float, ripple: Float = 0f) {
    if (alpha <= 0f) return
    val r = 10.dp.toPx()
    drawCircle(color, r, center, alpha * 0.55f)
    if (ripple > 0f && ripple < 1f) {
        drawCircle(color, r * (1f + ripple), center, alpha * (1f - ripple), style = Stroke(2.dp.toPx()))
    }
}

/** Eased 0→1 progress of [t] through the window [start, end]. */
internal fun phase(t: Float, start: Float, end: Float) =
    FastOutSlowInEasing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

/** A tap's ripple: grows linearly 0→1 while [t] is in [start, end], and is 0 otherwise. */
internal fun tapRipple(t: Float, start: Float, end: Float) = if (t in start..end) (t - start) / (end - start) else 0f

internal fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f

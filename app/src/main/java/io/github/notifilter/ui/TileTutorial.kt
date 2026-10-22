package io.github.notifilter.ui

import android.provider.Settings
import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Shown after a Quick Settings tile is added, because the tile ends up out of sight on the last page. */
data class TileHint(val label: String, val usage: String, @DrawableRes val icon: Int, val alreadyAdded: Boolean)

/**
 * One step of the "where did my tile go" tutorial. The animations are drawn rather than filmed so they
 * look like a generic phone instead of one brand's Quick Settings, and follow the app's theme.
 */
private class Step(val caption: String, val millis: Int, val stillAt: Float, val draw: DrawScope.(Scene, Float) -> Unit)

private val steps = listOf(
    Step("Swipe down from the top of the screen twice to open Quick Settings.", 4200, 0.8f) { s, t -> swipeDown(s, t) },
    Step("New tiles go at the end. Swipe left to the last page to find it.", 4000, 0.7f) { s, t -> swipeToEnd(s, t) },
    Step(
        "To keep it handy, tap Edit (the pencil icon, or ⋮ → Edit on some phones), " +
            "then hold the tile and drag it to the front.",
        5200,
        0.6f,
    ) { s, t -> dragToFront(s, t) },
)

@Composable
fun TileHintDialog(hint: TileHint, onDismiss: () -> Unit) {
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == steps.lastIndex
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
        title = { Text(if (hint.alreadyAdded) "\"${hint.label}\" is already added" else "\"${hint.label}\" added") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(hint.usage, Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                HorizontalPager(pager) { page ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        StepAnimation(steps[page], hint.icon)
                        Spacer(Modifier.height(12.dp))
                        // Four lines keeps every page the same height, so the dialog doesn't jump while swiping.
                        Text(steps[page].caption, textAlign = TextAlign.Center, minLines = 4)
                    }
                }
                PageDots(steps.size, pager.currentPage)
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

@Composable
private fun StepAnimation(step: Step, @DrawableRes icon: Int) {
    val colors = SceneColors(MaterialTheme.colorScheme)
    val tileIcon = painterResource(icon)
    val editIcon = rememberVectorPainter(Icons.Default.Edit)
    val progress = loopProgress(step.millis, step.stillAt)
    Canvas(Modifier.size(width = 136.dp, height = 228.dp)) {
        val corner = 16.dp.toPx()
        drawRoundRect(colors.frame, cornerRadius = CornerRadius(corner), style = Stroke(2.5.dp.toPx()))
        val inset = 5.dp.toPx()
        val screen = Rect(inset, inset, size.width - inset, size.height - inset)
        val screenCorner = CornerRadius(corner - inset)
        drawRoundRect(colors.wallpaper, screen.topLeft, screen.size, screenCorner)
        clipPath(Path().apply { addRoundRect(RoundRect(screen, screenCorner)) }) {
            step.draw(this, Scene(screen, colors, tileIcon, editIcon, gap = 5.dp.toPx()), progress.value)
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

private class SceneColors(c: ColorScheme) {
    val frame = c.onSurfaceVariant
    // Tinted so the shade (plain surface) stands out when it slides over the home screen.
    val wallpaper = c.primaryContainer
    val placeholder = c.outline.copy(alpha = 0.5f)
    val shade = c.surface
    val tile = c.secondaryContainer
    val card = c.surfaceContainerHighest
    val accent = c.primary
    val onAccent = c.onPrimary
    val finger = c.tertiary
}

/**
 * Layout of the miniature phone's Quick Settings: two wide tiles (like Wi-Fi and mobile data) at the
 * top, then a grid of round tiles, five per row as on most phones.
 */
private class Scene(
    val screen: Rect,
    val colors: SceneColors,
    val tileIcon: Painter,
    val editIcon: Painter,
    val gap: Float,
) {
    val pillSize = Size((screen.width - 3 * gap) / 2, screen.height * 0.085f)
    val pillTop = screen.top + screen.height * 0.08f
    val tileDiameter = (screen.width - (COLUMNS + 1) * gap) / COLUMNS
    val gridTop = pillTop + pillSize.height + gap * 1.5f
    val gridBottom = gridTop + ROWS * (tileDiameter + gap)

    fun pill(i: Int) = Offset(screen.left + gap + i * (pillSize.width + gap), pillTop)

    /** Center of round tile [i], counting left to right, top to bottom. */
    fun slot(i: Int) = Offset(
        screen.left + gap + (i % COLUMNS) * (tileDiameter + gap) + tileDiameter / 2,
        gridTop + (i / COLUMNS) * (tileDiameter + gap) + tileDiameter / 2,
    )

    companion object {
        const val COLUMNS = 5
        const val ROWS = 4
        const val PER_PAGE = COLUMNS * ROWS
    }
}

/**
 * Page 1: the first swipe pulls the notification shade all the way down (shades snap fully open, they
 * don't stop halfway), showing the wide tiles and one row of round tiles above the notifications. The
 * second swipe grows that row into the full grid, pushing the notifications down out of the way.
 */
private fun DrawScope.swipeDown(s: Scene, t: Float) {
    val screen = s.screen
    val h = screen.height
    val first = phase(t, 0.08f, 0.32f)
    val second = phase(t, 0.46f, 0.68f)
    val fade = 1f - phase(t, 0.9f, 1f)
    val shadeHeight = h * first
    val firstRowBottom = s.gridTop + s.tileDiameter + s.gap
    val gridEdge = mix(firstRowBottom, s.gridBottom, second)

    drawHome(s)
    drawShade(s, shadeHeight, fade)
    clipRect(screen.left, screen.top, screen.right, screen.top + shadeHeight) {
        drawPills(s, fade)
        for (i in 0 until Scene.COLUMNS) drawTile(s, s.slot(i), ours = false, alpha = fade)
        clipRect(screen.left, firstRowBottom, screen.right, gridEdge) {
            for (i in Scene.COLUMNS until Scene.PER_PAGE) drawTile(s, s.slot(i), ours = false, alpha = second * fade)
        }
        drawPageDots(s, position = 0f, alpha = second * fade)
        for (i in 0 until 3) {
            drawRoundRect(
                s.colors.card,
                Offset(screen.left + s.gap, gridEdge + s.gap + i * h * 0.13f),
                Size(screen.width - 2 * s.gap, h * 0.11f),
                CornerRadius(s.gap * 1.5f),
                alpha = (1f - second) * fade,
            )
        }
    }

    val top = screen.top + 8.dp.toPx()
    val bottom = screen.top + h * 0.9f
    val firstFinger = phase(t, 0.02f, 0.08f) * (1f - phase(t, 0.32f, 0.38f))
    val secondFinger = phase(t, 0.40f, 0.46f) * (1f - phase(t, 0.68f, 0.74f))
    if (firstFinger > 0f) drawFinger(s, Offset(screen.center.x, mix(top, bottom, first)), firstFinger)
    if (secondFinger > 0f) drawFinger(s, Offset(screen.center.x, mix(top, bottom, second)), secondFinger)
}

/** Page 2: swipe the tile grid left to its last page, where the new tile is waiting. */
private fun DrawScope.swipeToEnd(s: Scene, t: Float) {
    val screen = s.screen
    val fade = phase(t, 0f, 0.06f) * (1f - phase(t, 0.92f, 1f))
    val scroll = phase(t, 0.15f, 0.45f)
    val dx = -scroll * screen.width
    val oursIndex = 6

    drawShade(s, screen.height, 1f)
    drawPills(s, fade)
    for (i in 0 until Scene.PER_PAGE) drawTile(s, s.slot(i) + Offset(dx, 0f), ours = false, alpha = fade)
    for (i in 0..oursIndex) drawTile(s, s.slot(i) + Offset(dx + screen.width, 0f), ours = i == oursIndex, alpha = fade)
    drawPageDots(s, position = scroll, alpha = fade)

    val glow = phase(t, 0.5f, 0.58f) * (1f - phase(t, 0.86f, 0.92f)) * fade
    if (glow > 0f) {
        val grow = s.gap * (0.6f + 0.4f * sin(t * 2 * PI.toFloat() * 6))
        drawCircle(
            s.colors.accent,
            s.tileDiameter / 2 + grow,
            s.slot(oursIndex) + Offset(dx + screen.width, 0f),
            alpha = glow,
            style = Stroke(2.dp.toPx()),
        )
    }

    val finger = phase(t, 0.06f, 0.13f) * (1f - phase(t, 0.45f, 0.52f))
    if (finger > 0f) {
        val x = mix(screen.left + screen.width * 0.8f, screen.left + screen.width * 0.2f, scroll)
        drawFinger(s, Offset(x, (s.gridTop + s.gridBottom) / 2), finger)
    }
}

/** Page 3: tap the pencil to edit, then press and drag the new tile from the last slot to the first. */
private fun DrawScope.dragToFront(s: Scene, t: Float) {
    val screen = s.screen
    val fade = phase(t, 0f, 0.06f) * (1f - phase(t, 0.92f, 1f))
    val edit = phase(t, 0.2f, 0.26f)
    val lift = phase(t, 0.36f, 0.42f) * (1f - phase(t, 0.72f, 0.78f))
    val drag = phase(t, 0.42f, 0.72f)
    val last = Scene.PER_PAGE - 1

    drawShade(s, screen.height, 1f)
    drawPills(s, fade)

    val pencilSize = screen.height * 0.06f
    val pencil = Offset(screen.right - s.gap - pencilSize / 2, screen.top + screen.height * 0.04f)
    translate(pencil.x - pencilSize / 2, pencil.y - pencilSize / 2) {
        with(s.editIcon) { draw(Size(pencilSize, pencilSize), (1f - edit) * fade, ColorFilter.tint(s.colors.frame)) }
    }
    // In edit mode the pencil becomes a "Done" pill, as on most phones.
    drawRoundRect(
        s.colors.accent,
        pencil - Offset(pencilSize, pencilSize * 0.4f),
        Size(pencilSize * 1.6f, pencilSize * 0.8f),
        CornerRadius(pencilSize * 0.4f),
        alpha = edit * fade,
    )

    for (i in 0 until Scene.PER_PAGE) drawTile(s, s.slot(i), ours = false, alpha = (1f - edit) * fade)
    drawPageDots(s, position = 0f, alpha = (1f - edit) * fade)
    for (i in 0 until last) drawTile(s, lerp(s.slot(i), s.slot(i + 1), drag), ours = false, alpha = edit * fade)
    val ours = lerp(s.slot(last), s.slot(0), drag)
    drawTile(s, ours, ours = true, alpha = edit * fade, scale = 1f + 0.15f * lift)

    val finger = phase(t, 0.04f, 0.1f) * (1f - phase(t, 0.78f, 0.84f))
    if (finger > 0f) {
        val position = when {
            t < 0.28f -> lerp(pencil + Offset(0f, screen.height * 0.15f), pencil, phase(t, 0.04f, 0.14f))
            t < 0.36f -> lerp(pencil, s.slot(last), phase(t, 0.28f, 0.36f))
            else -> ours
        }
        drawFinger(s, position, finger, ripple = if (t in 0.14f..0.24f) phase(t, 0.14f, 0.24f) else 0f)
    }
}

private fun DrawScope.drawHome(s: Scene) {
    val screen = s.screen
    val r = screen.width * 0.075f
    for (row in 0 until 3) {
        for (col in 0 until 4) {
            val center = Offset(
                screen.left + screen.width * (0.14f + col * 0.24f),
                screen.top + screen.height * (0.55f + row * 0.13f),
            )
            drawCircle(s.colors.placeholder, r, center)
        }
    }
}

/** The shade slides down from the top edge, with only its bottom corners rounded. */
private fun DrawScope.drawShade(s: Scene, height: Float, alpha: Float) {
    if (height <= 0f) return
    val r = s.gap * 3
    drawRoundRect(
        s.colors.shade,
        Offset(s.screen.left, s.screen.top - r),
        Size(s.screen.width, height + r),
        CornerRadius(r),
        alpha = alpha,
    )
}

/** The two wide tiles most phones show above the grid, for Wi-Fi and mobile data. */
private fun DrawScope.drawPills(s: Scene, alpha: Float) {
    if (alpha <= 0f) return
    val size = s.pillSize
    for (i in 0 until 2) {
        val topLeft = s.pill(i)
        drawRoundRect(s.colors.tile, topLeft, size, CornerRadius(size.height / 2), alpha = alpha)
        val icon = Offset(topLeft.x + size.height * 0.5f, topLeft.y + size.height / 2)
        drawCircle(s.colors.placeholder, size.height * 0.2f, icon, alpha)
        drawRoundRect(
            s.colors.placeholder,
            Offset(icon.x + size.height * 0.45f, topLeft.y + size.height * 0.42f),
            Size(size.width * 0.4f, size.height * 0.16f),
            CornerRadius(size.height * 0.08f),
            alpha = alpha * 0.8f,
        )
    }
}

/** A round Quick Settings tile; [ours] draws the new tile in the accent color with its real icon. */
private fun DrawScope.drawTile(s: Scene, center: Offset, ours: Boolean, alpha: Float, scale: Float = 1f) {
    if (alpha <= 0f) return
    val r = s.tileDiameter / 2 * scale
    drawCircle(if (ours) s.colors.accent else s.colors.tile, r, center, alpha)
    if (ours) {
        val iconSize = r * 1.1f
        translate(center.x - iconSize / 2, center.y - iconSize / 2) {
            with(s.tileIcon) { draw(Size(iconSize, iconSize), alpha, ColorFilter.tint(s.colors.onAccent)) }
        }
    } else {
        drawCircle(s.colors.placeholder, r * 0.4f, center, alpha)
    }
}

/** Two page dots under the tile grid; [position] slides the active dot from the first page (0) to the second (1). */
private fun DrawScope.drawPageDots(s: Scene, position: Float, alpha: Float) {
    if (alpha <= 0f) return
    val r = 2.5.dp.toPx()
    val y = s.gridBottom + s.gap
    val first = Offset(s.screen.center.x - 3 * r, y)
    val second = Offset(s.screen.center.x + 3 * r, y)
    drawCircle(s.colors.placeholder, r, first, alpha)
    drawCircle(s.colors.placeholder, r, second, alpha)
    drawCircle(s.colors.frame, r, lerp(first, second, position), alpha)
}

/** A translucent fingertip, with an expanding ring when [ripple] is between 0 and 1 to show a tap. */
private fun DrawScope.drawFinger(s: Scene, center: Offset, alpha: Float, ripple: Float = 0f) {
    val r = 10.dp.toPx()
    drawCircle(s.colors.finger, r, center, alpha * 0.55f)
    if (ripple > 0f && ripple < 1f) {
        drawCircle(s.colors.finger, r * (1f + ripple), center, alpha * (1f - ripple), style = Stroke(2.dp.toPx()))
    }
}

/** Eased 0→1 progress of [t] through the window [start, end]. */
private fun phase(t: Float, start: Float, end: Float) =
    FastOutSlowInEasing.transform(((t - start) / (end - start)).coerceIn(0f, 1f))

private fun mix(a: Float, b: Float, f: Float) = a + (b - a) * f

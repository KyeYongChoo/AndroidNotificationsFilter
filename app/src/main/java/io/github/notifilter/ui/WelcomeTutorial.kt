package io.github.notifilter.ui

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.notifilter.R
import kotlin.math.abs

/**
 * The comic new users see the first time they open the app (and again from ⋮ → How it works):
 * grant access, make a rule from a notification, see it hidden, find it in Filtered, the tile, and
 * where to find the comic again.
 */
@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    val tileTip = if (Build.VERSION.SDK_INT >= 33) {
        "Tip: add the Quick Settings tile from the ⋮ menu. Tapping it in your notification shade opens the Recent tab."
    } else {
        "Tip: edit your Quick Settings and add the \"Filter a notification\" tile. Tapping it opens the Recent tab."
    }
    ComicDialog(
        title = "Welcome to NotiFilter",
        intro = "NotiFilter hides the notifications you don't want. Here's how it works.",
        captions = panels.map { it.caption } + tileTip + replay.caption,
        onDismiss = onDismiss,
    ) { page ->
        when {
            page < panels.size -> panels[page].let { AppPhone(it.millis, it.stillAt, it.draw) }
            page == panels.size -> TileShortcutPhone()
            else -> AppPhone(replay.millis, replay.stillAt, replay.draw)
        }
    }
}

private class Panel(val caption: String, val millis: Int, val stillAt: Float, val draw: DrawScope.(AppScene, Float) -> Unit)

private val panels = listOf(
    Panel(
        "First, tap Grant access and allow NotiFilter. Android shows the same warning for every app that reads notifications.",
        6400,
        0.66f,
    ) { a, t -> grantAccess(a, t) },
    Panel(
        "When a notification you don't want arrives, open the Recent tab and tap it.",
        5600,
        0.72f,
    ) { a, t -> pickFromRecent(a, t) },
    Panel(
        "That makes a rule, already filled in. Tap Save, and it's hidden from now on, along with others like it.",
        6000,
        0.66f,
    ) { a, t -> saveRule(a, t) },
    Panel(
        "Everything hidden is listed in the Filtered tab. If you needed one after all, tap Open.",
        5200,
        0.44f,
    ) { a, t -> openFiltered(a, t) },
)

/** The last page, after the tile tip: where to find this tutorial again. */
private val replay = Panel(
    "You can watch this again any time: it's at the top of the ⋮ menu, under How it works.",
    4800,
    0.46f,
) { a, t -> reopenTutorial(a, t) }

/** The kinds of notification in the comic. The promo is the unwanted one, and the only one with a rule. */
private enum class Kind { PROMO, CHAT, MAIL }

private class AppColors(c: ColorScheme) {
    val surface = c.surface
    /** Stand-in for body text, drawn as rounded bars. */
    val ink = c.onSurfaceVariant.copy(alpha = 0.45f)
    /** Stand-in for titles. */
    val strongInk = c.onSurface.copy(alpha = 0.7f)
    val accent = c.primary
    val onAccent = c.onPrimary
    val card = c.surfaceContainerHighest
    val warningCard = c.errorContainer
    val onWarning = c.onErrorContainer.copy(alpha = 0.6f)
    val pressed = c.secondaryContainer
    val divider = c.outlineVariant
    val outline = c.outline
    val dialog = c.surfaceContainerHigh
    val scrim = Color.Black.copy(alpha = 0.35f)
    val iconBackground = c.secondaryContainer
    val icon = c.onSecondaryContainer
    val promoBackground = c.tertiaryContainer
    val promo = c.onTertiaryContainer
    val finger = c.tertiary
}

private class AppIcons(
    val promo: Painter,
    val chat: Painter,
    val mail: Painter,
    val filter: Painter,
    val back: Painter,
    val more: Painter,
)

@Composable
private fun rememberAppIcons() = AppIcons(
    promo = rememberVectorPainter(Icons.Default.ShoppingCart),
    chat = rememberVectorPainter(Icons.Default.Person),
    mail = rememberVectorPainter(Icons.Default.Email),
    filter = painterResource(R.drawable.ic_tile_filter),
    back = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
    more = rememberVectorPainter(Icons.Default.MoreVert),
)

/**
 * Layout of the miniature NotiFilter: an app bar, the "Filtering is on" switch, the Rules / Recent /
 * Filtered tabs and a list, with the "Notification access needed" card above them until access is
 * granted. Text is drawn as bars so the comic reads the same in every language and at any size.
 */
private class AppScene(val screen: Rect, val colors: AppColors, val icons: AppIcons, val gap: Float) {
    val w = screen.width
    val h = screen.height
    val line = h * 0.02f
    val iconSize = w * 0.09f
    val appBarY = screen.top + h * 0.08f
    val accessCardHeight = h * 0.25f
    val rowHeight = h * 0.13f
    val listHeaderHeight = h * 0.07f
    val switchSize = Size(w * 0.15f, h * 0.05f)

    fun contentTop(card: Float) = screen.top + h * 0.13f + card
    fun listTop(card: Float) = contentTop(card) + h * 0.14f
    fun tabCenter(tab: Float, card: Float = 0f) = Offset(screen.left + w * (tab + 0.5f) / 3, contentTop(card) + h * 0.105f)
    fun rowTop(i: Int, card: Float = 0f) = listTop(card) + listHeaderHeight + i * rowHeight
    fun rowCenter(i: Int) = Offset(screen.center.x, rowTop(i) + rowHeight / 2)
    fun openCenter(i: Int) = Offset(screen.right - gap * 2 - w * 0.06f, rowTop(i) + rowHeight / 2)
    val grantButton = Rect(Offset(screen.left + gap * 2.5f, contentTop(0f) + h * 0.165f), Size(w * 0.42f, h * 0.055f))
    val settingsSwitch = Offset(screen.right - gap * 2 - switchSize.width / 2, screen.top + h * 0.44f)
    val saveButton = Offset(screen.right - gap * 2 - w * 0.07f, appBarY)
    val menuButton = Offset(screen.right - gap * 2.4f, appBarY)
    val menu = Rect(Offset(screen.right - gap - w * 0.58f, screen.top + h * 0.05f), Size(w * 0.58f, h * 0.5f))
    val menuItemHeight = menu.height / 7
    fun menuItemCenter(i: Int) = Offset(menu.center.x, menu.top + menuItemHeight * (i + 0.5f))
    val dialog = Rect(Offset(screen.left + w * 0.07f, screen.center.y - h * 0.2f), Size(w * 0.86f, h * 0.4f))
    val allowButton = Offset(dialog.right - gap * 2 - w * 0.07f, dialog.bottom - h * 0.06f)
}

@Composable
private fun AppPhone(millis: Int, stillAt: Float, draw: DrawScope.(AppScene, Float) -> Unit) {
    val colors = AppColors(MaterialTheme.colorScheme)
    val icons = rememberAppIcons()
    MiniPhone(millis, stillAt, colors.surface) { screen, t -> draw(AppScene(screen, colors, icons, gap = 5.dp.toPx()), t) }
}

/** The last page: the Quick Settings tile opens the Recent tab, drawn on the tile tutorial's phone. */
@Composable
private fun TileShortcutPhone() {
    val colors = AppColors(MaterialTheme.colorScheme)
    val icons = rememberAppIcons()
    QuickSettingsPhone(5600, 0.46f, R.drawable.ic_tile_add) { s, t ->
        tileShortcut(s, AppScene(s.screen, colors, icons, s.gap), t)
    }
}

/**
 * Page 1: tap Grant access, switch NotiFilter on in Android's settings and allow it, then come back
 * to find the access card gone.
 */
private fun DrawScope.grantAccess(a: AppScene, t: Float) {
    val screen = a.screen
    val toSettings = phase(t, 0.24f, 0.32f)
    val dialog = phase(t, 0.44f, 0.48f) * (1f - phase(t, 0.6f, 0.64f))
    val on = phase(t, 0.6f, 0.66f)
    val back = phase(t, 0.72f, 0.8f)
    // The card goes while the settings screen still covers the app, so the layout doesn't visibly jump.
    val card = if (back > 0f) 0f else a.accessCardHeight

    drawMain(a, card, tab = 0f) { top -> drawEmptyRules(a, top) }
    if (toSettings > 0f && back < 1f) translate(screen.width * (1f - toSettings + back), 0f) { drawAccessSettings(a, on) }
    if (dialog > 0f) drawPermissionDialog(a, dialog)

    val button = a.grantButton.center
    val position = when {
        t < 0.3f -> lerp(button + Offset(screen.width * 0.15f, screen.height * 0.3f), button, phase(t, 0.06f, 0.16f))
        t < 0.46f -> lerp(button, a.settingsSwitch, phase(t, 0.3f, 0.37f))
        else -> lerp(a.settingsSwitch, a.allowButton, phase(t, 0.48f, 0.54f))
    }
    val ripple = tapRipple(t, 0.16f, 0.24f) + tapRipple(t, 0.37f, 0.44f) + tapRipple(t, 0.54f, 0.6f)
    drawFinger(a.colors.finger, position, phase(t, 0.03f, 0.07f) * (1f - phase(t, 0.66f, 0.7f)), ripple)
    fadeEnds(a.colors.surface, t)
}

/** Page 2: an unwanted notification pops up; open Recent, tap it, and the rule editor opens. */
private fun DrawScope.pickFromRecent(a: AppScene, t: Float) {
    val screen = a.screen
    val headsUp = phase(t, 0.05f, 0.15f) * (1f - phase(t, 0.36f, 0.44f))
    val tab = phase(t, 0.55f, 0.62f)
    val pressed = phase(t, 0.7f, 0.74f) * (1f - phase(t, 0.84f, 0.88f))
    val editor = phase(t, 0.76f, 0.86f)

    drawMain(a, 0f, tab) { top ->
        drawEmptyRules(a, top, alpha = 1f - tab)
        drawRecent(a, top, alpha = tab, pressed = pressed)
    }
    if (headsUp > 0f) drawHeadsUp(a, Kind.PROMO, mix(screen.top - a.h * 0.14f, screen.top + a.gap, headsUp))
    if (editor > 0f) translate((1f - editor) * screen.width, 0f) { drawEditor(a) }

    val recentTab = a.tabCenter(1f)
    val position = if (t < 0.62f) {
        lerp(recentTab + Offset(screen.width * 0.1f, screen.height * 0.35f), recentTab, phase(t, 0.46f, 0.54f))
    } else {
        lerp(recentTab, a.rowCenter(0), phase(t, 0.62f, 0.7f))
    }
    val ripple = tapRipple(t, 0.54f, 0.6f) + tapRipple(t, 0.7f, 0.76f)
    drawFinger(a.colors.finger, position, phase(t, 0.44f, 0.48f) * (1f - phase(t, 0.8f, 0.84f)), ripple)
    fadeEnds(a.colors.surface, t)
}

/**
 * Page 3: Save the rule. Back on Recent, the notification is dismissed straight away, and the next
 * one like it is hidden the moment it arrives.
 */
private fun DrawScope.saveRule(a: AppScene, t: Float) {
    val screen = a.screen
    val leave = phase(t, 0.22f, 0.32f)
    val promo = 1f - phase(t, 0.34f, 0.44f)
    val arrive = phase(t, 0.5f, 0.6f)
    val sweep = phase(t, 0.62f, 0.7f)
    val badge = phase(t, 0.62f, 0.68f) * (1f - phase(t, 0.76f, 0.84f))

    drawMain(a, 0f, tab = 1f) { top -> drawRecent(a, top, promo = promo) }
    if (leave < 1f) translate(leave * screen.width, 0f) { drawEditor(a) }

    val headsUpTop = mix(screen.top - a.h * 0.14f, screen.top + a.gap, arrive)
    if (arrive > 0f && sweep < 1f) drawHeadsUp(a, Kind.PROMO, headsUpTop, dx = sweep * screen.width, alpha = 1f - sweep)
    if (badge > 0f) {
        val center = Offset(screen.center.x, headsUpTop + a.h * 0.06f)
        val r = a.w * 0.08f * (0.6f + 0.4f * badge)
        drawCircle(a.colors.accent, r, center, badge)
        drawIconAt(a.icons.filter, center, r * 1.2f, a.colors.onAccent, badge)
    }

    val position = lerp(a.saveButton + Offset(-screen.width * 0.2f, screen.height * 0.3f), a.saveButton, phase(t, 0.05f, 0.15f))
    drawFinger(a.colors.finger, position, phase(t, 0.02f, 0.06f) * (1f - phase(t, 0.22f, 0.26f)), tapRipple(t, 0.15f, 0.22f))
    fadeEnds(a.colors.surface, t)
}

/** Page 4: the Filtered tab lists what was hidden; Open takes you where the notification would have. */
private fun DrawScope.openFiltered(a: AppScene, t: Float) {
    val screen = a.screen
    val tab = 1f + phase(t, 0.15f, 0.23f)
    val open = phase(t, 0.5f, 0.6f)

    drawMain(a, 0f, tab) { top ->
        drawRecent(a, top, alpha = 2f - tab, promo = 0f)
        drawFiltered(a, top, alpha = tab - 1f)
    }
    if (open > 0f) translate(0f, (1f - open) * screen.height) { drawShopApp(a) }

    val filteredTab = a.tabCenter(2f)
    val position = if (t < 0.26f) {
        lerp(filteredTab + Offset(-screen.width * 0.2f, screen.height * 0.35f), filteredTab, phase(t, 0.05f, 0.13f))
    } else {
        lerp(filteredTab, a.openCenter(0), phase(t, 0.28f, 0.38f))
    }
    val ripple = tapRipple(t, 0.13f, 0.2f) + tapRipple(t, 0.4f, 0.48f)
    drawFinger(a.colors.finger, position, phase(t, 0.02f, 0.06f) * (1f - phase(t, 0.54f, 0.58f)), ripple)
    fadeEnds(a.colors.surface, t)
}

/** Page 5: pull down the shade, tap the tile, and NotiFilter opens on Recent, ready to pick from. */
private fun DrawScope.tileShortcut(s: Scene, a: AppScene, t: Float) {
    val screen = s.screen
    val shade = phase(t, 0.08f, 0.28f)
    val app = phase(t, 0.5f, 0.6f)
    val ours = 3
    val shadeBottom = screen.top + screen.height * shade
    val firstRowBottom = s.gridTop + s.tileDiameter + s.gap

    drawHome(s)
    drawShade(s, screen.height * shade, 1f)
    clipRect(screen.left, screen.top, screen.right, shadeBottom) {
        drawPills(s, 1f)
        for (i in 0 until Scene.COLUMNS) drawTile(s, s.slot(i), ours = i == ours, alpha = 1f)
        drawHeadsUp(a, Kind.PROMO, firstRowBottom + s.gap)
        for (i in 1..2) {
            drawRoundRect(
                s.colors.card,
                Offset(screen.left + s.gap, firstRowBottom + s.gap + i * screen.height * 0.14f),
                Size(screen.width - 2 * s.gap, screen.height * 0.12f),
                CornerRadius(s.gap * 1.5f),
            )
        }
    }
    if (app > 0f) translate(0f, (1f - app) * screen.height) { drawMain(a, 0f, tab = 1f) { top -> drawRecent(a, top) } }

    val swipeFinger = phase(t, 0.02f, 0.08f) * (1f - phase(t, 0.28f, 0.33f))
    drawFinger(s.colors.finger, Offset(screen.center.x, shadeBottom.coerceAtLeast(screen.top + 8.dp.toPx())), swipeFinger)
    val tile = s.slot(ours)
    val position = if (t < 0.55f) {
        lerp(tile + Offset(0f, screen.height * 0.25f), tile, phase(t, 0.35f, 0.42f))
    } else {
        lerp(tile, a.rowCenter(0), phase(t, 0.62f, 0.7f))
    }
    val ripple = tapRipple(t, 0.42f, 0.5f) + tapRipple(t, 0.7f, 0.78f)
    drawFinger(s.colors.finger, position, phase(t, 0.33f, 0.37f) * (1f - phase(t, 0.8f, 0.85f)), ripple)
    fadeEnds(s.colors.wallpaper, t)
}

/**
 * The last page: open ⋮ and tap How it works, the menu's first item, and this tutorial opens again
 * (drawn as a dialog holding a miniature phone).
 */
private fun DrawScope.reopenTutorial(a: AppScene, t: Float) {
    val screen = a.screen
    val menu = phase(t, 0.2f, 0.28f) * (1f - phase(t, 0.54f, 0.6f))
    val pressed = phase(t, 0.42f, 0.46f)
    val tutorial = phase(t, 0.58f, 0.68f)

    drawMain(a, 0f, tab = 0f) { top -> drawEmptyRules(a, top) }
    if (menu > 0f) drawMenu(a, menu, pressed)
    if (tutorial > 0f) drawTutorialDialog(a, tutorial)

    val first = a.menuItemCenter(0)
    val position = if (t < 0.3f) {
        lerp(a.menuButton + Offset(-screen.width * 0.25f, screen.height * 0.35f), a.menuButton, phase(t, 0.05f, 0.15f))
    } else {
        lerp(a.menuButton, first, phase(t, 0.3f, 0.4f))
    }
    val ripple = tapRipple(t, 0.15f, 0.22f) + tapRipple(t, 0.42f, 0.5f)
    drawFinger(a.colors.finger, position, phase(t, 0.02f, 0.06f) * (1f - phase(t, 0.54f, 0.58f)), ripple)
    fadeEnds(a.colors.surface, t)
}

/** The ⋮ menu, unfolding from the top as [open] goes 0→1, with How it works first and [pressed] highlighting it. */
private fun DrawScope.drawMenu(a: AppScene, open: Float, pressed: Float) {
    val c = a.colors
    val menu = a.menu
    val corner = CornerRadius(a.gap)
    clipRect(menu.left, menu.top, menu.right, menu.top + menu.height * open) {
        drawRoundRect(c.dialog, menu.topLeft, menu.size, corner, alpha = open)
        drawRoundRect(c.divider, menu.topLeft, menu.size, corner, alpha = open, style = Stroke(1.dp.toPx()))
        if (pressed > 0f) {
            drawRect(c.pressed, Offset(menu.left, menu.top), Size(menu.width, a.menuItemHeight), alpha = pressed * open)
        }
        val x = menu.left + a.gap * 1.5f
        listOf(0.36f, 0.48f, 0.34f, 0.3f, 0.3f, 0.34f, 0.22f).forEachIndexed { i, width ->
            val y = a.menuItemCenter(i).y
            if (i == 0) {
                bar(a, x, y, a.w * width, c.accent, open, thick = true)
            } else {
                bar(a, x, y, a.w * width, c.ink, open)
            }
        }
    }
}

/** This tutorial, reopened: a dialog with a title, a miniature phone, a caption and page dots. */
private fun DrawScope.drawTutorialDialog(a: AppScene, alpha: Float) {
    val c = a.colors
    drawRect(c.scrim, alpha = alpha)
    val box = Rect(
        Offset(a.screen.left + a.w * 0.1f, a.screen.top + a.h * 0.18f),
        Size(a.w * 0.8f, a.h * 0.64f),
    )
    drawRoundRect(c.dialog, box.topLeft, box.size, CornerRadius(a.gap * 2.5f), alpha = alpha)
    bar(a, box.left + a.gap * 2, box.top + a.h * 0.05f, a.w * 0.5f, c.strongInk, alpha, thick = true)
    val phone = Rect(Offset(box.center.x - a.w * 0.14f, box.top + a.h * 0.1f), Size(a.w * 0.28f, a.h * 0.3f))
    drawRoundRect(c.strongInk, phone.topLeft, phone.size, CornerRadius(a.gap), alpha = alpha, style = Stroke(1.5.dp.toPx()))
    bar(a, box.center.x - a.w * 0.26f, phone.bottom + a.h * 0.045f, a.w * 0.52f, c.ink, alpha)
    bar(a, box.center.x - a.w * 0.2f, phone.bottom + a.h * 0.08f, a.w * 0.4f, c.ink, alpha)
    val dotY = phone.bottom + a.h * 0.12f
    val r = 1.5.dp.toPx()
    for (i in 0 until 6) {
        drawCircle(if (i == 0) c.accent else c.divider, r, Offset(box.center.x + (i - 2.5f) * r * 4, dotY), alpha)
    }
}

/** Fades the panel in at the start of each loop and out at the end, so the jump back isn't jarring. */
private fun DrawScope.fadeEnds(background: Color, t: Float) {
    val cover = 1f - phase(t, 0f, 0.05f) * (1f - phase(t, 0.92f, 1f))
    if (cover > 0f) drawRect(background, alpha = cover)
}

/** A rounded bar standing in for a line of text, [width] wide, vertically centered on [centerY]. */
private fun DrawScope.bar(a: AppScene, x: Float, centerY: Float, width: Float, color: Color, alpha: Float = 1f, thick: Boolean = false) {
    if (alpha <= 0f) return
    val height = if (thick) a.line * 1.3f else a.line
    drawRoundRect(color, Offset(x, centerY - height / 2), Size(width, height), CornerRadius(height / 2), alpha = alpha)
}

private fun DrawScope.drawIconAt(icon: Painter, center: Offset, size: Float, tint: Color, alpha: Float = 1f) {
    if (alpha <= 0f) return
    translate(center.x - size / 2, center.y - size / 2) {
        with(icon) { draw(Size(size, size), alpha, ColorFilter.tint(tint)) }
    }
}

private fun DrawScope.drawSwitch(a: AppScene, center: Offset, on: Float, alpha: Float = 1f) {
    val size = a.switchSize
    val topLeft = center - Offset(size.width / 2, size.height / 2)
    val c = a.colors
    drawRoundRect(lerp(c.divider, c.accent, on), topLeft, size, CornerRadius(size.height / 2), alpha = alpha)
    val r = size.height * 0.36f
    val x = mix(topLeft.x + size.height / 2, topLeft.x + size.width - size.height / 2, on)
    drawCircle(lerp(c.outline, c.onAccent, on), r, Offset(x, center.y), alpha)
}

/** NotiFilter's main screen, with [list] drawn below the tabs; [tab] slides the tab indicator. */
private fun DrawScope.drawMain(a: AppScene, card: Float, tab: Float, list: DrawScope.(top: Float) -> Unit) {
    val screen = a.screen
    val c = a.colors
    drawRect(c.surface, screen.topLeft, screen.size)
    bar(a, screen.left + a.gap * 2, a.appBarY, a.w * 0.34f, c.strongInk, thick = true)
    drawIconAt(a.icons.more, a.menuButton, a.iconSize, c.strongInk)
    if (card > 0f) drawAccessCard(a)

    val top = a.contentTop(card)
    val switchRow = top + a.h * 0.035f
    bar(a, screen.left + a.gap * 2, switchRow, a.w * 0.42f, c.strongInk)
    drawSwitch(a, Offset(screen.right - a.gap * 2 - a.switchSize.width / 2, switchRow), on = 1f)

    for (i in 0..2) {
        val center = a.tabCenter(i.toFloat(), card)
        val selected = 1f - abs(tab - i).coerceAtMost(1f)
        bar(a, center.x - a.w * 0.08f, center.y, a.w * 0.16f, lerp(c.ink, c.accent, selected))
    }
    val listTop = a.listTop(card)
    val indicator = 2.5.dp.toPx()
    val indicatorCenter = a.tabCenter(tab, card).x
    drawRoundRect(
        c.accent,
        Offset(indicatorCenter - a.w * 0.1f, listTop - indicator),
        Size(a.w * 0.2f, indicator),
        CornerRadius(indicator / 2),
    )
    drawLine(c.divider, Offset(screen.left, listTop), Offset(screen.right, listTop), 1.dp.toPx())
    clipRect(screen.left, listTop, screen.right, screen.bottom) { list(listTop) }
}

/** The "Notification access needed" card, with its Grant access button. */
private fun DrawScope.drawAccessCard(a: AppScene) {
    val c = a.colors
    val topLeft = Offset(a.screen.left + a.gap, a.contentTop(0f))
    drawRoundRect(c.warningCard, topLeft, Size(a.w - 2 * a.gap, a.accessCardHeight - a.gap), CornerRadius(a.gap * 1.5f))
    val x = topLeft.x + a.gap * 1.5f
    bar(a, x, topLeft.y + a.h * 0.035f, a.w * 0.55f, c.onWarning, thick = true)
    listOf(0.75f, 0.7f, 0.45f).forEachIndexed { i, width ->
        bar(a, x, topLeft.y + a.h * (0.075f + i * 0.03f), a.w * width, c.onWarning)
    }
    val button = a.grantButton
    drawRoundRect(c.accent, button.topLeft, button.size, CornerRadius(button.height / 2))
    bar(a, button.center.x - a.w * 0.13f, button.center.y, a.w * 0.26f, c.onAccent)
}

private fun DrawScope.drawEmptyRules(a: AppScene, top: Float, alpha: Float = 1f) {
    val c = a.colors
    bar(a, a.screen.center.x - a.w * 0.32f, top + a.h * 0.1f, a.w * 0.64f, c.ink, alpha)
    bar(a, a.screen.center.x - a.w * 0.24f, top + a.h * 0.135f, a.w * 0.48f, c.ink, alpha)
}

/**
 * The Recent tab. [promo] below 1 fades the unwanted notification out of the list and slides the
 * rest up, as when a new rule dismisses it; [pressed] highlights it as it's tapped.
 */
private fun DrawScope.drawRecent(a: AppScene, top: Float, alpha: Float = 1f, promo: Float = 1f, pressed: Float = 0f) {
    if (alpha <= 0f) return
    val c = a.colors
    drawListHeader(a, top, alpha, actions = 2)
    val first = a.rowTop(0)
    if (pressed > 0f) drawRect(c.pressed, Offset(a.screen.left, first), Size(a.w, a.rowHeight), alpha = pressed * alpha)
    drawNotificationRow(a, Kind.PROMO, first, alpha * promo)
    val shift = (1f - promo) * a.rowHeight
    drawNotificationRow(a, Kind.CHAT, a.rowTop(1) - shift, alpha)
    drawNotificationRow(a, Kind.MAIL, a.rowTop(2) - shift, alpha)
}

/** The Filtered tab: two hidden promos, each with an Open button. */
private fun DrawScope.drawFiltered(a: AppScene, top: Float, alpha: Float) {
    if (alpha <= 0f) return
    drawListHeader(a, top, alpha, actions = 1)
    for (i in 0..1) drawNotificationRow(a, Kind.PROMO, a.rowTop(i), alpha, open = true)
}

/** The hint line above a list, with its Refresh / Clear buttons on the right. */
private fun DrawScope.drawListHeader(a: AppScene, top: Float, alpha: Float, actions: Int) {
    val c = a.colors
    val y = top + a.listHeaderHeight / 2
    bar(a, a.screen.left + a.gap * 2, y, a.w * 0.44f, c.ink, alpha)
    for (i in 0 until actions) {
        bar(a, a.screen.right - a.gap * 2 - a.w * (0.12f + i * 0.17f), y, a.w * 0.12f, c.accent, alpha)
    }
    val bottom = top + a.listHeaderHeight
    drawLine(c.divider, Offset(a.screen.left, bottom), Offset(a.screen.right, bottom), 1.dp.toPx(), alpha = alpha)
}

/** One notification in a list: app icon, then app name, title and text as bars. */
private fun DrawScope.drawNotificationRow(a: AppScene, kind: Kind, top: Float, alpha: Float, open: Boolean = false) {
    if (alpha <= 0f) return
    val c = a.colors
    val r = a.w * 0.055f
    drawAppIcon(a, kind, Offset(a.screen.left + a.gap * 2 + r, top + a.rowHeight / 2), r, alpha)
    val x = a.screen.left + a.gap * 3 + 2 * r
    bar(a, x, top + a.rowHeight * 0.26f, a.w * 0.28f, c.ink, alpha)
    bar(a, x, top + a.rowHeight * 0.5f, a.w * (if (open) 0.36f else 0.42f), c.strongInk, alpha, thick = true)
    bar(a, x, top + a.rowHeight * 0.74f, a.w * (if (open) 0.4f else 0.52f), c.ink, alpha)
    if (open) {
        val center = a.openCenter(0).copy(y = top + a.rowHeight / 2)
        bar(a, center.x - a.w * 0.06f, center.y, a.w * 0.12f, c.accent, alpha, thick = true)
    }
    val bottom = top + a.rowHeight
    drawLine(c.divider, Offset(a.screen.left, bottom), Offset(a.screen.right, bottom), 1.dp.toPx(), alpha = alpha)
}

private fun DrawScope.drawAppIcon(a: AppScene, kind: Kind, center: Offset, r: Float, alpha: Float) {
    val c = a.colors
    val (icon, background, tint) = when (kind) {
        Kind.PROMO -> Triple(a.icons.promo, c.promoBackground, c.promo)
        Kind.CHAT -> Triple(a.icons.chat, c.iconBackground, c.icon)
        Kind.MAIL -> Triple(a.icons.mail, c.iconBackground, c.icon)
    }
    drawCircle(background, r, center, alpha)
    drawIconAt(icon, center, r * 1.2f, tint, alpha)
}

/** A notification card popping in at the top of the screen (or sitting in the shade), [top] being its top edge. */
private fun DrawScope.drawHeadsUp(a: AppScene, kind: Kind, top: Float, dx: Float = 0f, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val c = a.colors
    val topLeft = Offset(a.screen.left + a.gap + dx, top)
    val size = Size(a.w - 2 * a.gap, a.h * 0.12f)
    val corner = CornerRadius(a.gap * 1.5f)
    drawRoundRect(c.card, topLeft, size, corner, alpha = alpha)
    drawRoundRect(c.divider, topLeft, size, corner, alpha = alpha, style = Stroke(1.dp.toPx()))
    val r = a.w * 0.055f
    drawAppIcon(a, kind, Offset(topLeft.x + a.gap * 1.2f + r, topLeft.y + size.height / 2), r, alpha)
    val x = topLeft.x + a.gap * 2.2f + 2 * r
    bar(a, x, topLeft.y + size.height * 0.36f, a.w * 0.4f, c.strongInk, alpha, thick = true)
    bar(a, x, topLeft.y + size.height * 0.66f, a.w * 0.5f, c.ink, alpha)
}

/**
 * The rule editor, filled in from the promo: name left blank, its app, its title and text, and
 * below them the live preview of what the rule matches.
 */
private fun DrawScope.drawEditor(a: AppScene) {
    val screen = a.screen
    val c = a.colors
    drawRect(c.surface, screen.topLeft, screen.size)
    drawIconAt(a.icons.back, Offset(screen.left + a.gap * 2.4f, a.appBarY), a.iconSize, c.strongInk)
    bar(a, screen.left + a.gap * 5, a.appBarY, a.w * 0.26f, c.strongInk, thick = true)
    bar(a, a.saveButton.x - a.w * 0.07f, a.appBarY, a.w * 0.14f, c.accent, thick = true)

    val fieldHeight = a.h * 0.08f
    val x = screen.left + a.gap * 2
    var y = screen.top + a.h * 0.14f
    for (i in 0 until 4) {
        drawRoundRect(
            c.outline,
            Offset(x, y),
            Size(a.w - 4 * a.gap, fieldHeight),
            CornerRadius(a.gap * 0.8f),
            style = Stroke(1.dp.toPx()),
        )
        val center = y + fieldHeight / 2
        when (i) {
            0 -> bar(a, x + a.gap * 1.5f, center, a.w * 0.3f, c.ink)
            1 -> {
                val r = a.w * 0.04f
                drawAppIcon(a, Kind.PROMO, Offset(x + a.gap * 1.5f + r, center), r, 1f)
                bar(a, x + a.gap * 2.5f + 2 * r, center, a.w * 0.3f, c.strongInk)
            }
            2 -> bar(a, x + a.gap * 1.5f, center, a.w * 0.42f, c.strongInk)
            else -> bar(a, x + a.gap * 1.5f, center, a.w * 0.52f, c.strongInk)
        }
        y += a.h * 0.11f
    }
    bar(a, x, y + a.h * 0.02f, a.w * 0.4f, c.ink)
    drawNotificationRow(a, Kind.PROMO, y + a.h * 0.045f, 1f)
}

/** Android's notification access page for NotiFilter, with its switch [on] from 0 to 1. */
private fun DrawScope.drawAccessSettings(a: AppScene, on: Float) {
    val screen = a.screen
    val c = a.colors
    drawRect(c.surface, screen.topLeft, screen.size)
    drawIconAt(a.icons.back, Offset(screen.left + a.gap * 2.4f, a.appBarY), a.iconSize, c.strongInk)
    bar(a, screen.left + a.gap * 5, a.appBarY, a.w * 0.45f, c.strongInk, thick = true)

    val iconCenter = Offset(screen.center.x, screen.top + a.h * 0.24f)
    val r = a.w * 0.1f
    drawCircle(c.accent, r, iconCenter)
    drawIconAt(a.icons.filter, iconCenter, r * 1.2f, c.onAccent)
    bar(a, screen.center.x - a.w * 0.15f, screen.top + a.h * 0.335f, a.w * 0.3f, c.strongInk, thick = true)

    bar(a, screen.left + a.gap * 2, a.settingsSwitch.y, a.w * 0.5f, c.strongInk)
    drawSwitch(a, a.settingsSwitch, on)
    bar(a, screen.left + a.gap * 2, screen.top + a.h * 0.52f, a.w * 0.75f, c.ink)
    bar(a, screen.left + a.gap * 2, screen.top + a.h * 0.555f, a.w * 0.6f, c.ink)
}

/** Android's "Allow notification access?" confirmation, with its warning and Allow button. */
private fun DrawScope.drawPermissionDialog(a: AppScene, alpha: Float) {
    val c = a.colors
    drawRect(c.scrim, alpha = alpha)
    val box = a.dialog
    drawRoundRect(c.dialog, box.topLeft, box.size, CornerRadius(a.gap * 2.5f), alpha = alpha)
    val x = box.left + a.gap * 2
    bar(a, x, box.top + a.h * 0.05f, a.w * 0.55f, c.strongInk, alpha, thick = true)
    listOf(0.7f, 0.72f, 0.66f, 0.4f).forEachIndexed { i, width ->
        bar(a, x, box.top + a.h * (0.1f + i * 0.035f), a.w * width, c.ink, alpha)
    }
    val buttonY = a.allowButton.y
    bar(a, a.allowButton.x - a.w * 0.07f, buttonY, a.w * 0.14f, c.accent, alpha, thick = true)
    bar(a, a.allowButton.x - a.w * 0.32f, buttonY, a.w * 0.18f, c.accent, alpha, thick = true)
}

/** The shop's app, opened from a filtered notification's Open button. */
private fun DrawScope.drawShopApp(a: AppScene) {
    val screen = a.screen
    val c = a.colors
    drawRect(c.promoBackground, screen.topLeft, screen.size)
    drawIconAt(a.icons.promo, Offset(screen.center.x, screen.top + a.h * 0.3f), a.w * 0.3f, c.promo)
    bar(a, screen.center.x - a.w * 0.3f, screen.top + a.h * 0.48f, a.w * 0.6f, c.promo.copy(alpha = 0.6f), thick = true)
    bar(a, screen.center.x - a.w * 0.22f, screen.top + a.h * 0.53f, a.w * 0.44f, c.promo.copy(alpha = 0.4f))
}

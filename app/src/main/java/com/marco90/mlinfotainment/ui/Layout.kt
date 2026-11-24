package com.marco90.mlinfotainment.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.marco90.mlinfotainment.data.CarSettings
import com.marco90.mlinfotainment.data.Cell
import com.marco90.mlinfotainment.data.GridLayout
import com.marco90.mlinfotainment.data.Module
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** Pressione lunga che arriva prima dei contenuti (anche dei widget) e li annulla. */
private fun Modifier.longPressFirst(onLong: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = withTimeoutOrNull(250L) {
            waitForUpOrCancellation(PointerEventPass.Initial)
        }
        if (up == null) {
            onLong()
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                if (ev.changes.all { !it.pressed }) break
            }
        }
    }
}

/** I moduli e i widget su una griglia: tieni premuto per spostarli e ridimensionarli. */
@Composable
fun ModuleGrid(a: DashActions, landscape: Boolean, edit: Boolean, onEdit: (Boolean) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val s = remember { CarSettings.get(ctx) }
    val cols = if (landscape) GridLayout.LAND_COLS else GridLayout.PORT_COLS
    val rows = if (landscape) GridLayout.LAND_ROWS else GridLayout.PORT_ROWS
    val keys = buildList {
        s.activeModules.forEach { m ->
            if (m == Module.WIDGETS) s.widgetList.forEach { add("w:$it") } else add(m.key)
        }
    }
    val stored = if (landscape) s.layoutLand else s.layoutPort
    val cells = remember(stored, keys, cols, rows) { GridLayout.resolve(stored, keys, cols, rows, landscape) }
    val addWidget = rememberWidgetAdder()

    BoxWithConstraints(modifier) {
        val d = LocalDensity.current
        val cwPx = with(d) { maxWidth.toPx() } / cols
        val chPx = with(d) { maxHeight.toPx() } / rows
        cells.forEach { cell ->
            key(cell.key) {
                GridItem(
                    cell = cell,
                    others = cells.filter { it.key != cell.key },
                    cols = cols, rows = rows, cwPx = cwPx, chPx = chPx,
                    edit = edit,
                    onEnterEdit = { onEdit(true) },
                    onChange = { ch ->
                        val m = ch.associateBy { it.key }
                        s.saveLayout(landscape, cells.map { m[it.key] ?: it })
                    },
                    onRemove = if (GridLayout.isWidget(cell.key)) ({
                        val id = cell.key.substring(2).toIntOrNull()
                        if (id != null) {
                            try { WidgetHost.host(ctx).deleteAppWidgetId(id) } catch (e: Exception) { }
                            s.removeWidget(id)
                        }
                    }) else null
                ) { m -> ModuleView(cell.key, m, a) }
            }
        }
        if (cells.isEmpty()) EmptyHint()
        if (edit) {
            val c = LocalAuto.current
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .zIndex(5f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.rail)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Pill("Widget", Icons.Rounded.Add, false) { addWidget() }
                Pill("Ripristina", Icons.Rounded.Restore, false) { s.resetLayout(landscape) }
                Pill("Fine", Icons.Rounded.Check, true) { onEdit(false) }
            }
        }
    }
}

@Composable
private fun Pill(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    val c = LocalAuto.current
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (primary) c.accent else c.cardHigh)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (primary) c.onAccent else c.onCard, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (primary) c.onAccent else c.onCard, fontSize = 15.sp)
    }
}

/** Quanto il dito ha trascinato (in pixel) rispetto alla posizione di partenza. */
private class DragState {
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var dw by mutableFloatStateOf(0f)
    var dh by mutableFloatStateOf(0f)
    val active: Boolean get() = dx != 0f || dy != 0f || dw != 0f || dh != 0f
    fun reset() { dx = 0f; dy = 0f; dw = 0f; dh = 0f }
}

@Composable
private fun GridItem(
    cell: Cell,
    others: List<Cell>,
    cols: Int,
    rows: Int,
    cwPx: Float,
    chPx: Float,
    edit: Boolean,
    onEnterEdit: () -> Unit,
    onChange: (List<Cell>) -> Unit,
    onRemove: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit
) {
    val c = LocalAuto.current
    val d = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val ds = remember { DragState() }
    val cur by rememberUpdatedState(cell)
    val oth by rememberUpdatedState(others)
    val cwNow by rememberUpdatedState(cwPx)
    val chNow by rememberUpdatedState(chPx)
    val colsNow by rememberUpdatedState(cols)
    val rowsNow by rememberUpdatedState(rows)
    val (minW, minH) = GridLayout.minSize(cell.key)
    val moving by remember { derivedStateOf { ds.active } }

    /** Dove finirebbe l'elemento se lo lasciassi adesso e cosa si sposta per fargli posto. */
    fun plan(): List<Cell>? {
        val k = cur
        val cw = cwNow
        val ch = chNow
        val cl = colsNow
        val rw = rowsNow
        if (ds.dw != 0f || ds.dh != 0f) {
            val t = k.copy(
                w = (k.w + (ds.dw / cw).roundToInt()).coerceIn(minW, maxOf(minW, cl - k.x)),
                h = (k.h + (ds.dh / ch).roundToInt()).coerceIn(minH, maxOf(minH, rw - k.y))
            )
            if (oth.none { GridLayout.overlap(it, t) }) return listOf(t)
            GridLayout.pushAside(t, oth, cl, rw)?.let { return it }
            var w = t.w
            while (w >= minW) {
                var h = t.h
                while (h >= minH) {
                    val cand = t.copy(w = w, h = h)
                    if (oth.none { GridLayout.overlap(it, cand) }) return listOf(cand)
                    h--
                }
                w--
            }
            return null
        }
        val t = k.copy(
            x = (k.x + (ds.dx / cw).roundToInt()).coerceIn(0, maxOf(0, cl - k.w)),
            y = (k.y + (ds.dy / ch).roundToInt()).coerceIn(0, maxOf(0, rw - k.h))
        )
        if (oth.none { GridLayout.overlap(it, t) }) return listOf(t)
        GridLayout.pushAside(t, oth, cl, rw)?.let { return it }
        return GridLayout.nearestFree(t, oth, cl, rw)?.let { listOf(it) }
    }

    fun landing(): Cell? = plan()?.firstOrNull()

    fun commit() {
        val p = plan()
        if (p != null && p.firstOrNull() != cur || (p?.size ?: 0) > 1) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(p!!)
        }
        ds.reset()
    }

    // La cella ferma (posizione della griglia); il contenuto si muove dentro, il dito lavora sulla cella ferma.
    Box(
        Modifier
            .zIndex(if (moving) 2f else 0f)
            .offset { IntOffset((cell.x * cwPx).roundToInt(), (cell.y * chPx).roundToInt()) }
            .size(with(d) { (cell.w * cwPx).toDp() }, with(d) { (cell.h * chPx).toDp() })
            .padding(6.dp)
            .then(
                if (!edit) Modifier.longPressFirst {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onEnterEdit()
                } else Modifier
            )
    ) {
        if (edit) Ghost(cell, ds, cwPx, chPx, ::landing)
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(ds.dx.roundToInt(), ds.dy.roundToInt()) }
        ) {
            content(Modifier.fillMaxSize())
            if (edit) EditFrame(ds, ::landing)
        }
        if (edit) {
            ResizeOutline(cell, ds, cwPx, chPx, minW, minH)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { commit() },
                            onDragCancel = { ds.reset() }
                        ) { change, amt ->
                            change.consume()
                            ds.dx += amt.x
                            ds.dy += amt.y
                        }
                    }
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(c.accent)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { commit() },
                            onDragCancel = { ds.reset() }
                        ) { change, amt ->
                            change.consume()
                            ds.dw += amt.x
                            ds.dh += amt.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.OpenInFull, "Ridimensiona", tint = c.onAccent, modifier = Modifier.size(22.dp)) }
            if (onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Close, "Rimuovi", tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

/** Bordo e velo sull'elemento in modifica (rosso se non c'è posto). */
@Composable
private fun EditFrame(ds: DragState, landing: () -> Cell?) {
    val c = LocalAuto.current
    val shape = RoundedCornerShape(28.dp)
    val bad = ds.active && landing() == null
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(if (bad) Color.Red.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.15f))
            .border(2.dp, if (bad) Color.Red else c.accent, shape)
    )
}

/** Il rettangolo che mostra dove l'elemento si fermerà. */
@Composable
private fun Ghost(cell: Cell, ds: DragState, cwPx: Float, chPx: Float, landing: () -> Cell?) {
    if (!ds.active) return
    val l = landing() ?: return
    val c = LocalAuto.current
    val d = LocalDensity.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .offset { IntOffset(((l.x - cell.x) * cwPx).roundToInt(), ((l.y - cell.y) * chPx).roundToInt()) }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(with(d) { (l.w * cwPx).toDp() - 12.dp }, with(d) { (l.h * chPx).toDp() - 12.dp })
            .clip(shape)
            .background(c.accent.copy(alpha = 0.25f))
            .border(2.dp, c.accent, shape)
    )
}

/** Il bordo che segue il dito mentre ridimensioni. */
@Composable
private fun ResizeOutline(cell: Cell, ds: DragState, cwPx: Float, chPx: Float, minW: Int, minH: Int) {
    if (ds.dw == 0f && ds.dh == 0f) return
    val c = LocalAuto.current
    val d = LocalDensity.current
    val w = maxOf(cell.w * cwPx + ds.dw, minW * cwPx)
    val h = maxOf(cell.h * chPx + ds.dh, minH * chPx)
    Box(
        Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .size(with(d) { w.toDp() - 12.dp }, with(d) { h.toDp() - 12.dp })
            .border(2.dp, c.accent, RoundedCornerShape(28.dp))
    )
}

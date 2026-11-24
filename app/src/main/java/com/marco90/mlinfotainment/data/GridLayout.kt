package com.marco90.mlinfotainment.data

/** Un elemento della schermata: posizione e dimensione in celle della griglia. */
data class Cell(val key: String, val x: Int, val y: Int, val w: Int, val h: Int)

object GridLayout {
    const val LAND_COLS = 12
    const val LAND_ROWS = 6
    const val PORT_COLS = 6
    const val PORT_ROWS = 12

    fun isWidget(key: String) = key.startsWith("w:")

    fun minSize(key: String): Pair<Int, Int> = if (isWidget(key)) 2 to 2 else 3 to 2

    private fun defaults(land: Boolean): Map<String, Cell> =
        (if (land) listOf(
            Cell("nav", 0, 0, 7, 2), Cell("apps", 0, 2, 7, 2),
            Cell("speed", 7, 0, 5, 2), Cell("music", 7, 2, 5, 2), Cell("weather", 7, 4, 5, 2)
        ) else listOf(
            Cell("nav", 0, 0, 6, 2), Cell("speed", 0, 2, 6, 2), Cell("music", 0, 4, 6, 2),
            Cell("apps", 0, 6, 6, 2), Cell("weather", 0, 8, 6, 2)
        )).associateBy { it.key }

    fun parse(s: String): List<Cell> = s.split(";").mapNotNull { part ->
        val p = part.split(",")
        if (p.size != 5) return@mapNotNull null
        val n = p.drop(1).map { it.toIntOrNull() ?: return@mapNotNull null }
        Cell(p[0], n[0], n[1], n[2], n[3])
    }

    fun encode(list: List<Cell>): String = list.joinToString(";") { "${it.key},${it.x},${it.y},${it.w},${it.h}" }

    fun overlap(a: Cell, b: Cell) = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

    private fun clamp(c: Cell, cols: Int, rows: Int): Cell {
        val (mw, mh) = minSize(c.key)
        val w = c.w.coerceIn(mw, maxOf(mw, cols))
        val h = c.h.coerceIn(mh, maxOf(mh, rows))
        return c.copy(x = c.x.coerceIn(0, maxOf(0, cols - w)), y = c.y.coerceIn(0, maxOf(0, rows - h)), w = w, h = h)
    }

    /** Posizioni salvate per gli elementi attivi; i nuovi vanno nel primo spazio libero. */
    fun resolve(stored: String, keys: List<String>, cols: Int, rows: Int, land: Boolean): List<Cell> {
        val st = parse(stored).associateBy { it.key }
        val placed = mutableListOf<Cell>()
        for (k in keys) {
            val c = st[k]?.let { clamp(it, cols, rows) } ?: continue
            if (placed.none { overlap(it, c) }) placed += c
        }
        val defs = defaults(land)
        for (k in keys) {
            if (placed.any { it.key == k }) continue
            val (mw, mh) = minSize(k)
            val d = defs[k] ?: Cell(k, 0, 0, if (land) 4 else 6, 2)
            var found: Cell? = null
            val first = clamp(d, cols, rows)
            if (placed.none { overlap(it, first) }) found = first
            var w = first.w
            while (found == null && w >= mw) {
                var h = first.h
                while (found == null && h >= mh) {
                    for (y in 0..(rows - h)) {
                        for (x in 0..(cols - w)) {
                            val c = Cell(k, x, y, w, h)
                            if (placed.none { overlap(it, c) }) { found = c; break }
                        }
                        if (found != null) break
                    }
                    h--
                }
                w--
            }
            placed += found ?: Cell(k, 0, 0, mw, mh)
        }
        return placed
    }
}

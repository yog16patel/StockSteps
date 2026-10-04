package org.example.stocksteps

/** Folding bounds in window pixels, supplied by the native host. */
data class WindowHinge(val left: Float, val top: Float, val right: Float, val bottom: Float, val vertical: Boolean)

internal data class PaneRect(val x: Float, val y: Float, val width: Float, val height: Float)
internal data class PaneLayout(val search: PaneRect, val detail: PaneRect? = null)

internal fun paneLayout(width: Float, height: Float, density: Float, originX: Float, originY: Float, hinge: WindowHinge?): PaneLayout {
    val gap = 24 * density
    if (hinge != null) {
        val start = (if (hinge.vertical) hinge.left - originX else hinge.top - originY)
        val end = (if (hinge.vertical) hinge.right - originX else hinge.bottom - originY)
        val extent = if (hinge.vertical) width else height
        if (start < extent && end > 0) {
            val before = (start - gap / 2).coerceIn(0f, extent)
            val after = (end + gap / 2).coerceIn(0f, extent)
            val first = if (hinge.vertical) PaneRect(0f, 0f, before, height) else PaneRect(0f, 0f, width, before)
            val second = if (hinge.vertical) PaneRect(after, 0f, width - after, height) else PaneRect(0f, after, width, height - after)
            val minimum = (if (hinge.vertical) 280 else 200) * density
            return if (before >= minimum && extent - after >= minimum) PaneLayout(first, second)
            else PaneLayout(if (before >= extent - after) first else second)
        }
    }
    if (width / density >= 840) {
        val sidebar = (width - gap) * 0.42f
        return PaneLayout(PaneRect(0f, 0f, sidebar, height), PaneRect(sidebar + gap, 0f, width - sidebar - gap, height))
    }
    return PaneLayout(PaneRect(0f, 0f, width, height))
}

package org.example.stocksteps.designsystem

import org.example.stocksteps.designsystem.icons.StockIcons
import kotlin.test.Test
import kotlin.test.assertTrue

/** The insight category icons are path data parsed on first use; a malformed path would only fail at runtime, so build them here. */
class StockIconsTest {
    @Test
    fun insightCategoryIconsBuild() {
        listOf(StockIcons.TrendingUp, StockIcons.BarChart, StockIcons.Tag, StockIcons.Speed, StockIcons.Layers, StockIcons.Percent, StockIcons.Coin,
            StockIcons.Lightbulb).forEach { icon -> assertTrue(icon.root.iterator().hasNext(), icon.name) }
    }
}

package org.example.stocksteps.presentation.discovery

import org.example.stocksteps.companydetail.PriceDirection
import org.example.stocksteps.home.HomePresentation
import org.example.stocksteps.model.MarketMover

/**
 * Phase 8B: the legacy Discovery movers line, shared by Compose and SwiftUI. It used to print "Unavailable% · Price Unavailable" and raw
 * doubles ("1.2345%"); now a missing change and a missing price each say so in words, a real zero is "0.00%", and values use the existing
 * Home formatters (two decimals, sign, currency). Formatting only — nothing is computed or estimated.
 */
object MarketMoverDisplay {
    const val CHANGE_UNAVAILABLE = "Change unavailable"
    const val PRICE_UNAVAILABLE = "Price unavailable"

    /** "+1.23%", "-0.40%", "0.00%" (a valid zero), or "Change unavailable" for a missing or non-finite value. */
    fun change(percent: Double?): String =
        if (HomePresentation.direction(percent) == PriceDirection.UNAVAILABLE) CHANGE_UNAVAILABLE else HomePresentation.percent(percent)

    /** "$12.34" (the movers lists are US dollar prices), or "Price unavailable"; never "$0.00" for a missing price. */
    fun price(price: Double?): String = price?.let { HomePresentation.price(it, "USD") } ?: PRICE_UNAVAILABLE

    fun line(mover: MarketMover): String = "${change(mover.changePercent)} · ${price(mover.price)}"
}

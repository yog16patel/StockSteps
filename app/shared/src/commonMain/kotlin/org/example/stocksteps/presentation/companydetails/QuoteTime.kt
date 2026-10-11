package org.example.stocksteps.presentation.companydetails

/** Quote time in exchange (New York) time, e.g. "10:41 AM EDT"; null when it cannot be formatted. */
internal expect fun formatQuoteTime(epochSeconds: Long): String?

/** Exchange (New York) calendar date of a timestamp, "yyyy-MM-dd"; null when it cannot be formatted. */
internal expect fun exchangeDate(epochSeconds: Long): String?

/** Today's exchange (New York) calendar date from the device clock, "yyyy-MM-dd"; only ever compared with a quote's own date. */
internal expect fun currentExchangeDate(): String?

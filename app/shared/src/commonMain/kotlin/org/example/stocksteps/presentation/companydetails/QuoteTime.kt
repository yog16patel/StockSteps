package org.example.stocksteps.presentation.companydetails

/** Quote time in exchange (New York) time, e.g. "10:41 AM EDT"; null when it cannot be formatted. */
internal expect fun formatQuoteTime(epochSeconds: Long): String?

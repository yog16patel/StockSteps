package org.example.stocksteps.presentation.companydetails

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formatter = DateTimeFormatter.ofPattern("h:mm a z", Locale.US).withZone(ZoneId.of("America/New_York"))

internal actual fun formatQuoteTime(epochSeconds: Long): String? =
    runCatching { formatter.format(Instant.ofEpochSecond(epochSeconds)) }.getOrNull()

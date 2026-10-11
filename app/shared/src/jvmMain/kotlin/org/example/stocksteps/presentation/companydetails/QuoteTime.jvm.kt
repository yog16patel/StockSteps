package org.example.stocksteps.presentation.companydetails

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val exchangeZone = ZoneId.of("America/New_York")
private val formatter = DateTimeFormatter.ofPattern("h:mm a z · MMM d", Locale.US).withZone(exchangeZone)

internal actual fun formatQuoteTime(epochSeconds: Long): String? =
    runCatching { formatter.format(Instant.ofEpochSecond(epochSeconds)) }.getOrNull()

internal actual fun exchangeDate(epochSeconds: Long): String? =
    runCatching { Instant.ofEpochSecond(epochSeconds).atZone(exchangeZone).toLocalDate().toString() }.getOrNull()

internal actual fun currentExchangeDate(): String? = runCatching { LocalDate.now(exchangeZone).toString() }.getOrNull()

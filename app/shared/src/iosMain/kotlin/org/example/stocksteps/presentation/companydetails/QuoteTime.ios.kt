package org.example.stocksteps.presentation.companydetails

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

internal actual fun formatQuoteTime(epochSeconds: Long): String? =
    exchangeFormat("h:mm a z · MMM d", NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))

internal actual fun exchangeDate(epochSeconds: Long): String? =
    exchangeFormat("yyyy-MM-dd", NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))

internal actual fun currentExchangeDate(): String? = exchangeFormat("yyyy-MM-dd", NSDate())

private fun exchangeFormat(pattern: String, date: NSDate): String? {
    val formatter = NSDateFormatter()
    formatter.dateFormat = pattern
    formatter.locale = NSLocale(localeIdentifier = "en_US_POSIX")
    formatter.timeZone = NSTimeZone.timeZoneWithName("America/New_York") ?: return null
    return formatter.stringFromDate(date)
}

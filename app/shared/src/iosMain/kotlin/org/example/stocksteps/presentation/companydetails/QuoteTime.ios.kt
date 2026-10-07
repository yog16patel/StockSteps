package org.example.stocksteps.presentation.companydetails

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

internal actual fun formatQuoteTime(epochSeconds: Long): String? {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "h:mm a z"
    formatter.locale = NSLocale(localeIdentifier = "en_US")
    formatter.timeZone = NSTimeZone.timeZoneWithName("America/New_York") ?: return null
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochSeconds.toDouble()))
}

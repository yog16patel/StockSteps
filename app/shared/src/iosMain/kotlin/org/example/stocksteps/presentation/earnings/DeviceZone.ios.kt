package org.example.stocksteps.presentation.earnings

import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun deviceTimeZoneId(): String = NSTimeZone.localTimeZone.name

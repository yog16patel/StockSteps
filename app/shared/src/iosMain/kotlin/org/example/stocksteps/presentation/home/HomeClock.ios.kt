package org.example.stocksteps.presentation.home

import platform.Foundation.*

internal actual fun localHomeHour(): Int = NSCalendar.currentCalendar.component(NSCalendarUnitHour, NSDate()).toInt()

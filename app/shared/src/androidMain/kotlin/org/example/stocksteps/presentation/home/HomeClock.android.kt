package org.example.stocksteps.presentation.home

internal actual fun localHomeHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

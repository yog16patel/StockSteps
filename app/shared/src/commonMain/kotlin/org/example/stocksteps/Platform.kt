package org.example.stocksteps

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
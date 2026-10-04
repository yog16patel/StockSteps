package org.example.stocksteps.model

data class User(val id: String, val email: String?)

data class AuthSession(
    val user: User? = null,
    val initializing: Boolean = true,
    val configurationError: String? = null
)

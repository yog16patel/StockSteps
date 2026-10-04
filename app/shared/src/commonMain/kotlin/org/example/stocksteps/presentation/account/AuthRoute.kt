package org.example.stocksteps.presentation.account

import kotlinx.serialization.Serializable

@Serializable
internal data class AuthRoute(val signup: Boolean = false)

package org.example.stocksteps.appconfig

object AppConfig {

    val finHubApiKey: String =
        System.getenv("FINHUB_API_KEY") ?: error("FINHUB_API_KEY is missing")

    val fmpApiKey: String =
        System.getenv("FMP_API_KEY") ?: error("FMP_API_KEY missing")

}
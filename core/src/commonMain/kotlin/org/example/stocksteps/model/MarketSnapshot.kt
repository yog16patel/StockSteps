package org.example.stocksteps.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

@Serializable
enum class MarketStatus { OPEN, CLOSED, PRE_MARKET, AFTER_HOURS, UNKNOWN }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarketIndex(
    val symbol: String,
    val name: String,
    @EncodeDefault
    val price: Double? = null,
    @EncodeDefault
    val change: Double? = null,
    @EncodeDefault
    val changePercent: Double? = null,
    @EncodeDefault
    val isProxy: Boolean = true,
    @EncodeDefault
    val error: ApiError? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SnapshotSectionError(val section: String, val error: ApiError)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MarketSnapshot(
    @EncodeDefault
    val marketStatus: MarketStatus = MarketStatus.UNKNOWN,
    @EncodeDefault
    val indices: List<MarketIndex> = emptyList(),
    @EncodeDefault
    val gainers: List<MarketMover> = emptyList(),
    @EncodeDefault
    val losers: List<MarketMover> = emptyList(),
    @EncodeDefault
    val mostActive: List<MarketMover> = emptyList(),
    val lastUpdated: String,
    @EncodeDefault
    val errors: List<SnapshotSectionError> = emptyList()
)

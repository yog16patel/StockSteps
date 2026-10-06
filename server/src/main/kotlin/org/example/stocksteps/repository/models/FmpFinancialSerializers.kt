package org.example.stocksteps.repository.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** FMP may encode the same fiscal-year identifier as a JSON string or integer. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object FmpFiscalYearSerializer : KSerializer<String?> {
    override val descriptor = String.serializer().nullable.descriptor
    override fun deserialize(decoder: Decoder): String? =
        (decoder as JsonDecoder).decodeJsonElement().let { element ->
            (element as? JsonPrimitive)?.contentOrNull?.takeIf { it.toIntOrNull() != null }
        }
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** A malformed optional number must not discard all other metrics in a statement. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object FmpFinancialNumberSerializer : KSerializer<Double?> {
    override val descriptor = Double.serializer().nullable.descriptor
    override fun deserialize(decoder: Decoder): Double? =
        ((decoder as JsonDecoder).decodeJsonElement() as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

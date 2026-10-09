package org.example.stocksteps.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.LayoutBase
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Phase 5A: one JSON object per line for Cloud Run (`LOG_FORMAT=json`), which Cloud Logging turns into `jsonPayload` with `severity` and
 * `message` recognised. Carries exactly what the text pattern does (time, thread, logger, level, message, stack trace) — no MDC, no request
 * data — so the Phase 4A-0 rule still holds: only sanitized application messages reach the logs.
 */
class CloudLoggingJsonLayout : LayoutBase<ILoggingEvent>() {
    override fun doLayout(event: ILoggingEvent): String {
        val stack = event.throwableProxy?.let { ThrowableProxyUtil.asString(it) }
        return buildJsonObject {
            put("severity", severity(event.level))
            // A stack trace inside `message` is what Cloud Error Reporting groups on.
            put("message", if (stack == null) event.formattedMessage else "${event.formattedMessage}\n$stack")
            put("time", Instant.ofEpochMilli(event.timeStamp).toString())
            put("logger", event.loggerName)
            put("thread", event.threadName)
        }.toString() + "\n"
    }

    companion object {
        fun severity(level: Level): String = when (level.toInt()) {
            Level.ERROR_INT -> "ERROR"
            Level.WARN_INT -> "WARNING"
            Level.INFO_INT -> "INFO"
            else -> "DEBUG"
        }
    }
}

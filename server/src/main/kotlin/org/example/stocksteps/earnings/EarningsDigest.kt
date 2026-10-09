package org.example.stocksteps.earnings

import kotlinx.coroutines.CancellationException
import org.example.stocksteps.model.EarningsDateStatus
import org.example.stocksteps.service.CompanyFinancialCache
import org.example.stocksteps.userdata.EarningsReminderDocument
import org.example.stocksteps.userdata.UserDataStore
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * StockSteps+ personalized earnings digest (Phase 5): "what happened with the companies I'm
 * interested in?" Built deterministically from the user's own watchlists (identity from the verified
 * token; never a client-supplied list), their preferred markets (Daily Brief preferences) and learning
 * interests, using the same Phase 1–3 sources as every other earnings screen. An optional AI summary is
 * generated only on request. Cached per user, period, plan, watchlist and preferences, so content is
 * never shared across accounts. Watching a company is never treated as owning it.
 */
class EarningsDigestService(
    private val store: UserDataStore,
    private val earnings: EarningsService,
    private val premium: EarningsPremiumService,
    private val clock: Clock,
    private val sampleData: Boolean,
    private val cache: CompanyFinancialCache = CompanyFinancialCache(capacity = 1_024)
) {
    private val aiSummaries = ConcurrentHashMap<String, DigestAiSummary>()

    companion object {
        const val CACHE_TTL = 15 * 60_000L
        private val DAY = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
        private val CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        const val TITLE = "Your weekly earnings digest is ready"
    }

    private fun bad(code: String, message: String): Nothing = throw EarningsRequestException(400, code, message)
    private suspend fun doc(uid: String) = store.updateEarningsReminders(uid) { it to it }
    private suspend fun watched(uid: String): Set<String> = store.updateWatchlists(uid) { it to it }.watchlists
        .flatMap { list -> list.entries.map { it.instrument.symbol.trim().uppercase() } }.toSet()
    private suspend fun markets(uid: String): List<String> = runCatching { store.updateBriefPreferences(uid) { it to it }?.preferences?.markets }.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf("US", "CA")

    private fun market(e: EarningsEvent) = if (e.exchange.equals("TSX", true) || e.exchange.equals("TSXV", true) || e.symbol.endsWith(".TO", true) || e.symbol.endsWith(".V", true)) "CA" else "US"
    private fun reported(e: EarningsEvent) = e.actual?.let { it.eps != null || it.revenue != null } == true

    private fun zone(doc: EarningsReminderDocument?) = ReminderPlanner.zone(doc?.preferences ?: EarningsReminderPreferences())

    /** Deterministic digest for the 7 days ending [end] (user-local date) plus the next 7 days. */
    suspend fun build(uid: String, prefs: EarningsDigestPreferences, end: LocalDate, scenario: String? = null): PersonalizedEarningsDigest {
        val s = scenario.takeIf { sampleData }
        val watched = if (s == "empty-watchlist") emptySet() else watched(uid)
        val markets = markets(uid)
        val key = listOf(uid, end, watched.sorted().joinToString(","), prefs, markets, s).joinToString("|")
        return cache.getOrLoad("digest:$key", CACHE_TTL) { compute(uid, prefs, end, watched, markets, s) }
    }

    private suspend fun compute(uid: String, prefs: EarningsDigestPreferences, end: LocalDate, watched: Set<String>, markets: List<String>, scenario: String?): PersonalizedEarningsDigest {
        val start = end.minusDays(DigestPolicy.WINDOW_DAYS - 1L)
        val now = clock.instant().toString()
        fun digest(reported: List<DigestReportedItem>, upcoming: List<DigestUpcomingItem>, lessons: List<DigestLesson>, notes: List<String>, emptyReason: String?): PersonalizedEarningsDigest {
            val version = EarningsGrounding.version(listOf(watched.sorted().joinToString(",")) + reported.map { "${it.reportId}|${it.headline}|${it.reactionText}" } +
                upcoming.map { "${it.eventId}|${it.date}|${it.dateStatus}" } + lessons.map { it.key })
            return PersonalizedEarningsDigest("dg-" + EarningsGrounding.version(listOf(uid, start.toString(), end.toString(), version)), start.toString(), end.toString(), watched.size,
                reported, upcoming, lessons, notes, reported.isEmpty() && upcoming.isEmpty(), emptyReason, now, version, sampleData = sampleData)
        }
        if (watched.isEmpty()) return digest(emptyList(), emptyList(), EarningsDigestRules.lessons(emptyList(), emptyList(), prefs.interests, false), emptyList(),
            "Add companies to a watchlist to get a personalized earnings digest.")
        val notes = ArrayList<String>()
        val loaded = HashMap<String, List<EarningsEvent>>()
        var failed = 0
        for (symbol in watched.sorted()) try { loaded[symbol] = earnings.reminderEvents(symbol) } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            failed++
        }
        if (failed > 0) notes += "Earnings data for $failed compan${if (failed == 1) "y" else "ies"} couldn't be loaded, so ${if (failed == 1) "it's" else "they're"} left out."
        val all = loaded.values.flatten()
        val shown = all.filter { market(it) in markets }
        val hidden = all.filter { market(it) !in markets }.map { it.symbol }.distinct().size
        if (hidden > 0) notes += "$hidden compan${if (hidden == 1) "y is" else "ies are"} outside the markets you chose in Daily Brief settings."
        val events = if (scenario == "no-events") emptyList() else shown
        val recent = events.filter { reported(it) && it.date >= start.toString() && it.date <= end.toString() }
            .sortedWith(compareByDescending<EarningsEvent> { it.date }.thenBy { it.symbol }).take(DigestPolicy.MAX_ITEMS)
        var fellAfterBeat = false
        val reportedItems = recent.mapNotNull { e ->
            val results = try { earnings.resultsFor(e.id) } catch (cause: Exception) {
                if (cause is CancellationException) throw cause
                notes += "${e.name}'s results couldn't be loaded."; return@mapNotNull null
            }
            val i = results.insights
            val rx = try { earnings.priceReaction(e.id, null).reaction } catch (cause: Exception) { if (cause is CancellationException) throw cause; null }
            val move = rx?.takeIf { it.hasChange }?.percentChange?.let(EarningsMath::decimal)
            if (i.eps.classification == Classification.BEAT && move != null && move < org.example.stocksteps.portfolio.Decimal.ZERO) fellAfterBeat = true
            DigestReportedItem(e.id, e.symbol, results.report.companyName, e.date, EarningsDigestRules.headline(i.eps.classification, i.revenue.classification),
                i.eps.classification, i.revenue.classification,
                rx?.takeIf { it.hasChange }?.let { "Price change over ${it.window.label.lowercase()} after the report: ${EarningsResultsFormat.percent(it.percentChange)}" }
                    ?: "Price reaction: ${rx?.statusMessage ?: "not available"}",
                i.comparisonWarnings.firstOrNull())
        }
        val upcomingItems = if (!prefs.includeUpcoming) emptyList() else events.filter { e ->
            !reported(e) && e.sourceStatus != EarningsEventStatus.CANCELED && e.sourceStatus != EarningsEventStatus.POSTPONED && e.dateStatus != EarningsDateStatus.UNKNOWN &&
                e.date >= end.toString() && e.date <= end.plusDays(DigestPolicy.WINDOW_DAYS.toLong()).toString()
        }.sortedWith(compareBy<EarningsEvent> { it.date }.thenBy { it.symbol }).take(DigestPolicy.MAX_ITEMS).map { e ->
            DigestUpcomingItem(e.id, e.symbol, e.name, e.date, e.session, e.dateStatus,
                EarningsFormatter.date(e.date) + " · " + EarningsFormatter.session(e.session) + if (e.dateStatus == EarningsDateStatus.CONFIRMED) "" else " · date not confirmed")
        }
        if (sampleData) notes += "Sample earnings data for development, not real results."
        val lessons = EarningsDigestRules.lessons(reportedItems, upcomingItems, prefs.interests, fellAfterBeat)
        return digest(reportedItems, upcomingItems, lessons, notes,
            if (reportedItems.isEmpty() && upcomingItems.isEmpty()) "No company on your watchlists reported earnings in the last 7 days or is expected to report in the next 7 days." else null)
    }

    private suspend fun record(uid: String, d: PersonalizedEarningsDigest, notified: Boolean) {
        if (d.empty) return
        store.updateEarningsReminders(uid) { current ->
            val base = current ?: EarningsReminderDocument(uid)
            val existing = base.digestHistory.firstOrNull { it.periodEnd == d.periodEnd }
            val entry = DigestHistoryEntry(d.digestId, d.periodStart, d.periodEnd, d.generatedAt, d.recentlyReported.size, d.upcomingEvents.size, notified || existing?.notified == true)
            if (existing == entry) return@updateEarningsReminders current to Unit
            base.copy(digestHistory = (listOf(entry) + base.digestHistory.filterNot { it.periodEnd == d.periodEnd }).take(DigestPolicy.MAX_HISTORY)) to Unit
        }
    }

    // ---------- API ----------

    suspend fun latest(uid: String, scenario: String?): PersonalizedEarningsDigest {
        val plan = premium.requirePlus(uid, scenario, "The personalized earnings digest")
        val doc = doc(uid)
        val d = build(uid, doc?.digest ?: EarningsDigestPreferences(), clock.instant().atZone(zone(doc)).toLocalDate(), scenario)
        record(uid, d, notified = false)
        return d.copy(aiSummary = aiSummaries[d.digestId], usage = premium.quota.usage(uid, plan.plus, plan.status))
    }

    /** Optional AI educational summary of the current digest (quota: digest summaries; cached per digest version). */
    suspend fun explain(uid: String, scenario: String?): PersonalizedEarningsDigest {
        val plan = premium.requirePlus(uid, scenario, "AI digest summaries")
        val d = latest(uid, scenario)
        if (d.empty) bad("DIGEST_EMPTY", "There's nothing in this week's digest to summarize.")
        val summary = aiSummaries[d.digestId] ?: run {
            val lines = d.recentlyReported.map { it.reportId to "${it.companyName} (${it.symbol}) reported ${it.headline}. ${it.reactionText ?: ""}".trim() } +
                d.upcomingEvents.map { it.eventId to "${it.companyName} (${it.symbol}) is expected to report on ${it.whenText}." }
            val lessons = d.educationalHighlights.map { GroundingLesson("L-${it.key}", it.title, it.body) }
            premium.digestSummary(uid, plan, DigestAiContext(lines, lessons), scenario).also {
                if (aiSummaries.size > 5_000) aiSummaries.keys.take(1_000).forEach(aiSummaries::remove)
                aiSummaries[d.digestId] = it
            }
        }
        return d.copy(aiSummary = summary, usage = premium.quota.usage(uid, plan.plus, plan.status))
    }

    suspend fun history(uid: String): EarningsDigestHistory {
        premium.requirePlus(uid, null, "Digest history")
        return EarningsDigestHistory(doc(uid)?.digestHistory.orEmpty(), clock.instant().toString())
    }

    /** The next planned delivery for [doc] (week key and instant), or null when the digest is off. */
    fun plannedDelivery(doc: EarningsReminderDocument, now: Instant): Pair<String, Instant>? {
        if (doc.digest.cadence != DigestCadence.WEEKLY || !doc.preferences.enabled) return null
        val prefs = doc.preferences
        val zone = ReminderPlanner.zone(prefs)
        val day = runCatching { DayOfWeek.valueOf(doc.digest.dayOfWeek) }.getOrDefault(DayOfWeek.SATURDAY)
        var date = now.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(day))
        var at = ReminderPlanner.localInstant(date, prefs)
        // Never sent late or twice: a passed delivery time moves to next week.
        if (at.plus(ReminderPlanner.LATE_GRACE) < now) { date = date.plusWeeks(1); at = ReminderPlanner.localInstant(date, prefs) }
        val week = "${date.get(IsoFields.WEEK_BASED_YEAR)}-W${date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString().padStart(2, '0')}"
        return week to at
    }

    /**
     * At delivery time: the verified counts for the push (no company names in the payload) or null when
     * nothing should be sent (plan ended, digest turned off, or an empty digest).
     */
    suspend fun notification(uid: String, scheduledFor: Instant): Pair<String, PersonalizedEarningsDigest>? {
        val doc = doc(uid) ?: return null
        if (doc.digest.cadence != DigestCadence.WEEKLY) return null
        val plan = premium.plan(uid)
        if (!plan.plus) return null
        val d = build(uid, doc.digest, scheduledFor.atZone(zone(doc)).toLocalDate())
        val body = EarningsDigestRules.notificationBody(d.recentlyReported.size, d.upcomingEvents.size) ?: return null
        record(uid, d, notified = true)
        return body to d
    }

    suspend fun settings(uid: String): EarningsDigestSettings {
        val plan = premium.plan(uid)
        val doc = doc(uid)
        val prefs = doc?.digest ?: EarningsDigestPreferences()
        val reminders = doc?.preferences ?: EarningsReminderPreferences()
        val planned = doc?.let { plannedDelivery(it, clock.instant()) }
        val active = prefs.cadence == DigestCadence.WEEKLY && plan.plus && reminders.enabled
        val message = when {
            prefs.cadence == DigestCadence.NONE -> "Off. Turn on the weekly digest to get a summary of your watchlist's earnings."
            !plan.plus -> "Paused: the weekly digest is part of StockSteps+. Your setting is kept, and basic earnings reminders keep working."
            !reminders.enabled -> "Paused: earnings notifications are turned off in Earnings Reminders."
            else -> "Weekly on ${DigestPolicy.dayLabel(prefs.dayOfWeek)}s around ${reminders.deliveryTime} (${reminders.timeZone}). Nothing is sent in a week with nothing to report."
        }
        val next = planned?.takeIf { active }?.second?.atZone(ReminderPlanner.zone(reminders))?.let { "${DAY.format(it)} around ${CLOCK.format(it)} (${reminders.timeZone})" }
        return EarningsDigestSettings(prefs, plan.plus, plan.status, active, message, reminders.deliveryTime, reminders.timeZone,
            if (reminders.quietStart != null && reminders.quietEnd != null) "${reminders.quietStart}–${reminders.quietEnd}" else null,
            reminders.enabled, next, premium.quota.usage(uid, plan.plus, plan.status), sampleData)
    }

    suspend fun setPreferences(uid: String, p: EarningsDigestPreferences): EarningsDigestSettings {
        if (p.dayOfWeek !in DigestPolicy.DAYS) bad("INVALID_DAY", "Choose a day from Monday to Sunday.")
        val keys = DigestPolicy.INTERESTS.map { it.first }.toSet()
        if (p.interests.size > keys.size || p.interests.any { it !in keys }) bad("INVALID_INTERESTS", "Choose from the listed learning interests.")
        // Turning the weekly digest on needs StockSteps+; turning it off (or editing other fields) never does.
        if (p.cadence == DigestCadence.WEEKLY) premium.requirePlus(uid, null, "The weekly earnings digest")
        store.updateEarningsReminders(uid) { current -> (current ?: EarningsReminderDocument(uid)).copy(digest = p.copy(interests = p.interests.distinct()), updatedAt = clock.millis()) to Unit }
        return settings(uid)
    }
}

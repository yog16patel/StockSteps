package org.example.stocksteps.earnings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.example.stocksteps.model.ApiError
import org.example.stocksteps.network.StockStepsApiException
import kotlin.test.*

/** Earnings Intelligence Lite, Phase 4: the shared reminder presenter (server-confirmed saves, account safety). */
@OptIn(ExperimentalCoroutinesApi::class)
class EarningsRemindersPresenterTest {
    private class Remote : EarningsRemindersRemote {
        val reminders = mutableListOf<EarningsReminder>()
        var prefs = EarningsReminderPreferences(timeZone = "America/Toronto")
        var fail: Exception? = null
        val calls = mutableListOf<String>()
        var owner = "alice"
        private fun response() = EarningsRemindersResponse(reminders.toList(), prefs, asOf = "now")
        override suspend fun reminders(): EarningsRemindersResponse { calls += "get:$owner"; return response() }
        override suspend fun create(request: CreateEarningsReminder): EarningsRemindersResponse {
            calls += "create"; fail?.let { throw it }
            reminders += EarningsReminder("r${reminders.size + 1}", request.eventId.substringBefore(':'), request.eventId, ReminderSource.MANUAL, request.offsetDays ?: 1,
                createdAt = 0, updatedAt = 0, scheduleStatus = ReminderScheduleStatus.ACTIVE)
            return response()
        }
        override suspend fun update(reminderId: String, request: UpdateEarningsReminder): EarningsRemindersResponse {
            calls += "update"; fail?.let { throw it }
            val updated = reminders.map { if (it.reminderId == reminderId) it.copy(offsetDays = request.offsetDays ?: it.offsetDays) else it }
            reminders.clear(); reminders.addAll(updated); return response()
        }
        override suspend fun delete(reminderId: String): EarningsRemindersResponse { calls += "delete"; reminders.removeAll { it.reminderId == reminderId }; return response() }
        override suspend fun preferences(preferences: EarningsReminderPreferences): EarningsRemindersResponse { calls += "prefs:${preferences.timeZone}"; prefs = preferences; return response() }
    }
    private fun TestScope.settle() { advanceTimeBy(1_000); runCurrent() }

    @Test fun savesAreConfirmedByTheServerAndFailuresAreVisible() = runTest {
        val remote = Remote(); val session = MutableStateFlow<String?>("alice")
        val p = EarningsRemindersPresenter(remote, backgroundScope, session, { "America/Toronto" }, { true }).also { it.start() }
        settle()
        assertEquals(ReminderControl.OFF, p.state.value.control("AAPL:2026-Q4"))
        p.remind("AAPL:2026-Q4", 3)
        runCurrent()
        assertEquals(ReminderControl.ON, p.state.value.control("AAPL:2026-Q4"))
        assertEquals(3, p.state.value.reminderFor("AAPL:2026-Q4")!!.offsetDays)
        p.remind("AAPL:2026-Q4", 7); settle()
        assertEquals(listOf("create", "update"), remote.calls.filter { it == "create" || it == "update" })     // editing doesn't create a second one
        remote.fail = StockStepsApiException(500, ApiError("X", "Saving failed."))
        p.remind("KO:2026-Q3", 1); settle()
        assertEquals(ReminderControl.FAILED, p.state.value.control("KO:2026-Q3")); assertEquals("Saving failed.", p.state.value.message)
        assertNull(p.state.value.reminderFor("KO:2026-Q3"))                                                      // never shown as saved
        remote.fail = null; p.remind("KO:2026-Q3", 1); settle()
        assertEquals(ReminderControl.ON, p.state.value.control("KO:2026-Q3"))
        p.turnOff("AAPL:2026-Q4"); settle()
        assertEquals(ReminderControl.OFF, p.state.value.control("AAPL:2026-Q4"))
    }

    @Test fun permissionAndMasterSwitchAreExplainedNotHidden() = runTest {
        val remote = Remote()
        val p = EarningsRemindersPresenter(remote, backgroundScope, MutableStateFlow("alice"), { "America/Toronto" }, { false }).also { it.start() }
        settle()
        assertTrue(p.state.value.deliveryWarning!!.contains("Notifications are off"))
        p.setPermission(true); assertNull(p.state.value.deliveryWarning)
        p.updatePreferences { it.copy(enabled = false) }; settle()
        assertTrue(p.state.value.deliveryWarning!!.contains("turned off in Earnings Reminders"))
    }

    @Test fun signOutAndAccountSwitchNeverShowAnotherUsersReminders() = runTest {
        val remote = Remote(); val session = MutableStateFlow<String?>("alice")
        val p = EarningsRemindersPresenter(remote, backgroundScope, session, { "America/Toronto" }).also { it.start() }
        settle(); p.remind("AAPL:2026-Q4", 1); settle()
        session.value = null; settle()
        assertFalse(p.state.value.signedIn); assertNull(p.state.value.response)
        remote.reminders.clear(); remote.owner = "bob"
        session.value = "bob"; settle()
        assertEquals(ReminderControl.OFF, p.state.value.control("AAPL:2026-Q4"))
        assertTrue(remote.calls.contains("get:bob"))
        p.remind("AAPL:2026-Q4", 1)
        session.value = null; settle()
        assertNull(p.state.value.response)                                                                       // a late response for bob isn't applied
    }

    @Test fun deviceTimeZoneChangesUpdateTheSchedule() = runTest {
        val remote = Remote()
        val p = EarningsRemindersPresenter(remote, backgroundScope, MutableStateFlow("alice"), { "Europe/London" }).also { it.start() }
        settle()
        assertEquals("Europe/London", p.state.value.preferences.timeZone)
        assertTrue(remote.calls.contains("prefs:Europe/London"))
        val again = remote.calls.size
        p.refresh(); settle()
        assertEquals(again + 1, remote.calls.size)                                                              // same zone → no extra write
    }
}

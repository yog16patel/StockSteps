package org.example.stocksteps.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.local.WatchlistDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Always uses local emulators and a named Firebase app, never production credentials. */
@RunWith(AndroidJUnit4::class)
class FirebaseAccountIntegrationTest {
    @Test fun guestMergeCrossDeviceOfflineRemovalSessionAndAccountIsolation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.nanoTime().toString()
        val options = FirebaseOptions.Builder().setApplicationId("1:123456:android:stocksteps")
            .setApiKey("fake-emulator-api-key").setProjectId("stocksteps").build()
        val appA = FirebaseApp.initializeApp(context, options, "test-a-$suffix")
        val appB = FirebaseApp.initializeApp(context, options, "test-b-$suffix")
        val authA = FirebaseAuth.getInstance(appA).also { it.useEmulator("127.0.0.1", 9099) }
        val authB = FirebaseAuth.getInstance(appB).also { it.useEmulator("127.0.0.1", 9099) }
        val cloudA = FirebaseFirestore.getInstance(appA).also { it.useEmulator("127.0.0.1", 8085) }
        val cloudB = FirebaseFirestore.getInstance(appB).also { it.useEmulator("127.0.0.1", 8085) }
        val databaseA = "account-test-a-$suffix.db"
        val databaseB = "account-test-b-$suffix.db"
        val accountsA = AccountDependencies(AndroidAuthGateway(authA), AndroidWatchlistGateway(cloudA), AndroidSqliteDriver(WatchlistDatabase.Schema, context, databaseA))
        val accountsB = AccountDependencies(AndroidAuthGateway(authB), AndroidWatchlistGateway(cloudB), AndroidSqliteDriver(WatchlistDatabase.Schema, context, databaseB))
        try {
            withTimeout(20_000) {
                accountsA.auth.session.first { !it.initializing }
                accountsA.watchlist.add("aapl")
                accountsA.watchlist.add("NVDA")
                assertEquals(2, accountsA.watchlist.items.first { it.size == 2 }.size)
                val email = "stocksteps-$suffix@example.com"
                val password = "EmulatorOnly123!"
                accountsA.auth.signUp(email, password)
                val uid = accountsA.auth.session.first { it.user != null }.user!!.id
                val aaplA = cloudA.collection("users").document(uid).collection("watchlist").document("AAPL")
                while (!Tasks.await(aaplA.get(Source.SERVER)).exists()) delay(100)
                accountsB.auth.signIn(email, password)
                accountsB.auth.session.first { it.user?.id == uid }
                assertEquals(setOf("AAPL", "NVDA"), accountsB.watchlist.items.first { it.size == 2 }.map { it.symbol }.toSet())
                // Auth SDK owns restored identity; a fresh repository observes the existing session.
                assertEquals(uid, FirebaseAuth.getInstance(appB).currentUser?.uid)
                Tasks.await(cloudA.disableNetwork())
                accountsA.watchlist.remove("AAPL")
                assertFalse(accountsA.watchlist.items.first { rows -> rows.none { it.symbol == "AAPL" } }.any { it.symbol == "AAPL" })
                assertTrue(Tasks.await(cloudB.collection("users").document(uid).collection("watchlist").document("AAPL").get(Source.SERVER)).exists())
                Tasks.await(cloudA.enableNetwork())
                accountsA.watchlist.retrySync()
                while (Tasks.await(cloudB.collection("users").document(uid).collection("watchlist").document("AAPL").get(Source.SERVER)).exists()) delay(100)
                accountsB.watchlist.items.first { rows -> rows.none { it.symbol == "AAPL" } }
                accountsA.auth.signOut()
                accountsA.auth.session.first { it.user == null }
                assertTrue(accountsA.watchlist.items.first().isEmpty())
                accountsA.auth.signUp("other-$suffix@example.com", password)
                val otherUid = accountsA.auth.session.first { it.user != null }.user!!.id
                assertNotEquals(uid, otherUid)
                assertTrue(accountsA.watchlist.items.first().isEmpty())
                try {
                    Tasks.await(aaplA.get(Source.SERVER))
                    fail("Cross-account reads must be denied")
                } catch (_: java.util.concurrent.ExecutionException) { /* expected rule rejection */ }
            }
        } finally {
            accountsA.close(); accountsB.close()
            Tasks.await(cloudA.terminate()); Tasks.await(cloudB.terminate())
            appA.delete(); appB.delete()
            context.deleteDatabase(databaseA); context.deleteDatabase(databaseB)
        }
    }
}

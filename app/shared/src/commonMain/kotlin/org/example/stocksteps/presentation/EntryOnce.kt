package org.example.stocksteps.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope

/**
 * 5D.5.6D lifecycle rule (Android counterpart of the iOS `taskOnce`, 5D.5.6C3): a tab switch disposes the tab's screens and restores them later,
 * so a `LaunchedEffect` that fetches would run again on every return. The flags live in a ViewModel of the back-stack entry: they survive tab
 * switches (the entry's ViewModelStore is saved with the tab) and are cleared with the entry and on process death, when loading again is
 * needed. Pull-to-refresh and Retry stay explicit.
 */
internal class EntryOnce : ViewModel() {
    private val done = mutableSetOf<Any?>()
    /** True the first time [key] is seen by this back-stack entry. */
    fun first(key: Any?): Boolean = done.add(key)
}

@Composable
internal fun rememberEntryOnce(): EntryOnce = viewModel(key = "stocksteps-entry-once") { EntryOnce() }

/** Runs [block] once per back-stack entry and [key] (again only for a new key), never because the screen came back on screen. */
@Composable
internal fun LaunchedOncePerEntry(key: Any?, block: suspend CoroutineScope.() -> Unit) {
    val once = rememberEntryOnce()
    LaunchedEffect(key) { if (once.first(key)) block() }
}

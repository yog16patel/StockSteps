package org.example.stocksteps.presentation.learn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.stocksteps.WindowHinge
import org.example.stocksteps.di.AccountDependencies
import org.example.stocksteps.learning.ephemeralLearningProgress

/** Reads saved research progress (account or guest) and wires the hub's navigation. */
@Composable
internal fun LearnScene(
    hinge: WindowHinge?,
    accounts: AccountDependencies?,
    onResearch: (symbol: String, name: String) -> Unit,
    onSearch: () -> Unit,
    onPractice: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val repository = accounts?.learning ?: remember { ephemeralLearningProgress(scope) }
    val progress by repository.state.collectAsStateWithLifecycle()
    LearnScreen(progress, hinge, onResearch = onResearch, onSearch = onSearch, onPractice = onPractice)
}

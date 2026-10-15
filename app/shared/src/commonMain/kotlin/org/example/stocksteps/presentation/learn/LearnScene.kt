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
    onPractice: () -> Unit = {},
    onTopics: (topic: String?, search: Boolean) -> Unit = { _, _ -> },
    onTerm: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val repository = accounts?.learning ?: remember { ephemeralLearningProgress(scope) }
    val progress by repository.state.collectAsStateWithLifecycle()
    LearnScreen(progress, hinge, onResearch = onResearch, onSearch = onSearch, onPractice = onPractice, onTopics = onTopics, onTerm = onTerm)
}

/** Topics or one topic's terms (Phase 5D.5.2); the route only names the topic. */
@Composable
internal fun LearnTopicsScene(route: LearnTopicsRoute, hinge: WindowHinge?, onTopic: (String) -> Unit, onTerm: (String) -> Unit) =
    LearnTopicsScreen(route.topic, route.search, hinge, onTopic, onTerm)

/** A term's reading page (Phase 5D.5.2). */
@Composable
internal fun LearnTermScene(route: LearnTermRoute, hinge: WindowHinge?, onTerm: (String) -> Unit, onTopics: () -> Unit, onSearch: () -> Unit) =
    LearnTermScreen(route.id, hinge, onTerm, onTopics, onSearch)

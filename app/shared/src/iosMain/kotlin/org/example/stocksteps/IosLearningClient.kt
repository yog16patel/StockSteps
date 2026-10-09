package org.example.stocksteps

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.example.stocksteps.data.account.AccountSubscription
import org.example.stocksteps.di.StockStepsDependencies
import org.example.stocksteps.learning.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Guided Research and the Learn hub for SwiftUI: the same shared presenter, progress repository
 * (device + account sync), education catalogue and backend contract as Android.
 */
class IosLearningClient(baseUrl: () -> String, account: IosAccountClient?) {
    private val dependencies = StockStepsDependencies(baseUrl)
    private val accounts = account?.dependenciesForEarnings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val data = dependencies.guidedResearch()
    val progress: LearningProgressRepository = accounts?.learning ?: ephemeralLearningProgress(scope)

    fun observeProgress(onChange: (LearningProgressRepository.State) -> Unit): AccountSubscription {
        val job = scope.launch { progress.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    @OptIn(ExperimentalTime::class)
    fun research(symbol: String, name: String?, step: Int): GuidedResearchPresenter = GuidedResearchPresenter(
        data = data,
        progress = progress,
        plus = accounts?.entitlements?.state?.map { it?.plus } ?: flowOf(null),
        asker = accounts?.researchAsk,
        scope = scope,
        today = { Clock.System.now().toString().take(10) }
    ).also { it.load(symbol, name, step.takeIf { s -> s > 0 }) }

    fun observeResearch(presenter: GuidedResearchPresenter, onChange: (GuidedResearchState) -> Unit): AccountSubscription {
        val job = scope.launch { presenter.state.collect(onChange) }
        return object : AccountSubscription { override fun cancel() { job.cancel() } }
    }

    // Swift-friendly helpers.
    val stepCount: Int get() = ResearchStep.COUNT
    fun stepAccessibilityLabel(number: Int, question: String, status: String) = ResearchStep.accessibilityLabel(number, question, status)
    val terms: List<EducationEntry> get() = BeginnerEducation.entries
    fun term(id: String): EducationEntry? = BeginnerEducation.entry(id)
    val quickPickSymbols: List<String> get() = listOf("AAPL", "KO", "JPM", "MSFT")
    val quickPickNames: List<String> get() = listOf("Apple", "Coca-Cola", "JPMorgan Chase", "Microsoft")
    fun journey(state: LearningProgressRepository.State, symbol: String): ResearchProgress? = state.journey(symbol)
    fun quizRecord(state: GuidedResearchState, quiz: Quiz): QuizRecord? = state.progress?.quiz(quiz)
    fun answer(state: GuidedResearchState, quiz: Quiz): QuizResult? = state.answers[quiz.id]
    fun completed(state: GuidedResearchState, step: Int): Boolean = state.completed(step)
    fun shortName(name: String): String = GuidedResearchEngine.shortName(name)
    fun isFund(snapshot: ResearchSnapshot): Boolean = snapshot.kind == CompanyKind.FUND
    fun isUnsupported(snapshot: ResearchSnapshot): Boolean = snapshot.kind == CompanyKind.UNSUPPORTED

    fun close() {
        scope.cancel()
        dependencies.close()
    }
}

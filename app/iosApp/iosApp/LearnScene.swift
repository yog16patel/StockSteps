import SwiftUI

/// Reads saved research progress and wires the hub's navigation; LearnScreen only renders.
struct LearnScene: View {
    let learning: LearningModel
    let onResearch: (ResearchTarget) -> Void
    let onSearch: () -> Void
    var onPractice: () -> Void = {}
    var body: some View { LearnScreen(learning: learning, onResearch: onResearch, onSearch: onSearch, onPractice: onPractice) }
}

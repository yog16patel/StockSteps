import SwiftUI

/// Reads saved research progress and wires the hub's navigation; LearnScreen only renders.
struct LearnScene: View {
    let learning: LearningModel
    let onResearch: (ResearchTarget) -> Void
    let onSearch: () -> Void
    var onPractice: () -> Void = {}
    /// Phase 5D.5.2: topics and term pages.
    var onLearnTarget: (LearnTarget) -> Void = { _ in }
    /// 5D.5.6B landing bar: the all-alerts history and Settings.
    var onNotifications: () -> Void = {}
    var onSettings: () -> Void = {}
    var body: some View {
        LearnScreen(learning: learning, onResearch: onResearch, onSearch: onSearch, onPractice: onPractice, onLearnTarget: onLearnTarget,
                    onNotifications: onNotifications, onSettings: onSettings)
    }
}

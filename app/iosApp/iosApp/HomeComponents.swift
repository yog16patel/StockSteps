import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography
private let dims = StockStepsTheme.dimensions

/// Inviting learning banner: soft gradient, decorative illustration, indigo title and a round
/// arrow cue. The whole banner is one button; Learn owns the content.
struct HomeLearnBanner: View {
    @Environment(\.colorScheme) private var scheme
    let action: () -> Void
    var body: some View {
        let colors = StockStepsTheme.colors(scheme)
        Button(action: action) {
            HStack(spacing: CGFloat(space.md)) {
                Image("LearnBasics")
                    .resizable()
                    .frame(width: CGFloat(dims.learnIllustration), height: CGFloat(dims.learnIllustration))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
                    Text("Learn the Basics")
                        .font(StockStepsTheme.font(type.cardTitle, relativeTo: .headline).bold())
                        .foregroundStyle(colors.learnAccent)
                    Text("How the stock market works, step by step.")
                        .font(StockStepsTheme.font(type.caption, relativeTo: .caption1))
                        .foregroundStyle(colors.textBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "arrow.right")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(colors.onLearnAccent)
                    .frame(width: CGFloat(dims.learnAction), height: CGFloat(dims.learnAction))
                    .background(colors.learnAccent, in: Circle())
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, CGFloat(space.md))
            .padding(.vertical, CGFloat(space.sm))
            .background(
                LinearGradient(colors: [colors.learnContainerStart, colors.learnContainerEnd], startPoint: .leading, endPoint: .trailing),
                in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge))
            )
            .contentShape(RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.cardLarge)))
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Start learning")
    }
}

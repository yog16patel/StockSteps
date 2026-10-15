import Shared
import SwiftUI

private let space = StockStepsTheme.spacing
private let type = StockStepsTheme.typography

/// A compact market index tile (5D.5.1b, as Compose `StockIndexTile`): name, the percentage change as the loud value (arrow + sign via
/// `StockPriceChange`, never colour alone), the level and the point change, an optional state line; one spoken summary. Use only where
/// `StockIndexTilesLayout.tiles` says every piece fits; otherwise show `StockIndexRow`. Nothing is truncated.
struct StockIndexTile: View {
    let name: String
    let percent: String?
    let value: String
    let points: String?
    let direction: PriceDirection
    let accessibilityText: String
    var stateLabel: String? = nil
    var caution = false
    var changeUnavailable = "Change unavailable"
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dynamicTypeSize) private var typeSize
    var body: some View {
        let _ = typeSize
        let colors = StockStepsTheme.colors(scheme)
        VStack(alignment: .leading, spacing: CGFloat(space.xxs)) {
            Text(name).font(StockStepsTheme.font(type.label, relativeTo: .footnote)).foregroundStyle(colors.textSupporting)
                .fixedSize(horizontal: false, vertical: true)
            if let percent {
                StockPriceChange(percentage: percent, direction: direction, style: type.numberMedium, lineLimit: nil)
            } else {
                Text(changeUnavailable).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(colors.textMeta)
            }
            Text(value).font(StockStepsTheme.font(type.numberLabelStrong, relativeTo: .footnote)).foregroundStyle(colors.textValue).monospacedDigit()
            if let points {
                Text(points).font(StockStepsTheme.font(type.numberLabel, relativeTo: .footnote)).foregroundStyle(colors.textSupporting).monospacedDigit()
            }
            if let stateLabel {
                Text(stateLabel).font(StockStepsTheme.font(type.caption, relativeTo: .footnote)).foregroundStyle(caution ? colors.cautionText : colors.textMeta)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, CGFloat(space.sm))
        .padding(.vertical, CGFloat(space.cardPadding))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(colors.surface, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.corners.card)))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityText)
    }
}

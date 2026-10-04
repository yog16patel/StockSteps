import Shared
import SwiftUI

struct NewsCard: View {
    let article: NewsArticle
    private static let providerDescriptionLines = 3
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.small)) {
            Text([article.source, article.publishedAt].compactMap { $0 }.joined(separator: " · "))
                .font(.caption).foregroundStyle(.secondary)
            Text(article.explanation?.simpleHeadline ?? article.title).font(.headline)
            if let explanation = article.explanation {
                Text("AI-simplified · \(explanation.sentiment.name.lowercased().capitalized)")
                    .font(.caption).foregroundStyle(.secondary)
                Text(explanation.summary)
                Text("Why it matters").font(.subheadline.bold())
                Text(explanation.whyItMatters)
            }
            if article.explanation == nil,
               let description = article.description_?.trimmingCharacters(in: .whitespacesAndNewlines),
               !description.isEmpty {
                Text(description)
                    .font(.body)
                    .lineLimit(Self.providerDescriptionLines)
            }
            if let url = URL(string: article.url), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
                Link("Read original", destination: url)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(CGFloat(StockStepsTheme.spacing.large))
        .background(.background, in: RoundedRectangle(cornerRadius: CGFloat(StockStepsTheme.spacing.large)))
    }
}

struct HomeNewsSection: View {
    let articles: [NewsArticle]
    let loading: Bool
    let error: String?
    let onRefresh: () -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: CGFloat(StockStepsTheme.spacing.medium)) {
            HStack {
                Text("Market news").font(.title3.bold())
                Spacer()
                Button("Refresh", action: onRefresh)
            }
            if loading { ProgressView() }
            if let error { Text(error).foregroundStyle(.secondary) }
            if !loading && error == nil && articles.isEmpty { Text("No news available.") }
            ForEach(Array(articles.prefix(20).enumerated()), id: \.offset) { _, article in
                NewsCard(article: article)
            }
        }
    }
}

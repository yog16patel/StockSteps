package org.example.stocksteps.presentation.news

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import org.example.stocksteps.model.NewsArticle
import org.example.stocksteps.theme.ThemeSpacing

@Composable
internal fun NewsSection(
    articles: List<NewsArticle>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ThemeSpacing.medium.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Market news", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onRetry) { Text("Refresh") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it) }
        if (!loading && error == null && articles.isEmpty()) Text("No news available.")
        articles.take(20).forEach { NewsCard(it) }
    }
}

@Composable
internal fun NewsCard(article: NewsArticle) {
    val uriHandler = LocalUriHandler.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(ThemeSpacing.large.dp),
            verticalArrangement = Arrangement.spacedBy(ThemeSpacing.small.dp)
        ) {
            Text(listOfNotNull(article.source, article.publishedAt).joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            Text(article.explanation?.simpleHeadline ?: article.title, style = MaterialTheme.typography.titleMedium)
            article.explanation?.let { explanation ->
                Text("AI-simplified · ${explanation.sentiment.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.labelSmall)
                Text(explanation.summary)
                Text("Why it matters", style = MaterialTheme.typography.titleSmall)
                Text(explanation.whyItMatters)
            }
            if (article.explanation == null) {
                article.description?.trim()?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = PROVIDER_DESCRIPTION_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val link = article.url.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (link != null) TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                Text("Read original")
            }
        }
    }
}

private const val PROVIDER_DESCRIPTION_LINES = 3

package org.example.stocksteps.presentation.screener

import androidx.compose.runtime.Composable
import org.example.stocksteps.screener.ResearchExport

/** The iOS app uses SwiftUI (ShareLink) for reports; this Compose target doesn't save files. */
@Composable
internal actual fun rememberPdfSaver(onDone: (String) -> Unit): (ResearchExport) -> Unit = { onDone("Saving reports isn't available on this platform.") }

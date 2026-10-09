package org.example.stocksteps.presentation.screener

import androidx.compose.runtime.Composable
import org.example.stocksteps.screener.ResearchExport

/** Desktop preview target: saving reports isn't supported. */
@Composable
internal actual fun rememberPdfSaver(onDone: (String) -> Unit): (ResearchExport) -> Unit = { onDone("Saving reports isn't available on this platform.") }

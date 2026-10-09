package org.example.stocksteps.presentation.screener

import androidx.compose.runtime.Composable
import org.example.stocksteps.screener.ResearchExport

/**
 * Saves a research report PDF where the user chooses (Android: the system "Save as" picker, so no
 * storage permission and no shared/public file). [onDone] gets a message for the user.
 */
@Composable
internal expect fun rememberPdfSaver(onDone: (String) -> Unit): (ResearchExport) -> Unit

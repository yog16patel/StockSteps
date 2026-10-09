package org.example.stocksteps.presentation.screener

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.example.stocksteps.screener.ResearchExport

@Composable
internal actual fun rememberPdfSaver(onDone: (String) -> Unit): (ResearchExport) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<ResearchExport?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val export = pending
        pending = null
        if (uri == null || export == null) { onDone("The report wasn't saved."); return@rememberLauncherForActivityResult }
        val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(export.bytes) } != null }.getOrDefault(false)
        onDone(if (ok) "Report saved." else "The report couldn't be saved. Try again.")
    }
    return { export -> pending = export; launcher.launch(export.fileName) }
}

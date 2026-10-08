package com.tgsorter.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.tgsorter.app.domain.export.ExportKind
import com.tgsorter.app.util.toast

/**
 * Запуск системного диалога «Сохранить как» для экспорта.
 * Возвращает функцию: вызов `save(kind)` открывает диалог с именем файла `kind.fileName`,
 * после выбора места вызывается [onPicked].
 */
@Composable
fun rememberExportSaver(onPicked: (ExportKind, Uri) -> Unit): (ExportKind) -> Unit {
    val context = LocalContext.current
    var pendingKind by rememberSaveable { mutableStateOf<String?>(null) }

    val onResult: (Uri?) -> Unit = { uri ->
        val kind = pendingKind?.let { name -> ExportKind.entries.firstOrNull { it.name == name } }
        pendingKind = null
        if (uri != null && kind != null) onPicked(kind, uri)
    }
    val txtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain"), onResult)
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), onResult)

    return { kind ->
        pendingKind = kind.name
        try {
            if (kind == ExportKind.CSV) csvLauncher.launch(kind.fileName) else txtLauncher.launch(kind.fileName)
        } catch (e: ActivityNotFoundException) {
            pendingKind = null
            context.toast("Не найдено системное окно сохранения файлов")
        }
    }
}

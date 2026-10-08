package com.tgsorter.app.data.io

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.tgsorter.app.data.repository.ChannelRepository
import com.tgsorter.app.domain.export.ExportFormatter
import com.tgsorter.app.domain.export.ExportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Готовый к отправке файл экспорта. */
data class SharedExport(val uri: Uri, val mimeType: String, val fileName: String, val count: Int)

/**
 * Экспорт списков в TXT / CSV.
 *  - [saveTo] — запись в файл, выбранный через системный диалог «Сохранить» (SAF);
 *  - [prepareShare] — временный файл в cache + content:// через FileProvider для меню «Поделиться».
 */
class ExportManager(
    private val context: Context,
    private val repository: ChannelRepository,
) {

    suspend fun buildText(listId: Long, kind: ExportKind): Pair<String, Int> {
        val channels = repository.getChannels(listId, kind.status)
        return ExportFormatter.format(kind, channels) to channels.size
    }

    /** Возвращает количество записанных каналов. */
    suspend fun saveTo(uri: Uri, listId: Long, kind: ExportKind): Int {
        val (text, count) = buildText(listId, kind)
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            // "wt" — перезаписать файл; некоторые провайдеры (облачные диски) поддерживают только "w"
            val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
                ?: resolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("Не удалось открыть файл для записи")
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
        return count
    }

    suspend fun prepareShare(listId: Long, kind: ExportKind): SharedExport {
        val (text, count) = buildText(listId, kind)
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            File(dir, kind.fileName).apply { writeText(text, Charsets.UTF_8) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return SharedExport(uri, kind.mimeType, kind.fileName, count)
    }
}

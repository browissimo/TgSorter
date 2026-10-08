package com.tgsorter.app.data.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Ошибка чтения файла с понятным пользователю текстом. */
class FileReadException(message: String) : Exception(message)

data class TextFile(val displayName: String?, val text: String)

/**
 * Читает выбранный пользователем текстовый файл через ContentResolver (Storage Access Framework).
 * Поддерживает UTF-8 (с BOM и без), UTF-16 с BOM и Windows-1251.
 */
class TextFileReader(private val context: Context) {

    fun read(uri: Uri): TextFile {
        val resolver = context.contentResolver
        val name = queryDisplayName(uri)

        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) {
                        throw FileReadException("Файл слишком большой (больше ${MAX_FILE_BYTES / 1024 / 1024} МБ)")
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: throw FileReadException("Не удалось открыть файл")
        } catch (e: FileReadException) {
            throw e
        } catch (e: SecurityException) {
            throw FileReadException("Нет доступа к файлу. Выберите его ещё раз")
        } catch (e: Exception) {
            throw FileReadException("Не удалось прочитать файл: ${e.message ?: e.javaClass.simpleName}")
        }

        if (bytes.isEmpty()) throw FileReadException("Файл пустой")

        val text = decode(bytes)
        if (text.isBlank()) throw FileReadException("Файл пустой")
        if (looksBinary(text)) {
            throw FileReadException("Файл повреждён или не является текстовым (.txt)")
        }
        return TextFile(name, text)
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    } catch (e: Exception) {
        null
    }

    private fun decode(bytes: ByteArray): String {
        // BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // Строгий UTF-8, при ошибке — Windows-1251 (частый случай для файлов из Windows)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    /** Нулевые байты и много управляющих символов — признак бинарного/повреждённого файла. */
    private fun looksBinary(text: String): Boolean {
        val sample = text.take(8192)
        if (sample.isEmpty()) return false
        val control = sample.count { it == '\u0000' || (it < ' ' && it != '\n' && it != '\r' && it != '\t') }
        return control > 0 && control * 20 > sample.length || sample.contains('\u0000')
    }

    companion object {
        const val MAX_FILE_BYTES = 20L * 1024 * 1024
    }
}

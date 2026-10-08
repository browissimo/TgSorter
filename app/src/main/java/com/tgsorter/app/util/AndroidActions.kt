package com.tgsorter.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.tgsorter.app.data.io.SharedExport
import com.tgsorter.app.telegram.TelegramLauncher

fun Context.toast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/** Копирует текст в буфер обмена. На Android 13+ система сама показывает подтверждение. */
fun Context.copyToClipboard(text: String, confirmation: String = "Скопировано") {
    try {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Telegram channels", text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast(confirmation)
    } catch (e: Exception) {
        // Например, слишком большой объём для буфера обмена
        toast("Не удалось скопировать: список слишком большой. Используйте экспорт в файл", long = true)
    }
}

/** Открывает канал в Telegram и показывает понятное сообщение при неудаче. */
fun Context.openTelegramChannel(username: String) {
    when (val result = TelegramLauncher.open(this, username)) {
        TelegramLauncher.Result.OpenedInTelegram -> Unit
        TelegramLauncher.Result.OpenedViaWebLink ->
            toast("Telegram не найден — открываю ссылку t.me/$username")
        is TelegramLauncher.Result.Failed -> toast(result.message, long = true)
    }
}

/** Системное меню «Поделиться» для файла экспорта. */
fun Context.shareExport(export: SharedExport) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = export.mimeType
        putExtra(Intent.EXTRA_STREAM, export.uri)
        putExtra(Intent.EXTRA_SUBJECT, export.fileName)
        clipData = ClipData.newRawUri(export.fileName, export.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        startActivity(Intent.createChooser(send, "Экспорт: ${export.fileName}"))
    } catch (e: ActivityNotFoundException) {
        toast("Нет приложений, которые могут принять файл")
    }
}

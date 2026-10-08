package com.tgsorter.app.telegram

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tgsorter.app.domain.parser.ChannelListParser

/**
 * Единственная точка взаимодействия с Telegram: передача управления через Intent.
 * Никаких API, логинов и загрузки содержимого каналов.
 *
 *  1. `tg://resolve?domain=username` — открывает канал прямо в приложении Telegram;
 *  2. если обработчика `tg://` нет — `https://t.me/username` (браузер или Telegram по app-link).
 */
object TelegramLauncher {

    sealed interface Result {
        data object OpenedInTelegram : Result
        data object OpenedViaWebLink : Result
        data class Failed(val message: String) : Result
    }

    fun open(context: Context, username: String): Result {
        if (!ChannelListParser.isValidUsername(username)) {
            return Result.Failed("Некорректный username: @$username")
        }

        val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username"))
        if (tryStart(context, tgIntent)) return Result.OpenedInTelegram

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        if (tryStart(context, webIntent)) return Result.OpenedViaWebLink

        return Result.Failed("Telegram не установлен, и нет приложения для открытия ссылок t.me")
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

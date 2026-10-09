package com.tgsorter.app.telegram

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.tgsorter.app.domain.parser.ChannelListParser

/** Как открывать канал. Выбирается в меню экрана просмотра «Как открывать каналы». */
enum class OpenMethod(val id: String, val title: String, val description: String) {
    AUTO(
        "auto",
        "Telegram (tg://resolve)",
        "Ссылка адресуется напрямую установленному Telegram",
    ),
    SEARCH(
        "search",
        "Поиск в Telegram",
        "Telegram открывается на поиске с этим username — нажмите на найденный канал. " +
            "Работает, даже когда ссылки не открываются",
    ),
    TME_TELEGRAM(
        "tme_telegram",
        "Telegram (ссылка t.me)",
        "Как будто нажали ссылку t.me/username в другом приложении",
    ),
    CHOOSER(
        "chooser",
        "Спрашивать, чем открыть",
        "Системное окно выбора приложения для ссылки t.me",
    ),
    BROWSER(
        "browser",
        "Браузер: веб-превью t.me/s/…",
        "Лента публичного канала в браузере, без Telegram",
    );

    companion object {
        fun fromId(id: String?): OpenMethod = entries.firstOrNull { it.id == id } ?: AUTO
    }
}

/**
 * Единственная точка взаимодействия с Telegram: передача управления через Intent.
 * Никаких API, логинов и загрузки содержимого каналов.
 */
object TelegramLauncher {

    /** Канал для проверки: официальный канал Telegram, существует всегда. */
    const val TEST_USERNAME = "telegram"

    /** Официальные клиенты в порядке предпочтения (объявлены в <queries> манифеста). */
    private val KNOWN_CLIENTS = listOf(
        "org.telegram.messenger",      // Google Play
        "org.telegram.messenger.web",  // сайт telegram.org
        "org.telegram.messenger.beta",
        "org.thunderdog.challegram",   // Telegram X
    )

    sealed interface Result {
        data object Opened : Result
        data object OpenedViaWebLink : Result
        data class Failed(val message: String) : Result
    }

    fun open(context: Context, username: String, method: OpenMethod): Result {
        if (!ChannelListParser.isValidUsername(username)) {
            return Result.Failed("Некорректный username: @$username")
        }
        val tgUri = Uri.parse("tg://resolve?domain=$username")
        val webUri = Uri.parse("https://t.me/$username")
        val client = installedClient(context)

        return when (method) {
            OpenMethod.AUTO -> when {
                client != null && start(context, view(tgUri).setPackage(client)) -> Result.Opened
                start(context, view(tgUri)) -> Result.Opened
                client != null && start(context, view(webUri).setPackage(client)) -> Result.Opened
                start(context, view(webUri)) -> Result.OpenedViaWebLink
                else -> noApp()
            }

            // Поиск идёт другим запросом Telegram (contacts.search), а не resolveUsername,
            // поэтому работает и тогда, когда Telegram не открывает ссылки на каналы.
            OpenMethod.SEARCH -> {
                val searchUri = Uri.parse("tg://search?query=$username")
                when {
                    client != null && start(context, view(searchUri).setPackage(client)) -> Result.Opened
                    start(context, view(searchUri)) -> Result.Opened
                    else -> noApp()
                }
            }

            OpenMethod.TME_TELEGRAM -> when {
                client != null && start(context, view(webUri).setPackage(client)) -> Result.Opened
                start(context, view(webUri)) -> Result.Opened
                else -> noApp()
            }

            OpenMethod.CHOOSER ->
                if (start(context, Intent.createChooser(view(webUri), "Открыть @$username"))) Result.Opened else noApp()

            OpenMethod.BROWSER -> {
                val previewUri = Uri.parse("https://t.me/s/$username")
                val browser = browserPackage(context)
                when {
                    browser != null && start(context, view(previewUri).setPackage(browser)) -> Result.Opened
                    start(context, Intent.createChooser(view(previewUri), "Открыть в браузере")) -> Result.Opened
                    else -> Result.Failed("Не найден браузер")
                }
            }
        }
    }

    /** Текст для диагностики: какой Telegram установлен и кто обрабатывает ссылки. */
    fun diagnostics(context: Context): String {
        val pm = context.packageManager
        val client = installedClient(context)
        fun handlers(uri: String): String = try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(view(Uri.parse(uri)), 0)
                .map { "${it.loadLabel(pm)} (${it.activityInfo.packageName})" }
                .distinct()
                .joinToString(", ")
                .ifEmpty { "нет" }
        } catch (e: Exception) {
            "?"
        }
        return buildString {
            append("Официальный Telegram: ").append(client ?: "не найден").append('\n')
            append("tg:// открывает: ").append(handlers("tg://resolve?domain=$TEST_USERNAME")).append('\n')
            append("t.me открывает: ").append(handlers("https://t.me/$TEST_USERNAME"))
        }
    }

    private fun installedClient(context: Context): String? = KNOWN_CLIENTS.firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Браузер по умолчанию (или первый найденный), но не Telegram. */
    private fun browserPackage(context: Context): String? {
        val pm = context.packageManager
        val probe = view(Uri.parse("https://example.com"))
        return try {
            @Suppress("DEPRECATION")
            val default = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (default != null && default != "android" && default !in KNOWN_CLIENTS) {
                default
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, 0)
                    .map { it.activityInfo.packageName }
                    .firstOrNull { it !in KNOWN_CLIENTS && it != "android" }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun view(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun noApp() = Result.Failed("Telegram не установлен, и нет приложения для открытия ссылок t.me")

    private fun start(context: Context, intent: Intent): Boolean {
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

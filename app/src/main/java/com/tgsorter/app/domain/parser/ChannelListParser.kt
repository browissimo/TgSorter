package com.tgsorter.app.domain.parser

import com.tgsorter.app.domain.model.ChannelStatus

/**
 * Разбор текстового файла со списком Telegram-каналов.
 *
 * Поддерживаемые форматы строк:
 *  - `@username`
 *  - `username`
 *  - `https://t.me/username`, `http://t.me/username`, `t.me/username`, `www.t.me/username`
 *  - `https://t.me/s/username`, `https://t.me/username/123` (ссылка на пост)
 *  - `telegram.me/username`, `telegram.dog/username`, `username.t.me`
 *  - `tg://resolve?domain=username`
 *  - несколько ссылок/@упоминаний в одной строке, разделённых пробелами, `,` или `;`
 *  - CSV, экспортированный этим приложением (`username,status,position`) — статусы восстанавливаются
 *
 * Пустые строки и строки-комментарии (`#`, `//`) пропускаются, дубликаты удаляются
 * без учёта регистра (Telegram не различает регистр username), порядок сохраняется.
 */
object ChannelListParser {

    /** Telegram username: латиница, цифры и `_`, начинается с буквы, 4–32 символа. */
    private val USERNAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]{3,31}$")
    private val TOKEN_SEPARATORS = Regex("[\\s,;|]+")
    private val TELEGRAM_HOSTS = setOf("t.me", "telegram.me", "telegram.dog")
    private val SUBDOMAIN_HOST = Regex("^([A-Za-z0-9_]+)\\.t\\.me$", RegexOption.IGNORE_CASE)

    /** Служебные пути t.me, которые не являются username каналов. */
    private val RESERVED_PATHS = setOf(
        "joinchat", "addstickers", "addemoji", "addlist", "addtheme", "share", "proxy",
        "socks", "c", "iv", "setlanguage", "login", "invoice", "boost", "contact", "bg",
        "confirmphone", "giftcode", "m", "nft",
    )

    private const val MAX_INVALID_EXAMPLES = 5

    data class Entry(
        val username: String,
        /** Статус из CSV-резервной копии; null — обычный список (всё PENDING). */
        val status: ChannelStatus? = null,
    )

    data class Result(
        val entries: List<Entry>,
        /** Сколько повторов было удалено. */
        val duplicates: Int,
        /** Сколько непустых строк не удалось распознать. */
        val invalidLines: Int,
        /** Несколько примеров нераспознанных строк для показа пользователю. */
        val invalidExamples: List<String>,
        val isCsv: Boolean,
    ) {
        val restoredStatuses: Int
            get() = entries.count { it.status != null && it.status != ChannelStatus.PENDING }
    }

    fun isValidUsername(username: String): Boolean = USERNAME_REGEX.matches(username)

    fun parse(text: String): Result {
        val unique = LinkedHashMap<String, Entry>()
        var duplicates = 0
        var invalid = 0
        val examples = ArrayList<String>()
        var isCsv = false
        var firstContentLine = true

        for (rawLine in text.lineSequence()) {
            val line = rawLine.replace("﻿", "").trim()
            if (line.isEmpty()) continue

            if (firstContentLine) {
                firstContentLine = false
                if (isCsvHeader(line)) {
                    isCsv = true
                    continue
                }
            }
            if (line.startsWith("#") || line.startsWith("//")) continue

            val found: List<Entry> = if (isCsv) {
                listOfNotNull(parseCsvLine(line))
            } else {
                parseLine(line).map { Entry(it) }
            }

            if (found.isEmpty()) {
                invalid++
                if (examples.size < MAX_INVALID_EXAMPLES) examples += line.take(60)
                continue
            }
            for (entry in found) {
                val key = entry.username.lowercase()
                if (unique.containsKey(key)) duplicates++ else unique[key] = entry
            }
        }

        return Result(
            entries = unique.values.toList(),
            duplicates = duplicates,
            invalidLines = invalid,
            invalidExamples = examples,
            isCsv = isCsv,
        )
    }

    /** Извлекает все username из одной строки обычного списка. */
    fun parseLine(line: String): List<String> {
        val tokens = line.split(TOKEN_SEPARATORS).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        if (tokens.size == 1) return listOfNotNull(extractUsername(tokens[0], allowBare = true))

        // В строке несколько слов: берём только явно размеченные (@, t.me, tg://),
        // чтобы не превратить в каналы обычный текст вроде «Канал про котов».
        val marked = tokens.mapNotNull { extractUsername(it, allowBare = false) }
        if (marked.isNotEmpty()) return marked

        // «@ channel» или «t.me/ channel» — убираем пробелы и пробуем ещё раз.
        val joined = line.filterNot { it.isWhitespace() }
        return listOfNotNull(extractUsername(joined, allowBare = false))
    }

    /**
     * Нормализует одну «словоформу» в username без `@`.
     * @param allowBare принимать ли голое слово без `@`/ссылки.
     */
    fun extractUsername(rawToken: String, allowBare: Boolean = true): String? {
        val token = rawToken.trim()
            .trim('"', '\'', '`', '<', '>', '(', ')', '[', ']', '{', '}', '«', '»')
            .trimEnd('.', ',', ';', ':', '!', '?')
        if (token.isEmpty()) return null

        val candidate: String? = when {
            token.startsWith("tg://", ignoreCase = true) -> fromTgLink(token)
            token.startsWith("@") -> token.substring(1)
            else -> fromUrl(token) ?: if (allowBare) token else null
        }
        return candidate
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { USERNAME_REGEX.matches(it) }
    }

    private fun fromTgLink(token: String): String? {
        val query = token.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        return query.split('&').firstNotNullOfOrNull { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', missingDelimiterValue = "")
            if (key.equals("domain", ignoreCase = true)) value else null
        }
    }

    private fun fromUrl(token: String): String? {
        var rest = token
        val schemeEnd = rest.indexOf("://")
        if (schemeEnd >= 0) {
            val scheme = rest.substring(0, schemeEnd).lowercase()
            if (scheme != "http" && scheme != "https") return null
            rest = rest.substring(schemeEnd + 3)
        }

        val slash = rest.indexOf('/')
        val host = if (slash >= 0) rest.substring(0, slash) else rest
        val hostLower = host.lowercase().removePrefix("www.")
        val path = if (slash >= 0) rest.substring(slash + 1) else ""

        if (hostLower in TELEGRAM_HOSTS) {
            val segments = path.substringBefore('?').substringBefore('#')
                .split('/')
                .filter { it.isNotEmpty() }
            var first = segments.firstOrNull() ?: return null
            if (first.equals("s", ignoreCase = true)) first = segments.getOrNull(1) ?: return null
            if (first.startsWith("+") || first.lowercase() in RESERVED_PATHS) return null
            return first
        }

        // Формат username.t.me
        SUBDOMAIN_HOST.matchEntire(host.removePrefix("www."))?.let { match ->
            val name = match.groupValues[1]
            if (!name.equals("www", ignoreCase = true)) return name
        }
        return null
    }

    private fun isCsvHeader(line: String): Boolean {
        val cells = line.lowercase().split(',', ';').map { it.trim().trim('"') }
        return cells.firstOrNull() == "username" && "status" in cells
    }

    private fun parseCsvLine(line: String): Entry? {
        val cells = line.split(',', ';').map { it.trim().trim('"') }
        val username = extractUsername(cells.getOrElse(0) { "" }, allowBare = true) ?: return null
        val status = cells.getOrNull(1)?.let { ChannelStatus.parse(it) } ?: ChannelStatus.PENDING
        return Entry(username, status)
    }
}

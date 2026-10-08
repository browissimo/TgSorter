package com.tgsorter.app.domain.model

/**
 * Статус канала.
 *
 * [dbValue] — стабильное строковое значение, которое хранится в БД и в CSV.
 * Не зависит от имён enum-констант, поэтому безопасно при обфускации R8.
 */
enum class ChannelStatus(val dbValue: String) {
    PENDING("PENDING"),
    POSITIVE("POSITIVE"),
    NEGATIVE("NEGATIVE"),
    SKIPPED("SKIPPED");

    companion object {
        fun fromDb(value: String): ChannelStatus =
            entries.firstOrNull { it.dbValue == value } ?: PENDING

        /** Разбор статуса из CSV / пользовательского текста: POSITIVE, +, NEGATIVE, -, ... */
        fun parse(text: String): ChannelStatus? = when (text.trim().uppercase()) {
            "POSITIVE", "+", "YES", "ДА" -> POSITIVE
            "NEGATIVE", "-", "−", "NO", "НЕТ" -> NEGATIVE
            "SKIPPED", "SKIP" -> SKIPPED
            "PENDING", "" -> PENDING
            else -> null
        }
    }
}

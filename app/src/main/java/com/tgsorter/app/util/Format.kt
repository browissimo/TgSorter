package com.tgsorter.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val ruLocale: Locale = Locale.forLanguageTag("ru-RU")

/** 10000 → «10 000». */
fun Int.formatCount(): String = NumberFormat.getIntegerInstance(ruLocale).format(this)

/** Русское склонение: pluralRu(5, "канал", "канала", "каналов") → «каналов». */
fun pluralRu(n: Int, one: String, few: String, many: String): String {
    val n100 = abs(n) % 100
    val n10 = n100 % 10
    return when {
        n100 in 11..14 -> many
        n10 == 1 -> one
        n10 in 2..4 -> few
        else -> many
    }
}

fun channelsCount(n: Int): String = "${n.formatCount()} ${pluralRu(n, "канал", "канала", "каналов")}"

/** 0.3777f → «37,8 %». */
fun Float.formatPercent(): String = String.format(ruLocale, "%.1f%%", this * 100f)

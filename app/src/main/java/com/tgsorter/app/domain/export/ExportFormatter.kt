package com.tgsorter.app.domain.export

import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelStatus

/** Что экспортируем и под каким именем файла. */
enum class ExportKind(
    val fileName: String,
    val mimeType: String,
    /** null — общий CSV со всеми каналами. */
    val status: ChannelStatus?,
) {
    POSITIVE("positive.txt", "text/plain", ChannelStatus.POSITIVE),
    NEGATIVE("negative.txt", "text/plain", ChannelStatus.NEGATIVE),
    SKIPPED("skipped.txt", "text/plain", ChannelStatus.SKIPPED),
    PENDING("pending.txt", "text/plain", ChannelStatus.PENDING),
    CSV("channels.csv", "text/csv", null);

    companion object {
        fun forStatus(status: ChannelStatus): ExportKind = entries.first { it.status == status }
    }
}

object ExportFormatter {

    const val CSV_HEADER = "username,status,position"

    /** Список `@username`, по одному на строку. */
    fun usernames(channels: List<Channel>): String =
        channels.joinToString(separator = "\n", postfix = if (channels.isEmpty()) "" else "\n") {
            "@" + it.username
        }

    /** CSV `username,status,position` для всех каналов списка. */
    fun csv(channels: List<Channel>): String = buildString {
        append(CSV_HEADER).append('\n')
        for (channel in channels) {
            append(csvCell(channel.username)).append(',')
            append(channel.status.dbValue).append(',')
            append(channel.position).append('\n')
        }
    }

    fun format(kind: ExportKind, channels: List<Channel>): String =
        if (kind == ExportKind.CSV) csv(channels) else usernames(channels)

    private fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

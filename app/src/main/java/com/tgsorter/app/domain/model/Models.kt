package com.tgsorter.app.domain.model

/** Канал внутри списка. [position] — порядковый номер в исходном файле, начиная с 1. */
data class Channel(
    val id: Long,
    val listId: Long,
    val username: String,
    val position: Int,
    val status: ChannelStatus,
    val reviewedAt: Long?,
)

/** Счётчики по статусам для одного списка. */
data class StatusCounts(
    val pending: Int = 0,
    val positive: Int = 0,
    val negative: Int = 0,
    val skipped: Int = 0,
) {
    val total: Int get() = pending + positive + negative + skipped

    /** «Просмотрено» — каналы, получившие окончательную оценку + или −. */
    val reviewed: Int get() = positive + negative

    /** Обработано — всё, что уже не в очереди непросмотренных (включая пропущенные). */
    val processed: Int get() = total - pending

    /** Доля обработанных каналов, 0..1. */
    val progress: Float get() = if (total == 0) 0f else processed.toFloat() / total

    fun of(status: ChannelStatus): Int = when (status) {
        ChannelStatus.PENDING -> pending
        ChannelStatus.POSITIVE -> positive
        ChannelStatus.NEGATIVE -> negative
        ChannelStatus.SKIPPED -> skipped
    }

    companion object {
        fun from(pairs: List<Pair<ChannelStatus, Int>>): StatusCounts {
            var result = StatusCounts()
            for ((status, count) in pairs) {
                result = when (status) {
                    ChannelStatus.PENDING -> result.copy(pending = result.pending + count)
                    ChannelStatus.POSITIVE -> result.copy(positive = result.positive + count)
                    ChannelStatus.NEGATIVE -> result.copy(negative = result.negative + count)
                    ChannelStatus.SKIPPED -> result.copy(skipped = result.skipped + count)
                }
            }
            return result
        }
    }
}

/** Импортированный набор каналов («список») со статистикой. */
data class ChannelListSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val lastOpenedAt: Long,
    val currentPosition: Int,
    val counts: StatusCounts,
)

/** Базовая информация о списке без статистики. */
data class ChannelListInfo(
    val id: Long,
    val name: String,
    val currentPosition: Int,
)

/** Последнее действие из истории (для строки «Отменить»). */
data class LastAction(
    val historyId: Long,
    val channelId: Long,
    val username: String,
    val position: Int,
    val newStatus: ChannelStatus,
)

/** Какую очередь разбирает экран просмотра. */
enum class ReviewMode(val queueStatus: ChannelStatus) {
    /** Обычный режим: непросмотренные каналы. */
    PENDING(ChannelStatus.PENDING),

    /** Повторный разбор пропущенных каналов. */
    SKIPPED(ChannelStatus.SKIPPED);

    companion object {
        fun fromArg(value: String?): ReviewMode =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

package com.tgsorter.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tgsorter.app.domain.model.ChannelStatus

/** Импортированный набор каналов. */
@Entity(tableName = "channel_lists")
data class ChannelListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    /** Чем больше — тем «активнее» список (показывается первым на главном экране). */
    val lastOpenedAt: Long,
    /** Позиция канала, который сейчас открыт на экране просмотра (1-based). */
    val currentPosition: Int,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = ChannelListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["listId", "position"], unique = true),
        Index(value = ["listId", "status", "position"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val username: String,
    /** Порядковый номер в исходном файле, начиная с 1. */
    val position: Int,
    val status: ChannelStatus,
    val reviewedAt: Long?,
)

/** Журнал действий для Undo (хранится в БД, поэтому переживает перезапуск). */
@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = ChannelListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["listId"])],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val channelId: Long,
    val previousStatus: ChannelStatus,
    val previousReviewedAt: Long?,
    val newStatus: ChannelStatus,
    /** Куда вернуть указатель просмотра при отмене. */
    val previousPosition: Int,
    val createdAt: Long,
)

/** Строка агрегированного запроса для главного экрана. */
data class ListSummaryRow(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val lastOpenedAt: Long,
    val currentPosition: Int,
    val total: Int,
    val positive: Int,
    val negative: Int,
    val skipped: Int,
)

data class StatusCountRow(
    val status: ChannelStatus,
    val count: Int,
)

data class LastActionRow(
    val id: Long,
    val channelId: Long,
    val newStatus: ChannelStatus,
    val username: String,
    val position: Int,
)

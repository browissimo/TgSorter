package com.tgsorter.app.data.repository

import androidx.room.withTransaction
import com.tgsorter.app.data.database.AppDatabase
import com.tgsorter.app.data.database.ChannelEntity
import com.tgsorter.app.data.database.ChannelListEntity
import com.tgsorter.app.data.database.HistoryEntity
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelListInfo
import com.tgsorter.app.domain.model.ChannelListSummary
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.model.LastAction
import com.tgsorter.app.domain.model.StatusCounts
import com.tgsorter.app.domain.parser.ChannelListParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Результат отметки канала на экране просмотра. */
sealed interface RateOutcome {
    /** Отметка сохранена; [next] — канал, на который перешёл указатель (null — очередь пуста). */
    data class Done(val next: Channel?) : RateOutcome

    /** Нажатие пришло для канала, который уже не текущий (например, двойной тап) — проигнорировано. */
    data object Ignored : RateOutcome
}

data class AppendOutcome(val added: Int, val alreadyInList: Int)

/**
 * Единственная точка доступа к данным. Все изменения выполняются в транзакциях Room,
 * поэтому прогресс сохраняется сразу после каждого нажатия и не теряется при закрытии.
 */
class ChannelRepository(private val db: AppDatabase) {

    private val listDao = db.channelListDao()
    private val channelDao = db.channelDao()
    private val historyDao = db.historyDao()

    // ---------- Наблюдение ----------

    fun observeListSummaries(): Flow<List<ChannelListSummary>> =
        listDao.observeSummaries().map { rows ->
            rows.map { row ->
                ChannelListSummary(
                    id = row.id,
                    name = row.name,
                    createdAt = row.createdAt,
                    lastOpenedAt = row.lastOpenedAt,
                    currentPosition = row.currentPosition,
                    counts = StatusCounts(
                        pending = row.total - row.positive - row.negative - row.skipped,
                        positive = row.positive,
                        negative = row.negative,
                        skipped = row.skipped,
                    ),
                )
            }
        }

    fun observeList(listId: Long): Flow<ChannelListInfo?> =
        listDao.observe(listId)
            .map { it?.let { e -> ChannelListInfo(e.id, e.name, e.currentPosition) } }
            .distinctUntilChanged()

    fun observeCurrentChannel(listId: Long): Flow<Channel?> =
        channelDao.observeCurrent(listId).map { it?.toDomain() }.distinctUntilChanged()

    fun observeCounts(listId: Long): Flow<StatusCounts> =
        channelDao.observeCounts(listId)
            .map { rows -> StatusCounts.from(rows.map { it.status to it.count }) }
            .distinctUntilChanged()

    fun observeChannels(listId: Long, status: ChannelStatus): Flow<List<Channel>> =
        channelDao.observeByStatus(listId, status).map { list -> list.map { it.toDomain() } }

    fun observeLastAction(listId: Long): Flow<LastAction?> =
        historyDao.observeLast(listId)
            .map { row ->
                row?.let { LastAction(it.id, it.channelId, it.username, it.position, it.newStatus) }
            }
            .distinctUntilChanged()

    fun observeUndoCount(listId: Long): Flow<Int> =
        historyDao.observeCount(listId).distinctUntilChanged()

    // ---------- Списки ----------

    /** Создаёт новый список из распознанных записей. Возвращает id списка. */
    suspend fun createList(name: String, entries: List<ChannelListParser.Entry>): Long {
        require(entries.isNotEmpty()) { "Пустой список" }
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val listId = listDao.insert(
                ChannelListEntity(name = name, createdAt = now, lastOpenedAt = now, currentPosition = 1),
            )
            val channels = entries.mapIndexed { index, entry ->
                val status = entry.status ?: ChannelStatus.PENDING
                ChannelEntity(
                    listId = listId,
                    username = entry.username,
                    position = index + 1,
                    status = status,
                    reviewedAt = if (status == ChannelStatus.PENDING) null else now,
                )
            }
            channelDao.insertAll(channels)
            listDao.touch(listId, now) // новый список становится активным
            // Для восстановленного CSV сразу встаём на первый непросмотренный канал.
            channelDao.firstWithStatus(listId, ChannelStatus.PENDING)?.let {
                listDao.setCurrentPosition(listId, it.position)
            }
            listId
        }
    }

    /** Дополняет существующий список новыми каналами (дубликаты пропускаются). */
    suspend fun appendToList(listId: Long, entries: List<ChannelListParser.Entry>): AppendOutcome =
        db.withTransaction {
            val existing = channelDao.getUsernames(listId).mapTo(HashSet()) { it.lowercase() }
            var position = channelDao.maxPosition(listId)
            val now = System.currentTimeMillis()
            var already = 0
            val toInsert = ArrayList<ChannelEntity>()
            for (entry in entries) {
                if (!existing.add(entry.username.lowercase())) {
                    already++
                    continue
                }
                position++
                val status = entry.status ?: ChannelStatus.PENDING
                toInsert += ChannelEntity(
                    listId = listId,
                    username = entry.username,
                    position = position,
                    status = status,
                    reviewedAt = if (status == ChannelStatus.PENDING) null else now,
                )
            }
            if (toInsert.isNotEmpty()) channelDao.insertAll(toInsert)
            AppendOutcome(added = toInsert.size, alreadyInList = already)
        }

    suspend fun renameList(listId: Long, name: String) = listDao.rename(listId, name)

    suspend fun deleteList(listId: Long) = db.withTransaction {
        historyDao.deleteForList(listId)
        channelDao.deleteForList(listId)
        listDao.delete(listId)
    }

    suspend fun touchList(listId: Long) = listDao.touch(listId, System.currentTimeMillis())

    // ---------- Просмотр ----------

    /**
     * Канал, с которого нужно продолжить разбор очереди [queue]:
     * текущий, если он ещё в очереди, иначе следующий за ним (по кругу).
     */
    suspend fun findResumeChannel(listId: Long, queue: ChannelStatus = ChannelStatus.PENDING): Channel? {
        val list = listDao.get(listId) ?: return null
        return findResume(listId, list.currentPosition, queue)?.toDomain()
    }

    /** Ставит указатель на канал для продолжения работы и делает список активным. */
    suspend fun prepareReview(listId: Long, queue: ChannelStatus) {
        db.withTransaction {
            val list = listDao.get(listId) ?: return@withTransaction
            val target = findResume(listId, list.currentPosition, queue)
            when {
                target != null -> if (target.position != list.currentPosition) {
                    listDao.setCurrentPosition(listId, target.position)
                }
                // Очередь пуста: просто убеждаемся, что указатель стоит на существующем канале.
                channelDao.getAt(listId, list.currentPosition) == null ->
                    channelDao.first(listId)?.let { listDao.setCurrentPosition(listId, it.position) }
            }
            listDao.touch(listId, System.currentTimeMillis())
        }
    }

    /**
     * Отмечает канал и переходит к следующему в очереди [queue].
     * Срабатывает только для канала, который сейчас текущий, — это защищает
     * от двойного тапа, который иначе отметил бы следующий канал вслепую.
     */
    suspend fun rate(
        listId: Long,
        channelId: Long,
        newStatus: ChannelStatus,
        queue: ChannelStatus,
    ): RateOutcome = db.withTransaction {
        val list = listDao.get(listId) ?: return@withTransaction RateOutcome.Ignored
        val channel = channelDao.getById(channelId) ?: return@withTransaction RateOutcome.Ignored
        if (channel.listId != listId || channel.position != list.currentPosition) {
            return@withTransaction RateOutcome.Ignored
        }

        val now = System.currentTimeMillis()
        historyDao.insert(
            HistoryEntity(
                listId = listId,
                channelId = channel.id,
                previousStatus = channel.status,
                previousReviewedAt = channel.reviewedAt,
                newStatus = newStatus,
                previousPosition = list.currentPosition,
                createdAt = now,
            ),
        )
        historyDao.trim(listId, UNDO_LIMIT)
        channelDao.updateStatus(channel.id, newStatus, if (newStatus == ChannelStatus.PENDING) null else now)

        val next = channelDao.nextWithStatus(listId, queue, channel.position)
            ?: channelDao.firstWithStatus(listId, queue)
        if (next != null) listDao.setCurrentPosition(listId, next.position)
        RateOutcome.Done(next?.toDomain())
    }

    /** Меняет статус из экранов списков (без перемещения указателя). Тоже попадает в Undo. */
    suspend fun setStatus(listId: Long, channelId: Long, newStatus: ChannelStatus) {
        db.withTransaction {
            val list = listDao.get(listId) ?: return@withTransaction
            val channel = channelDao.getById(channelId) ?: return@withTransaction
            if (channel.status == newStatus) return@withTransaction
            val now = System.currentTimeMillis()
            historyDao.insert(
                HistoryEntity(
                    listId = listId,
                    channelId = channel.id,
                    previousStatus = channel.status,
                    previousReviewedAt = channel.reviewedAt,
                    newStatus = newStatus,
                    previousPosition = list.currentPosition,
                    createdAt = now,
                ),
            )
            historyDao.trim(listId, UNDO_LIMIT)
            channelDao.updateStatus(channel.id, newStatus, if (newStatus == ChannelStatus.PENDING) null else now)
        }
    }

    /** Отменяет последнее действие. Возвращает восстановленный канал или null, если отменять нечего. */
    suspend fun undo(listId: Long): Channel? = db.withTransaction {
        val entry = historyDao.last(listId) ?: return@withTransaction null
        channelDao.updateStatus(entry.channelId, entry.previousStatus, entry.previousReviewedAt)
        listDao.setCurrentPosition(listId, entry.previousPosition)
        historyDao.delete(entry.id)
        channelDao.getById(entry.channelId)?.toDomain()
    }

    suspend fun moveToPosition(listId: Long, position: Int): Boolean {
        val channel = channelDao.getAt(listId, position) ?: return false
        listDao.setCurrentPosition(listId, channel.position)
        return true
    }

    suspend fun moveNext(listId: Long): Boolean = db.withTransaction {
        val list = listDao.get(listId) ?: return@withTransaction false
        val next = channelDao.nextAny(listId, list.currentPosition) ?: return@withTransaction false
        listDao.setCurrentPosition(listId, next.position)
        true
    }

    suspend fun movePrevious(listId: Long): Boolean = db.withTransaction {
        val list = listDao.get(listId) ?: return@withTransaction false
        val previous = channelDao.previousAny(listId, list.currentPosition) ?: return@withTransaction false
        listDao.setCurrentPosition(listId, previous.position)
        true
    }

    suspend fun moveToLastReviewed(listId: Long): Boolean {
        val channel = channelDao.lastReviewed(listId) ?: return false
        listDao.setCurrentPosition(listId, channel.position)
        return true
    }

    suspend fun moveToFirstWithStatus(listId: Long, status: ChannelStatus): Boolean {
        val channel = channelDao.firstWithStatus(listId, status) ?: return false
        listDao.setCurrentPosition(listId, channel.position)
        return true
    }

    // ---------- Экспорт ----------

    suspend fun getChannels(listId: Long, status: ChannelStatus?): List<Channel> =
        (if (status == null) channelDao.getAll(listId) else channelDao.getByStatus(listId, status))
            .map { it.toDomain() }

    suspend fun getListName(listId: Long): String? = listDao.get(listId)?.name

    // ---------- Внутреннее ----------

    private suspend fun findResume(listId: Long, currentPosition: Int, queue: ChannelStatus): ChannelEntity? {
        val current = channelDao.getAt(listId, currentPosition)
        if (current != null && current.status == queue) return current
        return channelDao.nextWithStatus(listId, queue, currentPosition)
            ?: channelDao.firstWithStatus(listId, queue)
    }

    private fun ChannelEntity.toDomain() = Channel(
        id = id,
        listId = listId,
        username = username,
        position = position,
        status = status,
        reviewedAt = reviewedAt,
    )

    companion object {
        /** Сколько последних действий можно отменить. */
        const val UNDO_LIMIT = 20
    }
}

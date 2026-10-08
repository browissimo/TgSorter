package com.tgsorter.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tgsorter.app.domain.model.ChannelStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelListDao {

    @Insert
    suspend fun insert(list: ChannelListEntity): Long

    @Query("SELECT * FROM channel_lists WHERE id = :listId")
    suspend fun get(listId: Long): ChannelListEntity?

    @Query("SELECT * FROM channel_lists WHERE id = :listId")
    fun observe(listId: Long): Flow<ChannelListEntity?>

    @Query(
        """
        SELECT l.id AS id, l.name AS name, l.createdAt AS createdAt,
               l.lastOpenedAt AS lastOpenedAt, l.currentPosition AS currentPosition,
               COUNT(c.id) AS total,
               COALESCE(SUM(CASE WHEN c.status = 'POSITIVE' THEN 1 ELSE 0 END), 0) AS positive,
               COALESCE(SUM(CASE WHEN c.status = 'NEGATIVE' THEN 1 ELSE 0 END), 0) AS negative,
               COALESCE(SUM(CASE WHEN c.status = 'SKIPPED' THEN 1 ELSE 0 END), 0) AS skipped
        FROM channel_lists l
        LEFT JOIN channels c ON c.listId = l.id
        GROUP BY l.id
        ORDER BY l.lastOpenedAt DESC, l.id DESC
        """,
    )
    fun observeSummaries(): Flow<List<ListSummaryRow>>

    @Query("UPDATE channel_lists SET currentPosition = :position WHERE id = :listId")
    suspend fun setCurrentPosition(listId: Long, position: Int)

    /** Делает список самым «свежим»: время строго больше, чем у всех остальных списков. */
    @Query(
        """
        UPDATE channel_lists
        SET lastOpenedAt = MAX(:time, (SELECT COALESCE(MAX(lastOpenedAt), 0) + 1 FROM channel_lists))
        WHERE id = :listId
        """,
    )
    suspend fun touch(listId: Long, time: Long)

    @Query("UPDATE channel_lists SET name = :name WHERE id = :listId")
    suspend fun rename(listId: Long, name: String)

    @Query("DELETE FROM channel_lists WHERE id = :listId")
    suspend fun delete(listId: Long)
}

@Dao
interface ChannelDao {

    @Insert
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channels WHERE listId = :listId AND position = :position LIMIT 1")
    suspend fun getAt(listId: Long, position: Int): ChannelEntity?

    /** Текущий канал экрана просмотра — одним запросом, чтобы UI не видел промежуточных состояний. */
    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN channel_lists l ON l.id = c.listId AND c.position = l.currentPosition
        WHERE l.id = :listId
        LIMIT 1
        """,
    )
    fun observeCurrent(listId: Long): Flow<ChannelEntity?>

    @Query(
        """
        SELECT * FROM channels
        WHERE listId = :listId AND status = :status AND position > :after
        ORDER BY position LIMIT 1
        """,
    )
    suspend fun nextWithStatus(listId: Long, status: ChannelStatus, after: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE listId = :listId AND status = :status ORDER BY position LIMIT 1")
    suspend fun firstWithStatus(listId: Long, status: ChannelStatus): ChannelEntity?

    @Query("SELECT * FROM channels WHERE listId = :listId AND position > :after ORDER BY position LIMIT 1")
    suspend fun nextAny(listId: Long, after: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE listId = :listId AND position < :before ORDER BY position DESC LIMIT 1")
    suspend fun previousAny(listId: Long, before: Int): ChannelEntity?

    @Query("SELECT * FROM channels WHERE listId = :listId ORDER BY position LIMIT 1")
    suspend fun first(listId: Long): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels
        WHERE listId = :listId AND status != 'PENDING' AND reviewedAt IS NOT NULL
        ORDER BY reviewedAt DESC, id DESC LIMIT 1
        """,
    )
    suspend fun lastReviewed(listId: Long): ChannelEntity?

    @Query("SELECT status AS status, COUNT(*) AS count FROM channels WHERE listId = :listId GROUP BY status")
    fun observeCounts(listId: Long): Flow<List<StatusCountRow>>

    @Query("SELECT * FROM channels WHERE listId = :listId AND status = :status ORDER BY position")
    fun observeByStatus(listId: Long, status: ChannelStatus): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE listId = :listId AND status = :status ORDER BY position")
    suspend fun getByStatus(listId: Long, status: ChannelStatus): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE listId = :listId ORDER BY position")
    suspend fun getAll(listId: Long): List<ChannelEntity>

    @Query("SELECT username FROM channels WHERE listId = :listId")
    suspend fun getUsernames(listId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position), 0) FROM channels WHERE listId = :listId")
    suspend fun maxPosition(listId: Long): Int

    @Query("UPDATE channels SET status = :status, reviewedAt = :reviewedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ChannelStatus, reviewedAt: Long?)

    @Query("DELETE FROM channels WHERE listId = :listId")
    suspend fun deleteForList(listId: Long)
}

@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("SELECT * FROM history WHERE listId = :listId ORDER BY id DESC LIMIT 1")
    suspend fun last(listId: Long): HistoryEntity?

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    /** Оставляет только [keep] последних записей списка. */
    @Query(
        """
        DELETE FROM history
        WHERE listId = :listId AND id NOT IN (
            SELECT id FROM history WHERE listId = :listId ORDER BY id DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(listId: Long, keep: Int)

    @Query("SELECT COUNT(*) FROM history WHERE listId = :listId")
    fun observeCount(listId: Long): Flow<Int>

    @Query(
        """
        SELECT h.id AS id, h.channelId AS channelId, h.newStatus AS newStatus,
               c.username AS username, c.position AS position
        FROM history h
        INNER JOIN channels c ON c.id = h.channelId
        WHERE h.listId = :listId
        ORDER BY h.id DESC LIMIT 1
        """,
    )
    fun observeLast(listId: Long): Flow<LastActionRow?>

    @Query("DELETE FROM history WHERE listId = :listId")
    suspend fun deleteForList(listId: Long)
}

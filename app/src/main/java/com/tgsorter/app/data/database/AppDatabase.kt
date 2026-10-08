package com.tgsorter.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.tgsorter.app.domain.model.ChannelStatus

@Database(
    entities = [ChannelListEntity::class, ChannelEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun channelListDao(): ChannelListDao
    abstract fun channelDao(): ChannelDao
    abstract fun historyDao(): HistoryDao

    companion object {
        private const val DB_NAME = "tg_sorter.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                // Никаких destructive-миграций: данные пользователя не должны теряться.
                // При изменении схемы добавляйте Migration и повышайте version.
                .build()
    }
}

class Converters {
    @TypeConverter
    fun statusToDb(status: ChannelStatus): String = status.dbValue

    @TypeConverter
    fun statusFromDb(value: String): ChannelStatus = ChannelStatus.fromDb(value)
}

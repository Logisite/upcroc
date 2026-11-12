package app.upcroc.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [JobEntity::class, OutboxEntity::class, CheckEntity::class, EngineStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UpcrocDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun outboxDao(): OutboxDao
    abstract fun checkDao(): CheckDao
    abstract fun engineStateDao(): EngineStateDao

    companion object {
        fun build(context: Context): UpcrocDatabase =
            Room.databaseBuilder(context, UpcrocDatabase::class.java, "upcroc.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}

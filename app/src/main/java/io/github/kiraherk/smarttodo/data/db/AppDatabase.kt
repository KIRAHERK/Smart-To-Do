package io.github.kiraherk.smarttodo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        TaskEntity::class,
        OccurrenceEntity::class,
        NoteEntity::class,
        ActivityEntity::class,
        InboxEntity::class,
        ChatMessageEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun occurrences(): OccurrenceDao
    abstract fun notes(): NoteDao
    abstract fun activity(): ActivityDao
    abstract fun inbox(): InboxDao
    abstract fun chat(): ChatDao

    companion object {
        const val NAME = "smarttodo.db"
        const val VERSION = 1

        /**
         * Every schema change MUST add a migration here (e.g. MIGRATION_1_2) and a test.
         * We never use fallbackToDestructiveMigration: an app update must never wipe his history.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

package io.github.kiraherk.smarttodo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        OccurrenceEntity::class,
        NoteEntity::class,
        ActivityEntity::class,
        InboxEntity::class,
        ChatMessageEntity::class,
        CategoryEntity::class,
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
    abstract fun categories(): CategoryDao

    companion object {
        const val NAME = "smarttodo.db"
        const val VERSION = 2

        /**
         * Every schema change MUST add a migration here (e.g. MIGRATION_1_2) and a test.
         * We never use fallbackToDestructiveMigration: an app update must never wipe his history.
         */
        val MIGRATIONS: Array<Migration> get() = arrayOf(MIGRATION_1_2)

        /** The four categories every install starts with (names come from the app language). */
        const val SEED_CATEGORIES = "INSERT OR IGNORE INTO categories (`key`, name, colorIndex, icon, sortOrder) VALUES " +
            "('WORK', '', 0, 'work', 0), ('HOME', '', 1, 'home', 1), ('PERSONAL', '', 2, 'person', 2), ('TRAVEL', '', 3, 'flight', 3)"

        /** Version 2 adds his own categories. Nothing existing is touched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (`key` TEXT NOT NULL, `name` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, " +
                        "`icon` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
                db.execSQL(SEED_CATEGORIES)
            }
        }

        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(SEED_CATEGORIES)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(SEED_CALLBACK)
                .build()
    }
}

package io.github.kiraherk.smarttodo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks WHERE id = :id") fun observe(id: Long): Flow<TaskEntity?>
    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY updatedAt DESC") suspend fun active(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE archived = 0 AND recurrenceJson IS NOT NULL") suspend fun activeRecurring(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE archived = 1 ORDER BY updatedAt DESC LIMIT :limit") suspend fun recentlyDeleted(limit: Int = 15): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE searchText LIKE '%' || :q || '%' ORDER BY archived, updatedAt DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 50): List<TaskEntity>

    @Query("SELECT * FROM tasks") suspend fun all(): List<TaskEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<TaskEntity>)
    @Query("DELETE FROM tasks") suspend fun deleteAll()
}

@Dao
interface OccurrenceDao {
    @Insert suspend fun insert(occurrence: OccurrenceEntity): Long
    @Update suspend fun update(occurrence: OccurrenceEntity)

    @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun get(id: Long): OccurrenceEntity?

    @Transaction
    @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun getWithTask(id: Long): OccurrenceWithTask?

    @Transaction
    @Query(
        """SELECT o.* FROM occurrences o JOIN tasks t ON t.id = o.taskId
           WHERE t.archived = 0 AND o.status IN (:open)
           ORDER BY o.date IS NULL, o.date, o.time IS NULL, o.time, o.id"""
    )
    fun observeOpen(open: List<String>): Flow<List<OccurrenceWithTask>>

    @Transaction
    @Query(
        """SELECT o.* FROM occurrences o JOIN tasks t ON t.id = o.taskId
           WHERE t.archived = 0 AND o.status IN (:open)
           ORDER BY o.date IS NULL, o.date, o.time IS NULL, o.time, o.id"""
    )
    suspend fun open(open: List<String>): List<OccurrenceWithTask>

    @Transaction
    @Query(
        """SELECT o.* FROM occurrences o JOIN tasks t ON t.id = o.taskId
           WHERE t.archived = 0 AND o.date BETWEEN :from AND :to
           ORDER BY o.date, o.time IS NULL, o.time, o.id"""
    )
    suspend fun between(from: String, to: String): List<OccurrenceWithTask>

    @Transaction
    @Query(
        """SELECT o.* FROM occurrences o JOIN tasks t ON t.id = o.taskId
           WHERE t.archived = 0 AND o.date BETWEEN :from AND :to
           ORDER BY o.date, o.time IS NULL, o.time, o.id"""
    )
    fun observeBetween(from: String, to: String): Flow<List<OccurrenceWithTask>>

    @Query("SELECT * FROM occurrences WHERE taskId = :taskId ORDER BY date IS NULL DESC, date DESC, id DESC")
    fun observeForTask(taskId: Long): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrences WHERE taskId = :taskId AND status IN (:open) ORDER BY date IS NULL, date, id")
    suspend fun openForTask(taskId: Long, open: List<String>): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE taskId = :taskId ORDER BY date IS NULL, date DESC, id DESC LIMIT 1")
    suspend fun latestForTask(taskId: Long): OccurrenceEntity?

    @Query(
        """SELECT o.* FROM occurrences o JOIN tasks t ON t.id = o.taskId
           WHERE t.archived = 0 AND o.remindAt IS NOT NULL AND o.reminderFiredAt IS NULL AND o.status IN (:open)"""
    )
    suspend fun pendingReminders(open: List<String>): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences") suspend fun all(): List<OccurrenceEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<OccurrenceEntity>)
    @Query("DELETE FROM occurrences") suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Insert suspend fun insert(note: NoteEntity): Long
    @Query("SELECT * FROM notes WHERE taskId = :taskId ORDER BY createdAt DESC") fun observeForTask(taskId: Long): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE taskId = :taskId ORDER BY createdAt DESC") suspend fun forTask(taskId: Long): List<NoteEntity>
    @Query("SELECT * FROM notes WHERE createdAt >= :since ORDER BY createdAt") suspend fun since(since: Long): List<NoteEntity>
    @Query("SELECT * FROM notes WHERE searchText LIKE '%' || :q || '%' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 50): List<NoteEntity>

    @Query("SELECT * FROM notes") suspend fun all(): List<NoteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<NoteEntity>)
    @Query("DELETE FROM notes") suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Insert suspend fun insert(activity: ActivityEntity): Long
    @Query("SELECT * FROM activity WHERE taskId = :taskId ORDER BY at DESC, id DESC") fun observeForTask(taskId: Long): Flow<List<ActivityEntity>>

    @Transaction
    @Query("SELECT * FROM activity WHERE at >= :since ORDER BY at DESC, id DESC")
    fun observeSince(since: Long): Flow<List<ActivityWithTask>>

    @Transaction
    @Query("SELECT * FROM activity WHERE at >= :since ORDER BY at, id")
    suspend fun since(since: Long): List<ActivityWithTask>

    @Query("SELECT * FROM activity") suspend fun all(): List<ActivityEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ActivityEntity>)
    @Query("DELETE FROM activity") suspend fun deleteAll()
}

@Dao
interface InboxDao {
    @Insert suspend fun insert(item: InboxEntity): Long
    @Update suspend fun update(item: InboxEntity)
    @Query("SELECT * FROM inbox WHERE id = :id") suspend fun get(id: Long): InboxEntity?
    @Query("DELETE FROM inbox WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM inbox ORDER BY createdAt") fun observeAll(): Flow<List<InboxEntity>>
    @Query("SELECT COUNT(*) FROM inbox") fun observeCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM inbox") suspend fun count(): Int

    @Query("SELECT * FROM inbox") suspend fun all(): List<InboxEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<InboxEntity>)
    @Query("DELETE FROM inbox") suspend fun deleteAll()
}

@Dao
interface ChatDao {
    @Insert suspend fun insert(message: ChatMessageEntity): Long
    @Query("SELECT * FROM chat_messages ORDER BY at, id") fun observeAll(): Flow<List<ChatMessageEntity>>
    @Query("SELECT * FROM (SELECT * FROM chat_messages ORDER BY at DESC, id DESC LIMIT :limit) ORDER BY at, id")
    suspend fun recent(limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages") suspend fun all(): List<ChatMessageEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ChatMessageEntity>)
    @Query("DELETE FROM chat_messages") suspend fun deleteAll()
}

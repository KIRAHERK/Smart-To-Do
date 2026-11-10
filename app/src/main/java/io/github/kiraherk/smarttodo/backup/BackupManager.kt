package io.github.kiraherk.smarttodo.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import io.github.kiraherk.smarttodo.BuildConfig
import io.github.kiraherk.smarttodo.data.db.ActivityEntity
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.ChatMessageEntity
import io.github.kiraherk.smarttodo.data.db.InboxEntity
import io.github.kiraherk.smarttodo.data.db.NoteEntity
import io.github.kiraherk.smarttodo.data.db.OccurrenceEntity
import io.github.kiraherk.smarttodo.data.db.TaskEntity
import io.github.kiraherk.smarttodo.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The backup file: plain JSON of every table, readable by any future version of the app. */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val appVersion: String = "",
    val dbVersion: Int = AppDatabase.VERSION,
    val createdAt: Long = 0,
    val tasks: List<TaskEntity> = emptyList(),
    val occurrences: List<OccurrenceEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val activity: List<ActivityEntity> = emptyList(),
    val inbox: List<InboxEntity> = emptyList(),
    val chat: List<ChatMessageEntity> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
    }
}

/**
 * Backups so his history can never be lost:
 *  - automatic daily copy inside the app (last 14 kept; included in Android's Google backup),
 *  - the same copy in a folder he chose (survives uninstalling the app),
 *  - an extra copy right before installing an update,
 *  - share / restore by hand from Settings.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val afterRestore: suspend () -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val dir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun export(): BackupFile = db.withTransaction {
        BackupFile(
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = System.currentTimeMillis(),
            tasks = db.tasks().all(),
            occurrences = db.occurrences().all(),
            notes = db.notes().all(),
            activity = db.activity().all(),
            inbox = db.inbox().all(),
            chat = db.chat().all(),
        )
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    /** Writes a backup now. Returns the file inside the app. */
    suspend fun backupNow(reason: String = "auto"): File = withContext(Dispatchers.IO) {
        val text = encode(export())
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val file = File(dir, "smarttodo-$stamp-$reason.json")
        file.writeText(text)
        dir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
        copyToChosenFolder(text)
        settings.setLastBackupAt(System.currentTimeMillis())
        file
    }

    suspend fun autoBackupIfDue() {
        val last = settings.current().lastBackupAt
        if (System.currentTimeMillis() - last > 20 * 3_600_000L) runCatching { backupNow("auto") }
    }

    fun latestFile(): File? = dir.listFiles { f -> f.name.endsWith(".json") }?.maxByOrNull { it.name }

    /** Replaces everything with the backup's content. A safety backup of the current data is made first. */
    suspend fun restore(text: String) = withContext(Dispatchers.IO) {
        val backup = decode(text)
        require(backup.format <= BackupFile.FORMAT) { "Backup is from a newer version of the app" }
        backupNow("before-restore")
        db.withTransaction {
            db.chat().deleteAll()
            db.inbox().deleteAll()
            db.activity().deleteAll()
            db.notes().deleteAll()
            db.occurrences().deleteAll()
            db.tasks().deleteAll()
            db.tasks().insertAll(backup.tasks)
            db.occurrences().insertAll(backup.occurrences)
            db.notes().insertAll(backup.notes)
            db.activity().insertAll(backup.activity)
            db.inbox().insertAll(backup.inbox)
            db.chat().insertAll(backup.chat)
        }
        afterRestore()
    }

    suspend fun restore(uri: Uri) {
        val text = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } ?: error("Can't read file")
        restore(text)
    }

    private suspend fun copyToChosenFolder(text: String) {
        val uri = settings.current().backupTreeUri.takeIf { it.isNotBlank() } ?: return
        runCatching {
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return
            val name = "smarttodo-backup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".json"
            val file = folder.findFile(name) ?: folder.createFile("application/json", name) ?: return
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    companion object {
        private const val KEEP = 14
    }
}

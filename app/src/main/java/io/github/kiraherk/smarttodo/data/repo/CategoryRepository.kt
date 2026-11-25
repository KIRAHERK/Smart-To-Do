package io.github.kiraherk.smarttodo.data.repo

import androidx.room.withTransaction
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.CategoryEntity
import io.github.kiraherk.smarttodo.domain.CategoryKey
import kotlinx.coroutines.flow.Flow

/** His categories: the four built-in ones (which he can rename or remove) and any he adds. */
class CategoryRepository(private val db: AppDatabase) {
    private val dao get() = db.categories()

    fun observe(): Flow<List<CategoryEntity>> = dao.observeAll()

    suspend fun all(): List<CategoryEntity> = dao.all()

    /** Puts the built-in categories back if there are none at all (e.g. after clearing the data). */
    suspend fun ensureDefaults() {
        if (dao.all().isEmpty()) dao.insertAll(DEFAULTS)
    }

    suspend fun add(name: String, colorIndex: Int, icon: String): String {
        val all = dao.all()
        var stamp = System.currentTimeMillis()
        while (all.any { it.key == "C" + stamp.toString(36).uppercase() }) stamp++
        val key = "C" + stamp.toString(36).uppercase()
        val order = (all.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.upsert(CategoryEntity(key, name.trim(), colorIndex, icon, order))
        return key
    }

    suspend fun update(category: CategoryEntity) = dao.upsert(category.copy(name = category.name.trim()))

    /** Removes a category; its tasks move to [moveTo] so nothing is lost. The last one can't be removed. */
    suspend fun delete(key: String, moveTo: String): Boolean {
        val all = dao.all()
        if (all.size <= 1 || key == moveTo || all.none { it.key == moveTo }) return false
        db.withTransaction {
            db.tasks().moveCategory(key, moveTo)
            dao.delete(key)
        }
        return true
    }

    /** A category key that exists: [key] if it does, else Personal, else the first one. */
    suspend fun resolve(key: String?): String {
        val all = dao.all()
        all.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }?.let { return it.key }
        return all.firstOrNull { it.key == CategoryKey.PERSONAL.name }?.key ?: all.firstOrNull()?.key ?: CategoryKey.PERSONAL.name
    }

    /** [key] if it exists, otherwise null. */
    suspend fun existing(key: String?): String? = key?.let { k -> dao.all().firstOrNull { it.key.equals(k.trim(), ignoreCase = true) }?.key }

    companion object {
        val DEFAULTS = listOf(
            CategoryEntity(CategoryKey.WORK.name, "", 0, "work", 0),
            CategoryEntity(CategoryKey.HOME.name, "", 1, "home", 1),
            CategoryEntity(CategoryKey.PERSONAL.name, "", 2, "person", 2),
            CategoryEntity(CategoryKey.TRAVEL.name, "", 3, "flight", 3),
        )
    }
}

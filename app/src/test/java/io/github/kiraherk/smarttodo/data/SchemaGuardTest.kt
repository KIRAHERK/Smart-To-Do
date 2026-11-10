package io.github.kiraherk.smarttodo.data

import io.github.kiraherk.smarttodo.data.db.AppDatabase
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Protects his data across updates: every database version must have its exported schema committed,
 * and there must be a migration for every step from version 1 up to the current version.
 */
class SchemaGuardTest {
    @Test
    fun everyVersionHasSchemaAndMigration() {
        val dir = File("schemas/io.github.kiraherk.smarttodo.data.db.AppDatabase")
        for (v in 1..AppDatabase.VERSION) {
            assertTrue("Missing committed schema $v.json in $dir", File(dir, "$v.json").exists())
        }
        val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until AppDatabase.VERSION) {
            assertTrue("Missing Migration $v -> ${v + 1} in AppDatabase.MIGRATIONS", (v to v + 1) in steps)
        }
    }
}

package io.github.kiraherk.smarttodo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A clock the tests can move forward. */
class MutableClock(var now: Instant, private val zoneId: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
    fun advance(d: Duration) { now = now.plus(d) }
}

fun testContext(): Context = ApplicationProvider.getApplicationContext()

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(testContext(), AppDatabase::class.java).allowMainThreadQueries().build()

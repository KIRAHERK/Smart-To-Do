package io.github.kiraherk.smarttodo

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kiraherk.smarttodo.ai.AiAction
import io.github.kiraherk.smarttodo.ai.AiException
import io.github.kiraherk.smarttodo.data.db.AppDatabase
import io.github.kiraherk.smarttodo.data.db.InboxEntity
import io.github.kiraherk.smarttodo.inbox.OfflineQueue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What he said offline is added by itself once the connection (or the AI) is back. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [29], application = Application::class)
class OfflineQueueTest {
    private lateinit var db: AppDatabase
    private val applied = mutableListOf<String>()
    private val answered = mutableListOf<String>()
    private val notified = mutableListOf<List<String>>()
    private var replies: (String) -> List<AiAction> = { emptyList() }

    private fun queue(hasKey: Boolean = true) = OfflineQueue(
        inbox = db.inbox(),
        hasKey = { hasKey },
        interpret = { replies(it) },
        apply = { actions, text -> applied += text; actions.size },
        answer = { answered += it },
        onAdded = { notified += it },
    )

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    private fun said(text: String, at: Long) = runBlocking { db.inbox().insert(InboxEntity(text = text, createdAt = at)) }

    @Test
    fun everythingWaitingIsAddedOldestFirstAndHeIsTold() = runBlocking {
        said("بكرة أروح للبنك", 2)
        said("ذكرني أتصل بأحمد", 1)
        said("شو عندي اليوم", 3)
        replies = { text ->
            if (text.startsWith("شو")) listOf(AiAction(type = "ask_question", question = text))
            else listOf(AiAction(type = "create_task", title = text))
        }
        val out = queue().processAll()!!
        assertEquals(listOf("ذكرني أتصل بأحمد", "بكرة أروح للبنك"), applied)
        assertEquals(listOf("شو عندي اليوم"), answered)
        assertEquals(3, out.added.size)
        assertEquals(1, notified.size)
        assertTrue(db.inbox().all().isEmpty())
        assertNull(out.retryInMs)
    }

    @Test
    fun whenTheAiIsRestingItStopsAndTriesAgainLater() = runBlocking {
        said("بكرة أروح للبنك", 1)
        said("ذكرني أتصل بأحمد", 2)
        replies = { throw AiException.Quota("m", "quota", retryAfterMs = 40_000) }
        val out = queue().processAll()!!
        assertEquals(40_000L, out.retryInMs)
        assertEquals(2, db.inbox().all().size)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun withoutInternetNothingIsLost() = runBlocking {
        said("بكرة أروح للبنك", 1)
        replies = { throw AiException.Network(java.io.IOException("no route")) }
        val out = queue().processAll()!!
        assertEquals(OfflineQueue.NETWORK_RETRY_MS, out.retryInMs)
        assertEquals("بكرة أروح للبنك", db.inbox().all().single().text)
    }

    @Test
    fun whatTheAiCannotUnderstandWaitsForHimAndIsNotRetriedForever() = runBlocking {
        said("ممممم", 1)
        replies = { listOf(AiAction(type = "unclear", spokenSummary = "ممكن تعيد؟")) }
        val out = queue().processAll()!!
        assertEquals(1, out.needsHim)
        assertEquals(OfflineQueue.NEEDS_HIM, db.inbox().all().single().state)
        // The next round leaves it alone (he can still press "Try now").
        assertNull(queue().processAll())
    }

    @Test
    fun withoutAKeyItWaits() = runBlocking {
        said("بكرة أروح للبنك", 1)
        assertNull(queue(hasKey = false).processAll())
        assertEquals(1, db.inbox().all().size)
    }
}

package app.binelik.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 1.3.4: the fixes from the code review of 9/11 (A: blocked messages, C: «Ξέχνα με», D: one memory line). */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class Release134Test {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A: a message the safety pause answered is never in the history that goes to the AI. */
    @Test fun aPausedUserMessageNeverGoesToTheAiAgain() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, Db::class.java).allowMainThreadQueries().build()
        val dao = db.messages()
        dao.add(Message(character = "kolitos", fromUser = true, text = "first"))
        dao.add(Message(character = "kolitos", fromUser = false, text = "reply"))
        // the phone's check stopped it: stored already marked
        dao.add(Message(character = "kolitos", fromUser = true, text = "blocked locally", pause = true))
        // the AI paused on it: marked afterwards
        val id = dao.add(Message(character = "kolitos", fromUser = true, text = "paused by the AI"))
        dao.markPause(id)
        dao.add(Message(character = "kolitos", fromUser = true, text = "next"))
        // the same filter as the chat, Live and the memory
        val toAi = dao.forCharacter("kolitos").first().filter { !it.pause }.map { it.text }
        assertEquals(listOf("first", "reply", "next"), toAi)
        db.close()
    }

    /** C: a memory update that was on its way when «Ξέχνα με» was pressed does not write anything back. */
    @Test fun forgetMeWinsOverAnUpdateOnItsWay() {
        val m = Memory(ctx)
        m.forgetAll()
        assertTrue(m.save("kolitos", listOf("the boss again")))
        val gen = m.generation("kolitos") // taken before asking the AI
        m.forget("kolitos")                // «Ξέχνα με» while the AI answers
        assertFalse(m.save("kolitos", listOf("the boss again", "traffic"), gen))
        assertEquals(emptyList<String>(), m.lines("kolitos"))
        // another character is not affected, and «forget everything» stops every pending update
        val other = m.generation("mana")
        assertTrue(m.save("mana", listOf("cooking"), other))
        val again = m.generation("mana")
        m.forgetAll()
        assertFalse(m.save("mana", listOf("cooking"), again))
        assertEquals(emptyList<String>(), m.lines("mana"))
    }

    /** D: one line can be deleted; an update on its way does not bring it back. */
    @Test fun oneMemoryLineCanBeDeleted() {
        val m = Memory(ctx)
        m.forgetAll()
        m.save("kolitos", listOf("the boss again", "traffic", "the neighbour's drill"))
        val gen = m.generation("kolitos")
        m.remove("kolitos", "traffic")
        assertEquals(listOf("the boss again", "the neighbour's drill"), m.lines("kolitos"))
        assertFalse(m.save("kolitos", listOf("traffic"), gen))
        assertEquals(listOf("the boss again", "the neighbour's drill"), m.lines("kolitos"))
    }
}

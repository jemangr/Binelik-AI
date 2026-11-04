package app.binelik.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35])
class AngerDbTest {
    @Test fun beforeAndAfter() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), Db::class.java).allowMainThreadQueries().build()
        val id = db.anger().add(Anger(character = "kavgatzis", before = 8))
        db.anger().setAfter(id, 3)
        val a = db.anger().since(0).single()
        assertEquals(8, a.before); assertEquals(3, a.after)
        db.close()
    }
}

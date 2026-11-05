package app.binelik

import app.binelik.extras.Report
import app.binelik.extras.Shake
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReportShakeTest {
    @Test fun payloadHasTheAppAndOnlyTheEmailTyped() {
        val j = JSONObject(Report.payload("it closed", " me@example.com "))
        assertEquals("binelik", j.getString("app"))
        assertEquals("me@example.com", j.getString("email"))
        assertEquals("", j.getString("website"))
        assertEquals("", JSONObject(Report.payload("x", "")).getString("email"))
    }

    @Test fun threeStrongShakesFireOnceThenRest() {
        val s = Shake {}
        listOf(1.0, 1.4, 2.0, 1.1).forEachIndexed { i, g -> assertFalse(s.feed(g, 100L * i)) }
        assertFalse(s.feed(3.0, 1000)); assertFalse(s.feed(3.2, 1300)); assertTrue(s.feed(3.1, 1600))
        assertFalse(s.feed(3.0, 2000)); assertFalse(s.feed(3.0, 2200)); assertFalse(s.feed(3.0, 2400))
    }
}

package app.binelik

import app.binelik.update.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesTest {
    @Test fun comparesVersions() {
        assertTrue(Updates.newer("v1.1.0", "1.0.0"))
        assertTrue(Updates.newer("1.10.0", "1.9.2"))
        assertTrue(Updates.newer("2.0", "1.9.9"))
        assertFalse(Updates.newer("1.0.0", "1.0.0"))
        assertFalse(Updates.newer("v1.0.0", "1.1.0"))
        assertFalse(Updates.newer("1.1.0-beta", "1.1.0"))
    }

    @Test fun readsTheLatestRelease() {
        val r = Updates.parse("""{"tag_name":"v1.1.0","html_url":"https://github.com/jemangr/Binelik-AI/releases/tag/v1.1.0","body":"Νέα","assets":[
            {"name":"BinelikAI-1.1.0.apk","browser_download_url":"https://x/BinelikAI-1.1.0.apk"},
            {"name":"BinelikAI.apk","browser_download_url":"https://x/BinelikAI.apk"}]}""")!!
        assertEquals("1.1.0", r.version)
        assertEquals("https://x/BinelikAI.apk", r.apk)
        assertEquals("Νέα", r.notes)
        assertEquals(Updates.APK, Updates.parse("""{"tag_name":"v2.0.0","assets":[]}""")!!.apk)
        assertNull(Updates.parse("""{"message":"Not Found"}"""))
        assertNull(Updates.parse("not json"))
    }
}

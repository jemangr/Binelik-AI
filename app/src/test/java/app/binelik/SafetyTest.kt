package app.binelik

import app.binelik.safety.Safety
import app.binelik.safety.SafetyHit
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyTest {
    private fun hit(s: String) = Safety.check(s)

    @Test fun suicideStopsAtOnceEvenAtAPlainMention() {
        listOf(
            "σκέφτομαι να αυτοκτονήσω", "Αυτοκτονία", "θέλω να πεθάνω", "δεν θέλω να ζω άλλο", "δε θέλω να ζω",
            "θα κόψω τις φλέβες μου, να κόψω τις φλέβες", "θα βάλω τέλος στη ζωή μου", "I want to die", "I'm going to kill myself",
            "ΘΕΛΩ ΝΑ ΠΕΘΑΝΩ", "αυτοτραυματισμός",
        ).forEach { assertEquals(it, SafetyHit.SELF_HARM, hit(it)) }
    }

    @Test fun laughingIsNotSelfHarm() {
        assertEquals(SafetyHit.NONE, hit("θα πεθάνω από τα γέλια ρε"))
    }

    @Test fun ordinaryAngerCarriesOn() {
        listOf(
            "θα τον σκοτώσω τον μαλάκα", "θα τη σκοτώσω αν το ξανακάνει", "μου έρχεται να τον πνίξω",
            "άντε γαμήσου, μαλάκα", "ο προϊστάμενος είναι ηλίθιος", "",
        ).forEach { assertEquals(it, SafetyHit.NONE, hit(it)) }
    }

    @Test fun seriousIntentStops() {
        listOf(
            "θα τον σκοτώσω, και το εννοώ", "έχω όπλο και θα τον σκοτώσω", "θα τον μαχαιρώσω απόψε",
            "ξέρω πού μένει, θα τον σκοτώσω", "θα τον σκοτώσω αύριο το πρωί στις 8", "I have a gun and I will kill him",
        ).forEach { assertEquals(it, SafetyHit.VIOLENCE_INTENT, hit(it)) }
    }

    @Test fun normalizeDropsAccentsAndFinalSigma() {
        assertEquals("αυτοκτονια σκεψεισ", Safety.normalize("Αυτοκτονία, σκέψεις!"))
    }
}

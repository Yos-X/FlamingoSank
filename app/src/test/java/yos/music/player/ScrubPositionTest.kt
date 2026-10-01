package yos.music.player

import org.junit.Assert.assertEquals
import org.junit.Test
import yos.music.player.ui.pages.scrubbedPosition

class ScrubPositionTest {
    @Test
    fun noMovementKeepsTheStartPosition() {
        assertEquals(42_000f, scrubbedPosition(42_000f, 0f, 1000, 200_000f), 0.001f)
    }

    @Test
    fun movesRelativeToTheStartPosition() {
        // Half the track width is half the duration, whatever the start was
        assertEquals(142_000f, scrubbedPosition(42_000f, 500f, 1000, 200_000f), 0.001f)
        assertEquals(0f, scrubbedPosition(42_000f, -500f, 1000, 200_000f), 0.001f)
    }

    @Test
    fun staysInsideTheTrack() {
        assertEquals(200_000f, scrubbedPosition(150_000f, 900f, 1000, 200_000f), 0.001f)
        assertEquals(0f, scrubbedPosition(10_000f, -900f, 1000, 200_000f), 0.001f)
    }

    @Test
    fun ignoresUnknownSizeOrDuration() {
        assertEquals(5_000f, scrubbedPosition(5_000f, 300f, 0, 200_000f), 0.001f)
        assertEquals(5_000f, scrubbedPosition(5_000f, 300f, 1000, 0f), 0.001f)
    }
}

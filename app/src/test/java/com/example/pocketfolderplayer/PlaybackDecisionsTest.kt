package com.example.pocketfolderplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDecisionsTest {
    @Test fun restoresTheExistingSong() {
        assertEquals(2, PlaybackDecisions.restoreIndex(5, 2, false, 3))
    }

    @Test fun selectsTheNextSongWhenSavedSongWasRemoved() {
        assertEquals(2, PlaybackDecisions.restoreIndex(4, -1, true, 2))
    }

    @Test fun wrapsAfterRemovingTheLastSong() {
        assertEquals(0, PlaybackDecisions.restoreIndex(3, -1, true, 3))
    }

    @Test fun selectsFirstSongWithNoPreviousSession() {
        assertEquals(0, PlaybackDecisions.restoreIndex(3, -1, false, -1))
    }

    @Test fun handlesAnEmptyPlaylist() {
        assertEquals(-1, PlaybackDecisions.restoreIndex(0, -1, true, 10))
    }

    @Test fun skipsConsecutiveFailures() {
        val next = PlaybackDecisions.nextPlayableIndex(
            currentIndex = 0,
            playlistSize = 4,
            nextIndex = { if (it == 3) 0 else it + 1 },
            isFailed = { it == 1 || it == 2 }
        )
        assertEquals(3, next)
    }

    @Test fun stopsAtPlaylistEndWithRepeatOff() {
        assertEquals(-1, PlaybackDecisions.nextPlayableIndex(
            currentIndex = 2,
            playlistSize = 3,
            nextIndex = { if (it == 2) -1 else it + 1 },
            isFailed = { false }
        ))
    }

    @Test fun stopsIfEveryTrackFails() {
        assertEquals(-1, PlaybackDecisions.nextPlayableIndex(
            currentIndex = 0,
            playlistSize = 3,
            nextIndex = { (it + 1) % 3 },
            isFailed = { true }
        ))
    }

    @Test fun avoidsShortShuffleCycles() {
        assertEquals(-1, PlaybackDecisions.nextPlayableIndex(
            currentIndex = 1,
            playlistSize = 4,
            nextIndex = { if (it == 1) 2 else 1 },
            isFailed = { true }
        ))
    }
}

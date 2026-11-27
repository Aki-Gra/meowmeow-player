package com.example.pocketfolderplayer

/** Pure playlist decisions kept independent of Android for repeatable JVM tests. */
internal object PlaybackDecisions {
    /** Prefer an existing saved track; otherwise fall forward after its removal. */
    fun restoreIndex(
        playlistSize: Int,
        foundIndex: Int,
        savedTrackMissing: Boolean,
        previousIndex: Int
    ): Int {
        if (playlistSize <= 0) return -1
        if (foundIndex in 0 until playlistSize) return foundIndex
        return if (savedTrackMissing && previousIndex >= 0) previousIndex % playlistSize else 0
    }

    /**
     * Walk the playback order at most once. nextIndex returns -1 at the end.
     * Handles shuffle/repeat order from the caller, including short cycles.
     */
    fun nextPlayableIndex(
        currentIndex: Int,
        playlistSize: Int,
        nextIndex: (Int) -> Int,
        isFailed: (Int) -> Boolean
    ): Int {
        if (currentIndex !in 0 until playlistSize) return -1
        var position = currentIndex
        repeat(playlistSize) {
            position = nextIndex(position)
            if (position !in 0 until playlistSize || position == currentIndex) return -1
            if (!isFailed(position)) return position
        }
        return -1
    }
}

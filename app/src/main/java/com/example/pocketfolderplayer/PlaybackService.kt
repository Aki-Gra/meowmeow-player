package com.example.pocketfolderplayer

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** MediaSessionService owns playback, so music keeps playing in the background. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    lateinit var equalizer: EqualizerEngine
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val failedTracks = mutableSetOf<String>()
    private var recoveringFromError = false

    var noPlayableTracks by mutableStateOf(false)
        private set

    private val savePosition = object : Runnable {
        override fun run() {
            persistPlayback()
            handler.postDelayed(this, 3_000L)
        }
    }

    companion object {
        // Activity and service run in the same app process. No player instance is held by the Activity.
        var active: PlaybackService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        equalizer = EqualizerEngine(this)
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.repeatMode = FolderStore.repeatMode(this)
        player.shuffleModeEnabled = FolderStore.shuffleEnabled(this)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                equalizer.attach(audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    failedTracks.clear()
                    noPlayableTracks = false
                }
                persistPlayback()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Keep failed-track history until the playlist is refreshed.
                // Some damaged files briefly enter a playing state before failing.
                persistPlayback()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) persistPlayback()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // Save immediately when seeking or changing tracks.
                persistPlayback()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Media3 enters STATE_IDLE immediately after this callback.
                // Recover on the next main-loop turn, once that transition completes.
                handler.post {
                    if (active === this@PlaybackService) skipUnplayableTrack()
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                FolderStore.saveShuffle(this@PlaybackService, shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                FolderStore.saveRepeat(this@PlaybackService, repeatMode)
            }
        })
        val activityIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
        active = this
        handler.postDelayed(savePosition, 3_000L)
    }

    /**
     * Try subsequent tracks in the playlist and stop after exhausting candidates.
     * Keep shuffle order and the user's Repeat Off / All semantics.
     * For an error only, Repeat One may advance so a broken file cannot loop.
     */
    private fun skipUnplayableTrack() {
        if (recoveringFromError) return
        recoveringFromError = true
        try {
            val badIndex = player.currentMediaItemIndex
            val badItem = player.currentMediaItem
            val badId = badItem?.mediaId?.ifEmpty { "index:$badIndex" } ?: "index:$badIndex"
            failedTracks.add(badId)
            val requestedPlayback = player.playWhenReady
            val name = badItem?.mediaMetadata?.title?.toString() ?: "a song"
            val count = player.mediaItemCount

            // On paused restoration, search beyond the end if necessary.
            // During active playback, Repeat Off must still stop at the end.
            val skipRepeat = if (requestedPlayback &&
                player.repeatMode == Player.REPEAT_MODE_OFF
            ) {
                Player.REPEAT_MODE_OFF
            } else {
                Player.REPEAT_MODE_ALL
            }
            val timeline = player.currentTimeline
            var next = if (badIndex in 0 until count) {
                timeline.getNextWindowIndex(badIndex, skipRepeat, player.shuffleModeEnabled)
            } else {
                C.INDEX_UNSET
            }
            var inspected = 0
            while (next != C.INDEX_UNSET && inspected < count) {
                val item = player.getMediaItemAt(next)
                val id = item.mediaId.ifEmpty { "index:$next" }
                if (id !in failedTracks) break
                next = timeline.getNextWindowIndex(next, skipRepeat, player.shuffleModeEnabled)
                inspected++
            }

            if (next == C.INDEX_UNSET || inspected >= count || failedTracks.size >= count) {
                val allFailed = count > 0 && failedTracks.size >= count
                player.stop()
                noPlayableTracks = allFailed
                val message = if (allFailed) {
                    "No playable songs remain in this folder."
                } else {
                    "Couldn't play \"$name\". Reached the end of the playlist."
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                return
            }

            player.seekTo(next, 0L)
            player.prepare()
            if (requestedPlayback) player.play() else player.pause()
            Toast.makeText(this, "Skipped \"$name\".", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            player.stop()
            Toast.makeText(this, "Unable to play this song. Playback stopped.", Toast.LENGTH_SHORT).show()
        } finally {
            recoveringFromError = false
        }
    }

    private fun persistPlayback() {
        val item = player.currentMediaItem ?: return
        if (item.mediaId.isNotEmpty()) {
            FolderStore.savePlayback(
                this, item.mediaId, player.currentPosition, player.currentMediaItemIndex
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        persistPlayback()
        active = null
        equalizer.release()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}

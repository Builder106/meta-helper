package com.metahelper.app

import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    var onReplayRequested: (() -> Unit)? = null

    init {
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(context, "MetaHelperAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: android.content.Intent): Boolean {
                    val keyEvent = mediaButtonIntent.getParcelableExtra<android.view.KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
                    if (keyEvent?.action == android.view.KeyEvent.ACTION_DOWN) {
                        if (keyEvent.keyCode == android.view.KeyEvent.KEYCODE_MEDIA_NEXT) {
                            Log.d("AudioPlayer", "Double tap detected on glasses! Replay requested.")
                            onReplayRequested?.invoke()
                            return true
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onSkipToNext() {
                    Log.d("AudioPlayer", "SkipNext detected! Replaying audio...")
                    onReplayRequested?.invoke()
                }
            })
            isActive = true
        }
    }

    fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit = {}) {
        try {
            stop()
            val tempFile = File.createTempFile("response", "mp3", context.cacheDir)
            tempFile.deleteOnExit()
            val fos = FileOutputStream(tempFile)
            fos.write(audioBytes)
            fos.close()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    onComplete()
                    tempFile.delete()
                    stop()
                }
                start()
            }
            Log.d("AudioPlayer", "Started playing audio response")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio: ${e.message}")
        }
    }

    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}


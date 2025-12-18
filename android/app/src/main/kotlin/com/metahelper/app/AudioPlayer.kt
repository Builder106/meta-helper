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
        Log.d("AudioPlayer", "Attempting to play ${audioBytes.size} bytes of audio")
        try {
            stop()
            val tempFile = File.createTempFile("response", "mp3", context.cacheDir)
            Log.d("AudioPlayer", "Created temp file: ${tempFile.absolutePath}")
            tempFile.deleteOnExit()
            val fos = FileOutputStream(tempFile)
            fos.write(audioBytes)
            fos.close()
            Log.d("AudioPlayer", "Audio data written to temp file")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                Log.d("AudioPlayer", "DataSource set. Preparing...")
                prepare()
                Log.d("AudioPlayer", "Player prepared. Starting playback...")
                setOnCompletionListener {
                    Log.d("AudioPlayer", "Playback completed naturally")
                    onComplete()
                    tempFile.delete()
                    stop()
                }
                start()
            }
            Log.d("AudioPlayer", "MediaPlayer.start() called successfully")
        } catch (e: Exception) {
            val errorMsg = "Error playing audio: ${e.message}"
            Log.e("AudioPlayer", errorMsg)
            e.printStackTrace()
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


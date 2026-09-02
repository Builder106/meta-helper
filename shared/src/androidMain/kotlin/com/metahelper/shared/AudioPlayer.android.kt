package com.metahelper.shared

import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

internal class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    override var onReplayRequested: (() -> Unit)? = null

    init {
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(context, "MetaHelperAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: android.content.Intent): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(android.content.Intent.EXTRA_KEY_EVENT)
                    }

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

    override fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit) {
        Log.d("AudioPlayer", "Attempting to play ${audioBytes.size} bytes of audio")
        try {
            stop()

            val responseFile = File(context.cacheDir, "latest_answer.mp3")
            if (responseFile.exists()) {
                responseFile.delete()
            }

            val fos = FileOutputStream(responseFile)
            fos.write(audioBytes)
            fos.close()
            Log.d("AudioPlayer", "Audio data written to: ${responseFile.absolutePath}")

            mediaPlayer = MediaPlayer().apply {
                setDataSource(responseFile.absolutePath)
                setOnPreparedListener {
                    Log.d("AudioPlayer", "Player prepared. Starting playback...")
                    it.start()
                }
                setOnCompletionListener {
                    Log.d("AudioPlayer", "Playback completed naturally")
                    onComplete()
                    stop()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("AudioPlayer", "MediaPlayer error: what=$what extra=$extra")
                    onComplete()
                    stop()
                    true
                }
                Log.d("AudioPlayer", "DataSource set. Preparing asynchronously...")
                prepareAsync()
            }
            Log.d("AudioPlayer", "MediaPlayer.prepareAsync() called")
        } catch (e: Exception) {
            val errorMsg = "Error playing audio: ${e.message}"
            Log.e("AudioPlayer", errorMsg)
            e.printStackTrace()
        }
    }

    override fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }

    override fun release() {
        stop()
        mediaSession?.isActive = false
        mediaSession?.release()
    }
}

actual fun createAudioPlayer(context: Any): AudioPlayer {
    return AndroidAudioPlayer(context as Context)
}

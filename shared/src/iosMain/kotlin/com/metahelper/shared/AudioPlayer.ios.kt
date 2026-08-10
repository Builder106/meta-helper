package com.metahelper.shared

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSArray
import platform.Foundation.NSURL
import platform.Foundation.NSString
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSFileManager
import platform.AVFoundation.AVAudioPlayer
import platform.AVFoundation.AVAudioSession
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.MPRemoteCommandCenter
import platform.AVFoundation.MPNowPlayingInfoCenter

internal class IosAudioPlayer : AudioPlayer {
    private var audioPlayer: AVAudioPlayer? = null
    private var remoteCommandCenter = MPRemoteCommandCenter.shared()
    override var onReplayRequested: (() -> Unit)? = null

    init {
        setupAudioSession()
        setupRemoteCommands()
    }

    private fun setupAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback)
            audioSession.setActive(true)
        } catch (e: Exception) {
            println("Audio session setup failed: ${e.message}")
        }
    }

    private fun setupRemoteCommands() {
        remoteCommandCenter.nextTrackCommand.isEnabled = true
        remoteCommandCenter.nextTrackCommand.addTarget { event ->
            onReplayRequested?.invoke()
            MPRemoteCommandCenter.MPRemoteCommandSuccess
        }
    }

    override fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit = {}) {
        println("IosAudioPlayer: Attempting to play ${audioBytes.size} bytes of audio")

        // Write audio bytes to a temporary file
        val cacheDir = getCacheDirectory()
        val audioFile = cacheDir.resolve("latest_answer.mp3")

        try {
            NSFileManager.defaultManager().createFileAtPath(
                audioFile.toString(),
                audioBytes,
                true
            )

            val fileUrl = NSURL.fileURLWithPath(audioFile.toString())
            audioPlayer = AVAudioPlayer(fileUrl)

            audioPlayer?.delegate = object : AVAudioPlayerDelegate {
                override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer?, successfully: Boolean) {
                    println("IosAudioPlayer: Playback completed")
                    onComplete()
                    stop()
                }

                override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer?, error: platform.Foundation.NSError?) {
                    println("IosAudioPlayer: Decode error: ${error?.localizedDescription}")
                    onComplete()
                    stop()
                }
            }

            audioPlayer?.prepareToPlay()
            audioPlayer?.play()
            println("IosAudioPlayer: Started playback")

        } catch (e: Exception) {
            println("IosAudioPlayer: Error playing audio: ${e.message}")
            onComplete()
        }
    }

    override fun stop() {
        audioPlayer?.stop()
        audioPlayer = null
    }

    override fun release() {
        stop()
        remoteCommandCenter.nextTrackCommand.removeTarget(nil)
    }

    private fun getCacheDirectory(): NSString {
        val paths = NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSCachesDirectory,
            platform.Foundation.NSUserDomainMask,
            true
        )
        return paths[0] as NSString
    }
}

actual fun createAudioPlayer(context: Any): AudioPlayer {
    return IosAudioPlayer()
}
package com.metahelper.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class IosAudioPlayer : AudioPlayer {
    private var audioPlayer: AVAudioPlayer? = null
    private var audioPlayerDelegate: AudioPlayerDelegate? = null
    private var remoteCommandCenter = MPRemoteCommandCenter.sharedCommandCenter()
    override var onReplayRequested: (() -> Unit)? = null

    init {
        setupAudioSession()
        setupRemoteCommands()
    }

    private fun setupAudioSession() {
        try {
            val audioSession = AVAudioSession.sharedInstance()
            audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
            audioSession.setActive(true, error = null)
        } catch (e: Exception) {
            println("Audio session setup failed: ${e.message}")
        }
    }

    private fun setupRemoteCommands() {
        remoteCommandCenter.nextTrackCommand.enabled = true
        remoteCommandCenter.nextTrackCommand.addTargetWithHandler { _ ->
            onReplayRequested?.invoke()
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    override fun playAudio(audioBytes: ByteArray, onComplete: () -> Unit) {
        println("IosAudioPlayer: Attempting to play ${audioBytes.size} bytes of audio")

        // Write audio bytes to a temporary file
        val audioFilePath = getCacheDirectory() + "latest_answer.mp3"

        try {
            val nsData = audioBytes.toNSData()
            NSFileManager.defaultManager.createFileAtPath(audioFilePath, nsData, null)

            val fileUrl = NSURL.fileURLWithPath(audioFilePath)
            val player = AVAudioPlayer(fileUrl, error = null)
            audioPlayer = player

            val delegate = AudioPlayerDelegate(
                onFinish = {
                    println("IosAudioPlayer: Playback completed")
                    onComplete()
                    stop()
                },
                onDecodeError = { error ->
                    println("IosAudioPlayer: Decode error: ${error?.localizedDescription}")
                    onComplete()
                    stop()
                }
            )
            audioPlayerDelegate = delegate
            player.delegate = delegate

            player.prepareToPlay()
            player.play()
            println("IosAudioPlayer: Started playback")

        } catch (e: Exception) {
            println("IosAudioPlayer: Error playing audio: ${e.message}")
            onComplete()
        }
    }

    override fun stop() {
        audioPlayer?.stop()
        audioPlayer = null
        audioPlayerDelegate = null
    }

    override fun release() {
        stop()
        remoteCommandCenter.nextTrackCommand.removeTarget(null)
    }

    private fun getCacheDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        )
        val dir = paths[0] as String
        return dir + "/"
    }
}

@OptIn(ExperimentalForeignApi::class)
private class AudioPlayerDelegate(
    private val onFinish: () -> Unit,
    private val onDecodeError: (NSError?) -> Unit
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinish()
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
        onDecodeError(error)
    }
}

actual fun createAudioPlayer(context: Any): AudioPlayer {
    return IosAudioPlayer()
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

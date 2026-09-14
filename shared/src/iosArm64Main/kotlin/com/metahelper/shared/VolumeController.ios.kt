package com.metahelper.shared

internal class IosVolumeController : VolumeController {
    override fun setQuietVolume() {
        println("IosVolumeController: setQuietVolume (no-op on iOS)")
    }

    override fun restoreVolume() {
        println("IosVolumeController: restoreVolume (no-op on iOS)")
    }
}

actual fun createVolumeController(context: PlatformContext): VolumeController {
    return IosVolumeController()
}
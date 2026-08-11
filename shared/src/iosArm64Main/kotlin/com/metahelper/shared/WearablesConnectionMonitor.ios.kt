package com.metahelper.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class IosWearablesConnectionMonitor : WearablesConnectionMonitor {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()
    private var isMonitoring = false

    override fun start() {
        if (isMonitoring) return
        println("IosWearablesConnectionMonitor: Starting (stub implementation - MWDAT not available via CocoaPods)")

        // Stub implementation - in a real scenario, MWDAT would be integrated
        // For now, we simulate a disconnected state
        _connectionState.value = ConnectionState.Disconnected
        isMonitoring = true
    }

    override fun stop() {
        println("IosWearablesConnectionMonitor: Stopping (stub)")
        _connectionState.value = ConnectionState.Disconnected
        isMonitoring = false
    }
}

actual fun createWearablesConnectionMonitor(context: Any): WearablesConnectionMonitor {
    return IosWearablesConnectionMonitor()
}
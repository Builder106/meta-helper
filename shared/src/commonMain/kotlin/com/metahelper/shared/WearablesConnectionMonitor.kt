package com.metahelper.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for monitoring Meta Wearables SDK connection state.
 * Android implementation uses mwdat-core; iOS implementation can use mwdat-ios when available.
 */
interface WearablesConnectionMonitor {
    val connectionState: StateFlow<ConnectionState>

    fun start()
    fun stop()
}

sealed interface ConnectionState {
    data class Connected(val applicationId: String) : ConnectionState
    object Disconnected : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Factory for creating platform-specific WearablesConnectionMonitor implementations.
 */
actual fun createWearablesConnectionMonitor(context: Any): WearablesConnectionMonitor
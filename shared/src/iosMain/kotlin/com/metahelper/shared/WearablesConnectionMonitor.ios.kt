package com.metahelper.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.MWDAT.MWDATWearables
import platform.MWDAT.MWDATRegistrationState

internal class IosWearablesConnectionMonitor : WearablesConnectionMonitor {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()
    private var isMonitoring = false

    override fun start() {
        if (isMonitoring) return
        println("IosWearablesConnectionMonitor: Starting MWDAT Wearables SDK...")

        // Initialize MWDAT Wearables
        MWDATWearables.shared().initialize { success, error ->
            if (success) {
                println("IosWearablesConnectionMonitor: MWDAT Wearables initialized successfully")
                observeRegistrationState()
            } else {
                val errorMsg = error?.localizedDescription ?: "Unknown error"
                println("IosWearablesConnectionMonitor: MWDAT Wearables initialization failed: $errorMsg")
                _connectionState.value = ConnectionState.Error(errorMsg)
            }
        }
        isMonitoring = true
    }

    private fun observeRegistrationState() {
        // Observe registration state changes
        MWDATWearables.shared().registrationState.addObserver { state ->
            when (state) {
                is MWDATRegistrationState.Registered -> {
                    val appId = state.applicationId
                    println("IosWearablesConnectionMonitor: Glasses registered with Application ID: $appId")
                    _connectionState.value = ConnectionState.Connected(appId)
                }
                is MWDATRegistrationState.Unregistered -> {
                    println("IosWearablesConnectionMonitor: Glasses unregistered")
                    _connectionState.value = ConnectionState.Disconnected
                }
                is MWDATRegistrationState.Error -> {
                    val errorMsg = state.error?.localizedDescription ?: "Unknown error"
                    println("IosWearablesConnectionMonitor: Registration error: $errorMsg")
                    _connectionState.value = ConnectionState.Error(errorMsg)
                }
            }
        }
    }

    override fun stop() {
        println("IosWearablesConnectionMonitor: Stopping MWDAT Wearables SDK...")
        MWDATWearables.shared().deinitialize()
        isMonitoring = false
    }
}

actual fun createWearablesConnectionMonitor(context: Any): WearablesConnectionMonitor {
    return IosWearablesConnectionMonitor()
}
package com.metahelper.shared

import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidWearablesConnectionMonitor(
    private val context: android.content.Context
) : WearablesConnectionMonitor {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun start() {
        scope.launch {
            Wearables.registrationState.collect { state ->
                when (state) {
                    is RegistrationState.Registered -> {
                        _connectionState.value = ConnectionState.Connected(state.applicationId)
                    }
                    is RegistrationState.Unregistered -> {
                        _connectionState.value = ConnectionState.Disconnected
                    }
                    is RegistrationState.Error -> {
                        _connectionState.value = ConnectionState.Error(state.exception?.message ?: "Unknown error")
                    }
                }
            }
        }
    }

    override fun stop() {
        scope.cancel()
    }
}

actual fun createWearablesConnectionMonitor(context: Any): WearablesConnectionMonitor {
    return AndroidWearablesConnectionMonitor(context as android.content.Context)
}
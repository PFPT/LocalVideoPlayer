package com.zhaoshuo.localvideoplayer.cast

data class CastDevice(
    val id: String,
    val name: String,
    val description: String = "",
)

sealed interface CastUiState {
    data object Idle : CastUiState
    data object Searching : CastUiState
    data class Devices(val devices: List<CastDevice>) : CastUiState
    data class Connecting(val device: CastDevice) : CastUiState
    data class Connected(
        val device: CastDevice,
        val title: String,
    ) : CastUiState
    data class Error(val message: String) : CastUiState
}

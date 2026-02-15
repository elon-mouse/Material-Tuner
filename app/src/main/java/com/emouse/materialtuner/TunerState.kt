package com.emouse.materialtuner

data class TunerState(
    val note: String = "--",
    val hz: Double = 0.0,
    val cents: Float = 0f,
    val isListening: Boolean = false,
    val hasMicPermission: Boolean = false
)

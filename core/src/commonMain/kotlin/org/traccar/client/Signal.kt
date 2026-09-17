package org.traccar.client

sealed interface Signal {
    data class MotionChanged(val activity: MotionActivity) : Signal
    object StationaryEnter : Signal
    object StationaryExit : Signal
    object HeartbeatTick : Signal
}

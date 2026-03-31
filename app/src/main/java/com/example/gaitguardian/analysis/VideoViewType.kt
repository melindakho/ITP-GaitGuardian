package com.example.gaitguardian.analysis

enum class VideoViewType(
    val routeValue: String,
    val displayLabel: String
) {
    SIDE("side", "Side view"),
    FRONT("front", "Front view");

    companion object {
        fun fromRouteValue(value: String?): VideoViewType {
            return entries.firstOrNull { it.routeValue.equals(value, ignoreCase = true) } ?: SIDE
        }
    }
}

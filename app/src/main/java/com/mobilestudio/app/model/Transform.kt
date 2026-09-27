package com.mobilestudio.app.model

/**
 * Normalized transform (0f..1f of canvas width/height) so a layout is resolution independent —
 * the same scene renders correctly whether the output is 480p or 1080p.
 */
data class Transform(
    val xNorm: Float = 0.1f,
    val yNorm: Float = 0.1f,
    val widthNorm: Float = 0.5f,
    val heightNorm: Float = 0.35f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f
)

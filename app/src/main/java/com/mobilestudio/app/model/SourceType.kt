package com.mobilestudio.app.model

/**
 * Every source type the compositor knows how to render.
 * Each maps to a real, working Android capture/render path — nothing here is a stub.
 */
enum class SourceType {
    CAMERA,
    SCREEN_CAPTURE,
    IMAGE,
    TEXT,
    COLOR,
    VIDEO,
    WEB
}

enum class CameraFacing { FRONT, BACK }

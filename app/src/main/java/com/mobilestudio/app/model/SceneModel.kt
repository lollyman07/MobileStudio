package com.mobilestudio.app.model

import java.util.UUID

data class SceneModel(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var order: Int = 0,
    val sources: MutableList<SourceModel> = mutableListOf()
)

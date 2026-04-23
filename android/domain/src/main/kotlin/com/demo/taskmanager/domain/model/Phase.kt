package com.demo.taskmanager.domain.model

data class Phase(
    val id: String,
    val name: TaskPhaseName,
    val description: String?,
    val customName: String?,
    val projectId: String,
)

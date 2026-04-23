package com.demo.taskmanager.domain.model

data class Project(
    val id: String,
    val name: String,
    val description: String?,
    /** Prefix prepended to auto-generated task codes, e.g. "PROJ_". */
    val taskCodePrefix: String?,
    val defaultPhaseId: String?,
)

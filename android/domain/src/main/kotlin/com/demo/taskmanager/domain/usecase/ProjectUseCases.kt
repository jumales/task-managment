package com.demo.taskmanager.domain.usecase

import com.demo.taskmanager.domain.model.Project

/** Returns all projects available to the current user. */
fun interface GetProjectsUseCase {
    suspend operator fun invoke(): List<Project>
}

/** Returns project detail by ID. */
fun interface GetProjectUseCase {
    suspend operator fun invoke(id: String): Project
}

package com.demo.taskmanager.domain.usecase

import com.demo.taskmanager.domain.model.User

/** Returns all users visible to the current user. */
fun interface GetUsersUseCase {
    suspend operator fun invoke(): List<User>
}

/** Returns user detail by ID. */
fun interface GetUserUseCase {
    suspend operator fun invoke(id: String): User
}

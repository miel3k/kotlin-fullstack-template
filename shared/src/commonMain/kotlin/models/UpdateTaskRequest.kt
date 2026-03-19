package models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTaskRequest(
    val completed: Boolean,
)

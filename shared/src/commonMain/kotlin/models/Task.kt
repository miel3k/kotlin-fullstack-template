package models

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String? = null,
    val completed: Boolean = false,
    val createdAt: Long,
)

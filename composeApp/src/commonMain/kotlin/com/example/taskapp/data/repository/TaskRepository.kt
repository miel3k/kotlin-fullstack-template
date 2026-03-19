package com.example.taskapp.data.repository

import com.example.taskapp.data.Result
import kotlinx.coroutines.flow.Flow
import models.CreateTaskRequest
import models.Task

interface TaskRepository {
    suspend fun getTasks(): Result<List<Task>>
    fun observeTasks(): Flow<List<Task>>
    suspend fun createTask(request: CreateTaskRequest): Result<Task>
    suspend fun updateTask(id: String, completed: Boolean): Result<Task>
    suspend fun deleteTask(id: String): Result<Unit>
}

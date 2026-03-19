package com.example.taskapp.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.example.taskapp.data.Result
import com.example.taskapp.data.datasource.TaskDataSource
import com.example.taskapp.database.TaskDatabase
import com.example.taskapp.database.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import models.CreateTaskRequest
import models.Task
import models.UpdateTaskRequest

class DefaultTaskRepository(
    private val api: TaskDataSource,
    private val database: TaskDatabase
) : TaskRepository {

    override suspend fun getTasks(): Result<List<Task>> {
        return when (val result = api.getTasks()) {
            is Result.Success -> {
                result.data.forEach { task ->
                    database.taskQueries.insert(
                        id = task.id,
                        title = task.title,
                        description = task.description,
                        completed = if (task.completed) 1L else 0L,
                        createdAt = task.createdAt
                    )
                }
                result
            }

            is Result.Error -> {
                val cached = database.taskQueries.selectAll()
                    .executeAsList()
                    .map { it.toTask() }
                Result.Success(cached)
            }
        }
    }

    override fun observeTasks(): Flow<List<Task>> {
        return database.taskQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toTask() } }
    }

    override suspend fun createTask(request: CreateTaskRequest): Result<Task> {
        return when (val result = api.createTask(request)) {
            is Result.Success -> {
                val task = result.data
                database.taskQueries.insert(
                    id = task.id,
                    title = task.title,
                    description = task.description,
                    completed = if (task.completed) 1L else 0L,
                    createdAt = task.createdAt
                )
                result
            }

            is Result.Error -> result
        }
    }

    override suspend fun updateTask(
        id: String,
        completed: Boolean
    ): Result<Task> {
        val request = UpdateTaskRequest(completed)
        return when (val result = api.updateTask(id, request)) {
            is Result.Success -> {
                database.taskQueries.updateCompleted(if (completed) 1L else 0L, id)
                result
            }

            is Result.Error -> result
        }
    }

    override suspend fun deleteTask(id: String): Result<Unit> {
        return when (val result = api.deleteTask(id)) {
            is Result.Success -> {
                database.taskQueries.delete(id)
                result
            }

            is Result.Error -> result
        }
    }

    private fun TaskEntity.toTask() = Task(
        id = id,
        title = title,
        description = description,
        completed = completed == 1L,
        createdAt = createdAt
    )
}
package com.example.taskapp.data.datasource

import com.example.taskapp.data.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import models.CreateTaskRequest
import models.Task
import models.UpdateTaskRequest

class TaskDataSource(private val client: HttpClient) {

    suspend fun getTasks(): Result<List<Task>> {
        return try {
            val tasks = client.get("tasks").body<List<Task>>()
            Result.Success(tasks)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun createTask(request: CreateTaskRequest): Result<Task> {
        return try {
            val task = client.post("tasks") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<Task>()
            Result.Success(task)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateTask(id: String, request: UpdateTaskRequest): Result<Task> {
        return try {
            val task = client.put("tasks/$id") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<Task>()
            Result.Success(task)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteTask(id: String): Result<Unit> {
        return try {
            client.delete("tasks/$id")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

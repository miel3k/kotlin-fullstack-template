package repository

import database.TaskTable
import models.CreateTaskRequest
import models.Task
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

class TaskRepository {

    fun getAll(): List<Task> = transaction {
        TaskTable
            .selectAll()
            .orderBy(TaskTable.createdAt to SortOrder.DESC)
            .map { it.toTask() }
    }

    fun getById(taskId: String): Task? = transaction {
        TaskTable
            .selectAll()
            .where { TaskTable.id eq taskId }
            .map { it.toTask() }
            .singleOrNull()
    }

    fun create(request: CreateTaskRequest): Task = transaction {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        TaskTable.insert {
            it[TaskTable.id] = id
            it[TaskTable.title] = request.title
            it[TaskTable.description] = request.description
            it[TaskTable.completed] = false
            it[TaskTable.createdAt] = now
        }

        Task(
            id = id,
            title = request.title,
            description = request.description,
            completed = false,
            createdAt = now
        )
    }

    fun update(taskId: String, completed: Boolean): Task? = transaction {
        TaskTable.update({ TaskTable.id eq taskId }) {
            it[TaskTable.completed] = completed
        }

        getById(taskId)
    }

    fun delete(taskId: String): Boolean = transaction {
        TaskTable.deleteWhere { TaskTable.id eq taskId } > 0
    }

    private fun ResultRow.toTask() = Task(
        id = this[TaskTable.id],
        title = this[TaskTable.title],
        description = this[TaskTable.description],
        completed = this[TaskTable.completed],
        createdAt = this[TaskTable.createdAt]
    )
}

package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import models.CreateTaskRequest
import models.UpdateTaskRequest
import repository.TaskRepository

fun Route.taskRoutes() {
    val repository = TaskRepository()

    route("/tasks") {
        get {
            val tasks = repository.getAll()
            call.respond(tasks)
        }

        get("/{id}") {
            val taskId = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing task ID")

            val task = repository.getById(taskId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Task not found")

            call.respond(task)
        }

        post {
            val request = call.receive<CreateTaskRequest>()
            val task = repository.create(request)
            call.respond(HttpStatusCode.Created, task)
        }

        put("/{id}") {
            val taskId = call.parameters["id"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, "Missing task ID")

            val request = call.receive<UpdateTaskRequest>()
            val task = repository.update(taskId, request.completed)
                ?: return@put call.respond(HttpStatusCode.NotFound, "Task not found")

            call.respond(task)
        }

        delete("/{id}") {
            val taskId = call.parameters["id"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Missing task ID")

            val deleted = repository.delete(taskId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, "Task not found")
            }
        }
    }
}

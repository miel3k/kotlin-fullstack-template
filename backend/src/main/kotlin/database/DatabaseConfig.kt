package database

import io.ktor.server.application.Application
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    val url = environment.config.propertyOrNull("database.url")?.getString()
        ?: System.getenv("DATABASE_URL")
        ?: "jdbc:postgresql://localhost:5432/taskapp"

    val user = environment.config.propertyOrNull("database.user")?.getString()
        ?: System.getenv("DATABASE_USER")
        ?: "dev"

    val password = environment.config.propertyOrNull("database.password")?.getString()
        ?: System.getenv("DATABASE_PASSWORD")
        ?: "dev"

    Database.connect(
        url = url,
        driver = "org.postgresql.Driver",
        user = user,
        password = password
    )

    transaction {
        SchemaUtils.create(TaskTable)
    }
}

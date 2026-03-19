package database

import org.jetbrains.exposed.v1.core.Table

object TaskTable : Table("tasks") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val completed = bool("completed")
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

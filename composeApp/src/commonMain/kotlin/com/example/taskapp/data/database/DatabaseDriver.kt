package com.example.taskapp.data.database

import app.cash.sqldelight.db.SqlDriver
import com.example.taskapp.database.TaskDatabase

expect fun createDatabaseDriver(): SqlDriver

fun createDatabase(driver: SqlDriver): TaskDatabase {
    return TaskDatabase(driver)
}

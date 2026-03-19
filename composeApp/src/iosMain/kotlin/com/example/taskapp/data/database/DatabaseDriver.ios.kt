package com.example.taskapp.data.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.taskapp.database.TaskDatabase

actual fun createDatabaseDriver(): SqlDriver {
    return NativeSqliteDriver(TaskDatabase.Schema, "task.db")
}

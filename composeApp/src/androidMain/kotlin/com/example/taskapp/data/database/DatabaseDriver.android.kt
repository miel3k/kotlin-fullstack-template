package com.example.taskapp.data.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.example.taskapp.database.TaskDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual fun createDatabaseDriver(): SqlDriver {
    val context = DatabaseDriverFactory().context
    return AndroidSqliteDriver(TaskDatabase.Schema, context, "task.db")
}

class DatabaseDriverFactory : KoinComponent {
    val context: Context by inject()
}

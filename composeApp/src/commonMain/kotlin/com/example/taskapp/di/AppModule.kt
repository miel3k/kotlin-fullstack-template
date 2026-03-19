package com.example.taskapp.di

import com.example.taskapp.config.getApiBaseUrl
import com.example.taskapp.data.database.createDatabase
import com.example.taskapp.data.database.createDatabaseDriver
import com.example.taskapp.data.datasource.TaskDataSource
import com.example.taskapp.data.repository.DefaultTaskRepository
import com.example.taskapp.data.repository.TaskRepository
import com.example.taskapp.network.HttpClientFactory
import com.example.taskapp.viewmodel.TaskListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val appModule = module {
    // Database
    single { createDatabaseDriver() }
    single { createDatabase(get()) }

    // Network
    single { HttpClientFactory.create(getApiBaseUrl()) }
    single { TaskDataSource(get()) }

    // Repository
    single<TaskRepository> { DefaultTaskRepository(get(), get()) }

    // ViewModels
    factoryOf(::TaskListViewModel)
}

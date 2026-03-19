package com.example.taskapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.ComposeUIViewController
import com.example.taskapp.di.appModule
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController
import com.example.taskapp.ui.TaskListScreen

fun MainViewController(): UIViewController {
    startKoin {
        modules(appModule)
    }

    return ComposeUIViewController {
        MaterialTheme {
            TaskListScreen()
        }
    }
}

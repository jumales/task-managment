package com.demo.taskmanager

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt application class — must be declared before any @AndroidEntryPoint can inject. */
@HiltAndroidApp
class TaskManagerApp : Application()

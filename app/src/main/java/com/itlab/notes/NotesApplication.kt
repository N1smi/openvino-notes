package com.itlab.notes

import android.app.Application
import com.itlab.data.di.dataModule
import com.itlab.notes.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class NotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@NotesApplication)
            workManagerFactory()
            modules(listOf(appModule, dataModule))
        }
    }
}

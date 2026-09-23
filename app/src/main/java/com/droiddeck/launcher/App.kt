package com.droiddeck.launcher

import android.app.Application
import com.droiddeck.launcher.session.CrashHandler

/** Process-wide setup: the crash handler, so a session's folder is finished even when we die. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}

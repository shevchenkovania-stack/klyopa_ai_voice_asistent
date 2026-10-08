package com.aiagent.ai_voice_agent

import android.app.Application
import android.util.Log
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache

/**
 * Application - creates and caches FlutterEngine at process start.
 * MainActivity.provideFlutterEngine() reuses this engine so the Dart isolate
 * (and background services) survive Activity destruction.
 * NOTE: recreated during project recovery (original file was not in any snapshot).
 */
class WakeUpApplication : Application() {
    companion object {
        const val TAG = "WakeUpApp"
        const val ENGINE_ID = "main_engine"
    }

    lateinit var flutterEngine: FlutterEngine
        private set

    override fun onCreate() {
        super.onCreate()
        flutterEngine = FlutterEngine(this)
        flutterEngine.dartExecutor.executeDartEntrypoint(
            io.flutter.embedding.engine.dart.DartExecutor.DartEntrypoint.createDefault()
        )
        FlutterEngineCache.getInstance().put(ENGINE_ID, flutterEngine)
        Log.d(TAG, "[INIT] FlutterEngine cached as '$ENGINE_ID'")
    }

    override fun onTerminate() {
        FlutterEngineCache.getInstance().remove(ENGINE_ID)
        flutterEngine.destroy()
        super.onTerminate()
    }
}
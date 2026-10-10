package com.zhaoshuo.localvideoplayer

import android.app.Application
import android.content.Context
import com.zhaoshuo.localvideoplayer.data.VideoDurationResolver

class LocalVideoPlayerApplication : Application() {
    lateinit var durationResolver: VideoDurationResolver
        private set

    override fun onCreate() {
        super.onCreate()
        durationResolver = VideoDurationResolver(this)
    }

    override fun onTerminate() {
        if (::durationResolver.isInitialized) {
            durationResolver.release()
        }
        super.onTerminate()
    }
}

fun Context.appDurationResolver(): VideoDurationResolver {
    val app = applicationContext as? LocalVideoPlayerApplication
    return app?.durationResolver ?: VideoDurationResolver(applicationContext)
}

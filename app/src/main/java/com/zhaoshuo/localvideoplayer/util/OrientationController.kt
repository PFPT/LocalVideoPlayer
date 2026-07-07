package com.zhaoshuo.localvideoplayer.util

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration

class OrientationController(private val activity: Activity) {

    fun unlockForPlayer() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    fun lockPortrait() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun lockCurrentOrientation() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
    }

    fun togglePortraitLandscape() {
        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
            return
        }
        activity.requestedOrientation = if (isLandscape()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun isLandscape(): Boolean {
        return activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}

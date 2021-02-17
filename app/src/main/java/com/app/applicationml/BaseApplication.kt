package com.app.applicationml

import android.app.Application
import pyxis.uzuki.live.mediaresizer.MediaResizerGlobal

/**
 * MediaResizer
 * Class: BaseApplication
 * Created by Pyxis on 2017-11-27.
 *
 * Description:
 */
class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        MediaResizerGlobal.initializeApplication(this)
    }
}
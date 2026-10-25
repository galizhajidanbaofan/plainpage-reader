package io.github.galizhajidanbaofan.plainpage

import android.app.Application
import io.github.galizhajidanbaofan.plainpage.di.AppContainer

class PlainPageApplication : Application() {

    /** 组合根，Activity/ViewModel 从这里取依赖；不要在业务代码里 new 数据库。 */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

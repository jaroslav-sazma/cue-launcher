package app.cue.launcher

import android.app.Application
import app.cue.launcher.di.AppGraph
import dev.zacsweers.metro.createGraphFactory

class CueApplication : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}

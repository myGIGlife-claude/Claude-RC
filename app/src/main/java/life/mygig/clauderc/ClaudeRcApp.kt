package life.mygig.clauderc

import android.app.Application
import life.mygig.clauderc.notify.Push

class ClaudeRcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.start(this)   // Firebase must be up before a push can wake the app
    }
}

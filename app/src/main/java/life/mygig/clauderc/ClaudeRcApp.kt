package life.mygig.clauderc

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import life.mygig.clauderc.notify.Push
import life.mygig.clauderc.ssh.SshRunner

class ClaudeRcApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.start(this)   // Firebase must be up before a push can wake the app
        watchNetwork()
    }

    /** Wi-Fi <-> mobile data leaves cached SSH connections on a dead socket: drop them at once. */
    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                private var current: Network? = null
                override fun onAvailable(network: Network) {
                    val old = current
                    current = network
                    if (old != null && old != network) SshRunner.retireAll()   // the first call is just the current network
                }
                override fun onLost(network: Network) {
                    if (network == current) { current = null; SshRunner.retireAll() }
                }
            })
        }
    }
}

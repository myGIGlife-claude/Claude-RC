package life.mygig.clauderc

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import life.mygig.clauderc.ui.AppLock
import life.mygig.clauderc.ui.AppRoot
import life.mygig.clauderc.ui.MainViewModel
import life.mygig.clauderc.widget.WidgetLink
import life.mygig.clauderc.widget.WidgetTap
import life.mygig.clauderc.widget.parseWidgetTap

class MainActivity : FragmentActivity(), AppLock {

    private val vm: MainViewModel by viewModels()

    /** When the app last went to the background; null while it's in front. */
    private var backgroundedAt: Long? = null

    /** A widget tap not handed to the view model yet (that happens in onResume, after onStart's lock check). */
    private var widgetTap: WidgetTap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A recreated activity (rotation, restore) gets its launch intent again: that tap was handled.
        if (savedInstanceState == null) widgetTap = takeWidgetTap(intent)
        setContent { AppRoot(vm = vm, lock = this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetTap = takeWidgetTap(intent)
    }

    override fun onResume() {
        super.onResume()
        // The view model waits for the app to be unlocked before acting on it.
        widgetTap?.let { vm.openFromWidget(it) }
        widgetTap = null
    }

    /** Reads the widget extras and removes them, so nothing acts on them twice. */
    private fun takeWidgetTap(intent: Intent): WidgetTap? = runCatching {
        val tap = parseWidgetTap(intent.getStringExtra(WidgetLink.EXTRA_OPEN), intent.getStringExtra(WidgetLink.EXTRA_SESSION))
        intent.removeExtra(WidgetLink.EXTRA_OPEN)
        intent.removeExtra(WidgetLink.EXTRA_SESSION)
        tap
    }.getOrNull()   // a malformed extras bundle (any app can start this activity): ignored

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
        vm.chats.resumeChat()
        // Lock again after a while in the background. The short grace period
        // covers the PIN screen (its own activity) and quick trips to the
        // browser during a login.
        val since = backgroundedAt
        if (since != null && SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS) vm.lockApp()
        backgroundedAt = null
    }

    override fun onStop() {
        super.onStop()
        vm.setForeground(false)
        if (!isChangingConfigurations) {
            backgroundedAt = SystemClock.elapsedRealtime()
            vm.chats.lockChat()   // chat needs the PIN again after any trip away from the app
        }
    }

    override fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Fingerprint or device PIN. If the phone has no screen lock at all there is
     * nothing to check against, so it passes (App lock can't be turned on then).
     */
    override suspend fun unlock(reason: String): Boolean {
        if (!canAuthenticate()) return true
        return suspendCancellableCoroutine { cont ->
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock cLaudeRC")
                    .setSubtitle(reason)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build(),
            )
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        const val RELOCK_AFTER_MS = 30_000L
    }
}

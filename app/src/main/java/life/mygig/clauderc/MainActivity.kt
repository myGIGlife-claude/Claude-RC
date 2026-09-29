package life.mygig.clauderc

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import life.mygig.clauderc.ui.AppLock
import life.mygig.clauderc.ui.AppRoot
import life.mygig.clauderc.ui.MainViewModel

class MainActivity : FragmentActivity(), AppLock {

    private val vm: MainViewModel by viewModels()

    /** When the app last went to the background; null while it's in front. */
    private var backgroundedAt: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot(vm = vm, lock = this) }
    }

    override fun onStart() {
        super.onStart()
        vm.resumeChat()
        // Lock again after a while in the background. The short grace period
        // covers the PIN screen (its own activity) and quick trips to the
        // browser during a login.
        val since = backgroundedAt
        if (since != null && SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS) vm.lockApp()
        backgroundedAt = null
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backgroundedAt = SystemClock.elapsedRealtime()
            vm.lockChat()   // chat needs the PIN again after any trip away from the app
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
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        const val RELOCK_AFTER_MS = 30_000L
    }
}

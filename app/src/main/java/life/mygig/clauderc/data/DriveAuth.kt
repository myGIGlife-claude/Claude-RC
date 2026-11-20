package life.mygig.clauderc.data

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Google Identity Authorization for the Drive app folder scope only. The access token is handed to the caller and never stored. */
object DriveAuth {
    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DriveLogic.SCOPE)))
        .build()

    suspend fun authorize(ctx: Context): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(ctx).authorize(request())
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Token from the consent screen's result intent; throws ApiException when the user cancelled. */
    fun resultFromIntent(ctx: Context, data: android.content.Intent?): String? =
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data).accessToken
}

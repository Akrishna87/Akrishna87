package io.github.akrishna87.rssreader.ui

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** The web client of the Firebase project (the one the web reader signs in with). */
private const val WEB_CLIENT_ID = "799196048612-j2usflo476gv7cdaungb9aa1qul81k98.apps.googleusercontent.com"

/** Shows Google's account chooser. Returns the ID token, or null if the user backed out; throws with a readable message otherwise. */
suspend fun googleIdToken(activityContext: Context): String? {
    val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()).build()
    return try {
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            throw IllegalStateException("Google sign-in returned something unexpected")
        }
    } catch (e: GetCredentialCancellationException) {
        null
    } catch (e: GetCredentialException) {
        throw IllegalStateException(
            "Google sign-in failed (${e.type.substringAfterLast('.')}). Check that this app's signing fingerprint is added in Firebase.",
        )
    }
}

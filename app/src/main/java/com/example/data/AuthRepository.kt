package com.example.data

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import android.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UserSession(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val idToken: String? = null
)

/** Reason a Google sign-in attempt failed, used to pick a user-facing message and whether retry makes sense. */
enum class AuthErrorReason {
    NO_GOOGLE_ACCOUNT,
    CANCELLED,
    NETWORK,
    INVALID_CREDENTIAL,
    FIREBASE_REJECTED,
    UNKNOWN
}

sealed class AuthResult {
    data class Success(val session: UserSession) : AuthResult()
    data class Error(val reason: AuthErrorReason, val message: String) : AuthResult()
}

interface AuthRepository {
    val activeSession: StateFlow<UserSession?>

    suspend fun signInWithGoogle(context: Context, serverClientId: String): AuthResult
    suspend fun signInWithMockDevUser(): UserSession
    suspend fun signOut(context: Context)
    fun restoreSessionFromCache(session: UserSession)
}

class AuthRepositoryImpl(private val securityStore: SecurityStore) : AuthRepository {
    private val _activeSession = MutableStateFlow<UserSession?>(null)
    override val activeSession: StateFlow<UserSession?> = _activeSession

    override suspend fun signInWithGoogle(context: Context, serverClientId: String): AuthResult {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Allow new sign-ins
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .setNonce(generateSecureRandomNonce())
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                request = request,
                context = context,
            )
            handleSignIn(result)
        } catch (e: NoCredentialException) {
            Log.e("AuthRepository", "No Google account available for sign-in", e)
            AuthResult.Error(
                AuthErrorReason.NO_GOOGLE_ACCOUNT,
                "No Google account found on this device. Add one in Settings and try again."
            )
        } catch (e: GetCredentialCancellationException) {
            Log.w("AuthRepository", "Sign-in cancelled by user")
            AuthResult.Error(AuthErrorReason.CANCELLED, "Sign-in was cancelled.")
        } catch (e: GetCredentialInterruptedException) {
            Log.e("AuthRepository", "Sign-in interrupted", e)
            AuthResult.Error(AuthErrorReason.NETWORK, "Sign-in was interrupted. Please try again.")
        } catch (e: GetCredentialException) {
            Log.e("AuthRepository", "Failed to get Google credential", e)
            AuthResult.Error(
                AuthErrorReason.UNKNOWN,
                "Couldn't sign in with Google (${e.type}). Please try again."
            )
        }
    }

    private suspend fun handleSignIn(result: GetCredentialResponse): AuthResult {
        val credential = result.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            Log.e("AuthRepository", "Unexpected credential type returned: ${credential.type}")
            return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIAL, "Received an unexpected credential type.")
        }

        val googleIdTokenCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("AuthRepository", "Failed to parse Google ID token", e)
            return AuthResult.Error(AuthErrorReason.INVALID_CREDENTIAL, "Received an invalid Google credential.")
        }

        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val firebaseUser: com.google.firebase.auth.FirebaseUser? = try {
            suspendCancellableCoroutine<com.google.firebase.auth.FirebaseUser?> { continuation ->
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                    .addOnSuccessListener { authResult -> continuation.resume(authResult.user) }
                    .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
            }
        } catch (e: FirebaseNetworkException) {
            Log.e("AuthRepository", "Firebase sign-in failed due to network error", e)
            return AuthResult.Error(AuthErrorReason.NETWORK, "No internet connection. Please try again.")
        } catch (e: FirebaseAuthException) {
            Log.e("AuthRepository", "Firebase rejected the Google credential (${e.errorCode})", e)
            return AuthResult.Error(AuthErrorReason.FIREBASE_REJECTED, "Google sign-in was rejected: ${e.errorCode}")
        } catch (e: Exception) {
            Log.e("AuthRepository", "Unexpected error exchanging credential with Firebase", e)
            return AuthResult.Error(AuthErrorReason.UNKNOWN, "Something went wrong signing you in. Please try again.")
        }

        val session = UserSession(
            uid = firebaseUser?.uid ?: googleIdTokenCredential.id,
            displayName = firebaseUser?.displayName ?: googleIdTokenCredential.displayName ?: "User",
            email = firebaseUser?.email ?: extractEmailFromIdToken(googleIdTokenCredential.idToken) ?: googleIdTokenCredential.id,
            photoUrl = firebaseUser?.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
            idToken = googleIdTokenCredential.idToken
        )

        cacheSession(session)
        _activeSession.value = session
        return AuthResult.Success(session)
    }

    private fun extractEmailFromIdToken(idToken: String): String? {
        try {
            val parts = idToken.split(".")
            if (parts.size == 3) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE))
                val json = org.json.JSONObject(payload)
                val email = json.optString("email", "")
                return if (email.isNotEmpty()) email else null
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to extract email from ID token", e)
        }
        return null
    }

    override suspend fun signInWithMockDevUser(): UserSession {
        val mockSession = UserSession(
            uid = "mock-dev-uid-12345",
            displayName = "Mock Developer",
            email = "dev@example.com",
            photoUrl = null,
            idToken = "mock_token"
        )
        cacheSession(mockSession)
        _activeSession.value = mockSession
        return mockSession
    }

    override suspend fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context)
        try {
            credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to clear credential state", e)
        }
        securityStore.clearCachedSession()
        _activeSession.value = null
    }

    override fun restoreSessionFromCache(session: UserSession) {
        _activeSession.value = session
    }

    private fun cacheSession(session: UserSession) {
        securityStore.cacheSession(
            uid = session.uid,
            displayName = session.displayName,
            email = session.email,
            photoUrl = session.photoUrl,
            idToken = session.idToken
        )
    }

    private fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }
}

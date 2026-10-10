package com.example.ui.login

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.AuthRepository
import com.example.data.repository.CasinoFirestoreRepository
import com.example.data.repository.CasinoRepository
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.UserProfileRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Success(val user: FirebaseUser) : AuthUiState
    data class Error(val message: String) : AuthUiState

    val userOrNull: FirebaseUser?
        get() = (this as? Success)?.user
}

class LoginViewModel(
    private val authRepository: AuthRepository = FirebaseAuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(
        authRepository.currentUser?.let {
            syncUserWithCasino(it)
            AuthUiState.Success(it)
        } ?: AuthUiState.Idle
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collectLatest { user ->
                if (user != null) {
                    syncUserWithCasino(user)
                    _uiState.value = AuthUiState.Success(user)
                } else {
                    if (_uiState.value is AuthUiState.Success) {
                        _uiState.value = AuthUiState.Idle
                    }
                }
            }
        }
    }

    /**
     * Initiates the Jetpack Credential Manager Google Sign-In sheet
     */
    fun launchGoogleSignIn(context: Context, serverClientId: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(
                    request = request,
                    context = context
                )

                handleCredentialResponse(response.credential)
            } catch (e: GetCredentialCancellationException) {
                // User dismissed the picker dialog
                _uiState.value = authRepository.currentUser?.let { AuthUiState.Success(it) } ?: AuthUiState.Idle
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }

    /**
     * Parses the Google ID Token from Credential and executes sign-in
     */
    fun handleCredentialResponse(credential: Credential) {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                signInWithGoogle(googleIdTokenCredential.idToken)
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.localizedMessage ?: "Invalid Google credential format")
            }
        } else {
            _uiState.value = AuthUiState.Error("Unsupported credential type: ${credential.type}")
        }
    }

    /**
     * Authenticates with Firebase using a Google ID token
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = { user ->
                    syncUserWithCasino(user)
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Google authentication failed")
                }
            )
        }
    }

    // Alias for backward compatibility
    fun signInWithGoogleIdToken(idToken: String) = signInWithGoogle(idToken)

    /**
     * Signs in with Email and Password
     */
    fun signInWithEmail(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.signInWithEmail(trimmedEmail, password)
            result.fold(
                onSuccess = { user ->
                    syncUserWithCasino(user)
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Email sign-in failed")
                }
            )
        }
    }

    /**
     * Registers a new account with Email and Password
     */
    fun signUpWithEmail(email: String, password: String, displayName: String? = null) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.signUpWithEmail(trimmedEmail, password, displayName)
            result.fold(
                onSuccess = { user ->
                    syncUserWithCasino(user)
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Registration failed")
                }
            )
        }
    }

    /**
     * Signs out of Firebase and resets state to Idle
     */
    fun signOut() {
        viewModelScope.launch {
            val result = authRepository.signOut()
            result.fold(
                onSuccess = {
                    _uiState.value = AuthUiState.Idle
                },
                onFailure = { error ->
                    _uiState.value = AuthUiState.Error(error.localizedMessage ?: "Failed to sign out")
                }
            )
        }
    }

    /**
     * Dismisses the error state and returns to Idle (or Success if currently signed in)
     */
    fun clearError() {
        val user = authRepository.currentUser
        if (user != null) {
            _uiState.value = AuthUiState.Success(user)
        } else {
            _uiState.value = AuthUiState.Idle
        }
    }

    /**
     * Synchronizes current user data to Firestore
     */
    fun syncWithFirestore(firestoreRepo: CasinoFirestoreRepository) {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            val profile = CasinoRepository.userProfile.value
            val bal = CasinoRepository.balance.value
            firestoreRepo.createUserProfile(
                profile.copy(
                    id = user.uid,
                    userId = user.uid,
                    chipBalance = bal,
                    balance = bal,
                    displayName = profile.username
                )
            )
        }
    }

    fun syncWithFirestore(userProfileRepo: UserProfileRepository) {
        val user = authRepository.currentUser ?: return
        viewModelScope.launch {
            val profile = CasinoRepository.userProfile.value
            val bal = CasinoRepository.balance.value
            userProfileRepo.createUserProfile(
                profile.copy(
                    id = user.uid,
                    userId = user.uid,
                    chipBalance = bal,
                    balance = bal,
                    displayName = profile.username
                )
            )
        }
    }

    private fun syncUserWithCasino(user: FirebaseUser) {
        val name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: "Player"
        CasinoRepository.updateProfile(name, "👑")
    }
}

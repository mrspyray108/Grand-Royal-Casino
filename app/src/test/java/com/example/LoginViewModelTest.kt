package com.example

import com.example.data.repository.AuthRepository
import com.example.ui.login.AuthUiState
import com.example.ui.login.LoginViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    class FakeAuthRepository : AuthRepository {
        var userToReturn: FirebaseUser? = null
        var shouldSucceed = true
        private val authFlow = MutableStateFlow<FirebaseUser?>(null)

        override val currentUser: FirebaseUser?
            get() = userToReturn

        override fun observeAuthState(): Flow<FirebaseUser?> = authFlow

        override suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
            return if (shouldSucceed && userToReturn != null) Result.success(userToReturn!!)
            else Result.failure(Exception("Google Sign-in failed"))
        }

        override suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
            return if (shouldSucceed && userToReturn != null) Result.success(userToReturn!!)
            else Result.failure(Exception("Email Sign-in failed"))
        }

        override suspend fun signUpWithEmail(
            email: String,
            password: String,
            displayName: String?
        ): Result<FirebaseUser> {
            return if (shouldSucceed && userToReturn != null) Result.success(userToReturn!!)
            else Result.failure(Exception("Sign-up failed"))
        }

        override suspend fun signOut(): Result<Unit> {
            userToReturn = null
            authFlow.value = null
            return Result.success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAuthUiStates() {
        val idle: AuthUiState = AuthUiState.Idle
        assertTrue(idle is AuthUiState.Idle)

        val loading: AuthUiState = AuthUiState.Loading
        assertTrue(loading is AuthUiState.Loading)

        val error: AuthUiState = AuthUiState.Error("Invalid token")
        assertTrue(error is AuthUiState.Error)
        assertEquals("Invalid token", (error as AuthUiState.Error).message)
    }

    @Test
    fun testLoginViewModelInitialIdle() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun testLoginViewModelSignInValidationError() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeRepo)

        // Invalid email without @
        viewModel.signInWithEmail("invalidemail", "password123")
        assertTrue(viewModel.uiState.value is AuthUiState.Error)
        assertEquals("Please enter a valid email address.", (viewModel.uiState.value as AuthUiState.Error).message)
    }

    @Test
    fun testLoginViewModelSignInError() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository().apply { shouldSucceed = false }
        val viewModel = LoginViewModel(fakeRepo)

        viewModel.signInWithEmail("test@example.com", "wrongpass")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is AuthUiState.Error)
    }

    @Test
    fun testLoginViewModelSignOut() = runTest(testDispatcher) {
        val fakeRepo = FakeAuthRepository()
        val viewModel = LoginViewModel(fakeRepo)

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }
}

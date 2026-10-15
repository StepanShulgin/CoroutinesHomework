package ru.otus.coroutineshomework

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.otus.coroutineshomework.ui.login.LoginService
import ru.otus.coroutineshomework.ui.login.LoginViewModel
import ru.otus.coroutineshomework.ui.login.LoginViewState
import ru.otus.coroutineshomework.ui.login.data.Credentials
import ru.otus.coroutineshomework.ui.login.data.User
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeLoginService()

    @Test fun loginEmitsLoadingThenContentAndPassesCredentialsUnchanged() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        val states = mutableListOf<LoginViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { states += it }
        }
        model.login("admin", " password ")
        advanceUntilIdle()
        assertEquals(
            listOf(LoginViewState.Login(), LoginViewState.LoggingIn, LoginViewState.Content(api.user)),
            states
        )
        assertEquals(Credentials("admin", " password "), api.credentials)
    }

    @Test fun loginFailureIsExposedAndRetryCanSucceed() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        val error = IOException("Offline")
        api.loginError = error
        model.login("admin", "password")
        advanceUntilIdle()
        val reported = (model.state.value as LoginViewState.Login).error
        assertTrue(reported is IOException)
        assertEquals(error.message, reported?.message)
        api.loginError = null
        model.login("admin", "password")
        advanceUntilIdle()
        assertEquals(LoginViewState.Content(api.user), model.state.value)
    }

    @Test fun logoutEmitsLoadingThenLogin() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        model.login("admin", "password")
        advanceUntilIdle()
        val states = mutableListOf<LoginViewState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { states += it }
        }
        model.logout()
        assertEquals(LoginViewState.LoggingOut, model.state.value)
        advanceUntilIdle()
        assertEquals(
            listOf(LoginViewState.Content(api.user), LoginViewState.LoggingOut, LoginViewState.Login()),
            states
        )
        assertEquals(1, api.logoutCalls)
    }

    @Test fun logoutFailureReturnsToFormWithError() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        model.login("admin", "password")
        advanceUntilIdle()
        val error = IOException("Logout failed")
        api.logoutError = error
        model.logout()
        advanceUntilIdle()
        val reported = (model.state.value as LoginViewState.Login).error
        assertTrue(reported is IOException)
        assertEquals(error.message, reported?.message)
    }

    @Test fun repeatedActionsDoNotStartDuplicateRequests() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        repeat(3) { model.login("admin", "password") }
        advanceUntilIdle()
        assertEquals(1, api.loginCalls)
        model.login("other", "password")
        repeat(3) { model.logout() }
        advanceUntilIdle()
        assertEquals(1, api.loginCalls)
        assertEquals(1, api.logoutCalls)
    }

    @Test fun logoutBeforeLoginDoesNothing() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        model.logout()
        advanceUntilIdle()
        assertEquals(LoginViewState.Login(), model.state.value)
        assertEquals(0, api.logoutCalls)
    }

    @Test fun cancellationIsNotPresentedAsLoginError() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        api.loginError = CancellationException("Cancelled")
        model.login("admin", "password")
        advanceUntilIdle()
        assertEquals(LoginViewState.LoggingIn, model.state.value)
    }

    @Test fun clearingViewModelCancelsQueuedOperation() = runTest {
        val model = LoginViewModel(api, main.dispatcher)
        val store = ViewModelStore()
        store.put("login", model)
        model.login("admin", "password")
        store.clear()
        advanceUntilIdle()
        assertEquals(0, api.loginCalls)
        assertTrue(model.state.value is LoginViewState.Login)
    }

    private class FakeLoginService : LoginService {
        val user = User(1, "Admin")
        var credentials: Credentials? = null
        var loginError: Exception? = null
        var logoutError: Exception? = null
        var loginCalls = 0
        var logoutCalls = 0

        override fun login(credentials: Credentials): User {
            loginCalls++
            this.credentials = credentials
            loginError?.let { throw it }
            return user
        }

        override fun logout() {
            logoutCalls++
            logoutError?.let { throw it }
        }
    }
}

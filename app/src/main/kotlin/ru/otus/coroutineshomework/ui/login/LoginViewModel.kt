package ru.otus.coroutineshomework.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.otus.coroutineshomework.ui.login.data.Credentials

class LoginViewModel(
    private val api: LoginService = LoginApi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val stateFlow = MutableStateFlow<LoginViewState>(LoginViewState.Login())
    val state: StateFlow<LoginViewState> = stateFlow.asStateFlow()
    private var operation: Job? = null

    /**
     * Login to the network
     * @param name user name
     * @param password user password
     */
    fun login(name: String, password: String) {
        if (operation?.isActive == true || stateFlow.value !is LoginViewState.Login) return
        operation = viewModelScope.launch {
            loginFlow(Credentials(name, password)).collect { stateFlow.value = it }
        }
    }

    private fun loginFlow(credentials: Credentials): Flow<LoginViewState> = flow {
        emit(LoginViewState.LoggingIn)
        emit(LoginViewState.Content(api.login(credentials)))
    }.flowOn(ioDispatcher).catch { error ->
        if (error is CancellationException) throw error
        currentCoroutineContext().ensureActive()
        if (error !is Exception) throw error
        emit(LoginViewState.Login(error))
    }

    /**
     * Logout from the network
     */
    fun logout() {
        if (operation?.isActive == true || stateFlow.value !is LoginViewState.Content) return
        stateFlow.value = LoginViewState.LoggingOut
        operation = viewModelScope.launch {
            try {
                withContext(ioDispatcher) { api.logout() }
                stateFlow.value = LoginViewState.Login()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                stateFlow.value = LoginViewState.Login(error)
            }
        }
    }
}

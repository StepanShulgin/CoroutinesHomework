package ru.otus.coroutineshomework.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.otus.coroutineshomework.ui.login.data.Credentials

class LoginViewModel(
    private val api: LoginService = LoginApi(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableLiveData<LoginViewState>(LoginViewState.Login())
    val state: LiveData<LoginViewState> = _state

    /**
     * Login to the network
     * @param name user name
     * @param password user password
     */
    fun login(name: String, password: String) {
        if (_state.value !is LoginViewState.Login) return
        _state.value = LoginViewState.LoggingIn
        viewModelScope.launch {
            try {
                val user = withContext(ioDispatcher) { api.login(Credentials(name, password)) }
                _state.value = LoginViewState.Content(user)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                _state.value = LoginViewState.Login(error)
            }
        }
    }

    /**
     * Logout from the network
     */
    fun logout() {
        if (_state.value !is LoginViewState.Content) return
        _state.value = LoginViewState.LoggingOut
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { api.logout() }
                _state.value = LoginViewState.Login()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                _state.value = LoginViewState.Login(error)
            }
        }
    }
}

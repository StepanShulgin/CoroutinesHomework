package ru.otus.coroutineshomework.ui.login

import android.os.Looper
import android.os.NetworkOnMainThreadException
import android.util.Log
import ru.otus.coroutineshomework.ui.login.data.Credentials
import ru.otus.coroutineshomework.ui.login.data.User

interface LoginService {
    fun login(credentials: Credentials): User
    fun logout()
}

class LoginApi : LoginService {
    override fun login(credentials: Credentials): User = emulateNetworkRequest {
        if (credentials.login == "admin" && credentials.password == "password") {
            User(1, "Admin")
        } else {
            throw IllegalArgumentException("Invalid credentials")
        }
    }

    override fun logout() = emulateNetworkRequest {
        Unit
    }

    private inline fun <T> emulateNetworkRequest(crossinline block: () -> T): T {
        Log.i(TAG, "emulateNetworkRequest: running on ${Thread.currentThread().name}")
        if (Looper.getMainLooper().thread == Thread.currentThread()) {
            throw NetworkOnMainThreadException()
        }
        Thread.sleep(NETWORK_DELAY)
        return block()
    }

    private companion object {
        private const val TAG = "NetworkApi"
        private const val NETWORK_DELAY = 1000L
    }
}

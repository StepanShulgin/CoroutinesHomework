package ru.otus.coroutineshomework.ui.network

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class NetworkViewModel(
    private val request: suspend () -> Result<Long> = { emulateBlockingNetworkRequest() }
) : ViewModel() {

    private val _running: MutableLiveData<Boolean> = MutableLiveData(false)
    val running: LiveData<Boolean> = _running

    private val _result: MutableLiveData<Long?> = MutableLiveData()
    val result: LiveData<Long?> = _result

    fun startTest(numberOfThreads: Int) {
        require(numberOfThreads > 0) { "Number of requests must be positive" }
        if (_running.value == true) return
        _running.value = true
        _result.value = null
        viewModelScope.launch {
            try {
                val successful = coroutineScope {
                    List(numberOfThreads) {
                        async {
                            try {
                                request().getOrThrow()
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                ensureActive()
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
                _result.value = successful.takeIf { it.isNotEmpty() }
                    ?.let { it.sum() / it.size }
            } finally {
                _running.value = false
            }
        }
    }

    private companion object {
        private const val TAG = "NetworkViewModel"
        private const val MIN_DELAY = 1000L
        private const val MAX_DELAY = 5000L

        suspend fun emulateBlockingNetworkRequest(): Result<Long> = withContext(Dispatchers.IO) {
            val delay = Random.nextLong(MIN_DELAY, MAX_DELAY)
            var progress = 0L
            while (isActive && progress < delay) {
                if (5 == Random.nextInt(1, 20)) {
                    log("emulateBlockingNetworkRequest: Network request failed")
                    return@withContext Result.failure(Exception("Network request failed"))
                }
                log("emulateBlockingNetworkRequest: progress = $progress, delay = $delay")
                Thread.sleep(100)
                progress += 100
            }
            ensureActive()
            log("emulateBlockingNetworkRequest: Network request completed")
            return@withContext Result.success(delay)
        }

        private fun log(message: String) {
            Log.i(TAG, "Thread: ${Thread.currentThread().name}. $message")
        }
    }
}


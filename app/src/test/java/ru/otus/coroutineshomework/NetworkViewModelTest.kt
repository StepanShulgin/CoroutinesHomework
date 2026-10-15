package ru.otus.coroutineshomework

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.otus.coroutineshomework.ui.network.NetworkViewModel
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val liveData = InstantTaskExecutorRule()

    @Test fun requestsRunConcurrentlyAndOnlySuccessesContributeToAverage() = runTest {
        var calls = 0
        var active = 0
        var maximumActive = 0
        val model = NetworkViewModel {
            val index = calls++
            active++
            maximumActive = maxOf(maximumActive, active)
            try {
                delay(300L - index * 100)
                if (index == 1) Result.failure(IOException("Failed"))
                else Result.success(if (index == 0) 100L else 300L)
            } finally {
                active--
            }
        }
        model.startTest(3)
        assertTrue(model.running.value == true)
        advanceUntilIdle()
        assertEquals(3, maximumActive)
        assertEquals(300L, currentTime)
        assertEquals(200L, model.result.value)
        assertFalse(model.running.value == true)
    }

    @Test fun allFailuresProduceNoAverageAndStopLoading() = runTest {
        val model = NetworkViewModel { Result.failure(IOException("Failed")) }
        model.startTest(5)
        advanceUntilIdle()
        assertNull(model.result.value)
        assertEquals(false, model.running.value)
    }

    @Test fun thrownRequestErrorDoesNotCancelOtherRequests() = runTest {
        var calls = 0
        val model = NetworkViewModel {
            if (calls++ == 0) throw IOException("Failed")
            delay(10)
            Result.success(125L)
        }
        model.startTest(2)
        advanceUntilIdle()
        assertEquals(125L, model.result.value)
        assertEquals(false, model.running.value)
    }

    @Test fun repeatedStartDuringRunIsIgnored() = runTest {
        var calls = 0
        val model = NetworkViewModel {
            calls++
            delay(100)
            Result.success(100L)
        }
        repeat(3) { model.startTest(5) }
        advanceUntilIdle()
        assertEquals(5, calls)
    }

    @Test fun newRunClearsOldResultAndCanFinishWithAllFailures() = runTest {
        var successful = true
        val model = NetworkViewModel {
            if (successful) Result.success(100L) else Result.failure(IOException("Failed"))
        }
        model.startTest(1)
        advanceUntilIdle()
        assertEquals(100L, model.result.value)
        successful = false
        model.startTest(1)
        assertNull(model.result.value)
        advanceUntilIdle()
        assertNull(model.result.value)
        assertEquals(false, model.running.value)
    }

    @Test fun clearingViewModelCancelsEveryRequestAndStopsLoading() = runTest {
        var active = 0
        val model = NetworkViewModel {
            active++
            try {
                delay(10_000)
                Result.success(100L)
            } finally {
                active--
            }
        }
        val store = ViewModelStore()
        store.put("network", model)
        model.startTest(5)
        runCurrent()
        assertEquals(5, active)
        store.clear()
        advanceUntilIdle()
        assertEquals(0, active)
        assertEquals(false, model.running.value)
        assertNull(model.result.value)
    }

    @Test fun cancellationResultIsNotTreatedAsOrdinaryFailedRequest() = runTest {
        val model = NetworkViewModel {
            Result.failure(CancellationException("Cancelled"))
        }
        model.startTest(2)
        advanceUntilIdle()
        assertNull(model.result.value)
        assertEquals(false, model.running.value)
    }

    @Test fun nonPositiveRequestCountsAreRejectedWithoutStarting() {
        val model = NetworkViewModel { Result.success(100L) }
        for (count in listOf(0, -1)) {
            assertThrowsIllegalArgument { model.startTest(count) }
        }
        assertEquals(false, model.running.value)
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java, block)
    }
}

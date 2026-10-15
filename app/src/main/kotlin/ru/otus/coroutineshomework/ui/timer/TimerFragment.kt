package ru.otus.coroutineshomework.ui.timer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import ru.otus.coroutineshomework.databinding.FragmentTimerBinding
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class TimerFragment : Fragment() {

    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!
    private var timerJob: Job? = null

    private lateinit var timeFlow: MutableStateFlow<Duration>
    private var started = false

    private fun setButtonsState(started: Boolean) {
        with(binding) {
            btnStart.isEnabled = !started
            btnStop.isEnabled = started
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val initialTime = savedInstanceState?.getLong(TIME)?.milliseconds
            ?: if (::timeFlow.isInitialized) timeFlow.value else Duration.ZERO
        timeFlow = MutableStateFlow(initialTime)
        started = savedInstanceState?.getBoolean(STARTED) ?: started
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                timeFlow.collect { binding.time.text = it.toDisplayString() }
            }
        }
        setButtonsState(started)
        with(binding) {
            time.text = initialTime.toDisplayString()
            btnStart.setOnClickListener {
                started = true
                setButtonsState(started)
                startTimer()
            }
            btnStop.setOnClickListener {
                started = false
                stopTimer()
                setButtonsState(started)
            }
        }
        if (started) startTimer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::timeFlow.isInitialized) {
            outState.putLong(TIME, timeFlow.value.inWholeMilliseconds)
        }
        outState.putBoolean(STARTED, started)
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            val initialTime = timeFlow.value
            val mark = TimeSource.Monotonic.markNow()
            while (isActive) {
                delay(50)
                timeFlow.emit(initialTime + mark.elapsedNow())
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onDestroyView() {
        stopTimer()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TIME = "time"
        private const val STARTED = "started"

        private fun Duration.toDisplayString(): String = String.format(
            Locale.getDefault(),
            "%02d:%02d.%03d",
            inWholeMinutes,
            inWholeSeconds % 60,
            inWholeMilliseconds % 1000
        )
    }
}

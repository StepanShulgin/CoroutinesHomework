package ru.otus.coroutineshomework.ui.timer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.otus.coroutineshomework.databinding.FragmentTimerBinding
import java.util.Locale
import kotlin.properties.Delegates
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class TimerFragment : Fragment() {

    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!
    private var timerJob: Job? = null

    private var time: Duration by Delegates.observable(Duration.ZERO) { _, _, newValue ->
        binding.time.text = newValue.toDisplayString()
    }

    private var started by Delegates.observable(false) { _, _, newValue ->
        setButtonsState(newValue)
        if (newValue) {
            startTimer()
        } else {
            stopTimer()
        }
    }

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
        savedInstanceState?.let {
            time = it.getLong(TIME).milliseconds
            started = it.getBoolean(STARTED)
        }
        setButtonsState(started)
        with(binding) {
            time.text = this@TimerFragment.time.toDisplayString()
            btnStart.setOnClickListener {
                started = true
            }
            btnStop.setOnClickListener {
                started = false
            }
        }
        if (started) startTimer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(TIME, time.inWholeMilliseconds)
        outState.putBoolean(STARTED, started)
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
            val initialTime = time
            val mark = TimeSource.Monotonic.markNow()
            while (isActive) {
                delay(50)
                time = initialTime + mark.elapsedNow()
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

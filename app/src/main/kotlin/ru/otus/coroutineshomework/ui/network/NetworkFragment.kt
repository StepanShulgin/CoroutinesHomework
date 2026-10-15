package ru.otus.coroutineshomework.ui.network

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ru.otus.coroutineshomework.R
import ru.otus.coroutineshomework.databinding.FragmentNetworkBinding

class NetworkFragment : Fragment() {

    private var _binding: FragmentNetworkBinding? = null
    private val binding get() = _binding!!

    private val networkViewModel by viewModels<NetworkViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNetworkBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        networkViewModel.running.observe(viewLifecycleOwner) { running ->
            binding.loading.isVisible = running
            binding.result.isVisible = !running
            binding.numOfThreadsLayout.isEnabled = !running
            binding.btnStart.isEnabled = !running
        }

        networkViewModel.result.observe(viewLifecycleOwner) { result ->
            binding.result.text = result?.let { getString(R.string.result, it.toFloat() / 1000) }
                ?: getString(R.string.no_successful_requests)
        }

        if (savedInstanceState == null) {
            binding.numOfThreads.setText(resources.getStringArray(R.array.num_of_threads)[0], false)
        }

        binding.btnStart.setOnClickListener {
            networkViewModel.startTest(binding.numOfThreads.text.toString().toIntOrNull() ?: 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

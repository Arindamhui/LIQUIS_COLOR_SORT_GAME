package com.liquidcolorsort.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.liquidcolorsort.databinding.FragmentSettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSoundEnabled(isChecked)
        }
        binding.switchMusic.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setMusicEnabled(isChecked)
        }
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setVibrationEnabled(isChecked)
        }

        binding.btnRemoveAds.setOnClickListener {
            // Stub — shows a toast until IAP is implemented
            android.widget.Toast.makeText(
                context, "Remove Ads coming soon!", android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.soundEnabled.collect { enabled ->
                        binding.switchSound.isChecked = enabled
                    }
                }
                launch {
                    viewModel.musicEnabled.collect { enabled ->
                        binding.switchMusic.isChecked = enabled
                    }
                }
                launch {
                    viewModel.vibrationEnabled.collect { enabled ->
                        binding.switchVibration.isChecked = enabled
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

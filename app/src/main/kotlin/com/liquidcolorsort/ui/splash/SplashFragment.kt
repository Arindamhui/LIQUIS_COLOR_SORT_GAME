package com.liquidcolorsort.ui.splash

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.liquidcolorsort.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen showing a loading placeholder before transitioning to Main Menu (Home).
 */
class SplashFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Auto-navigate to home screen after 1.5 seconds
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500L)
            if (isAdded) {
                findNavController().navigate(R.id.action_splash_to_home)
            }
        }
    }
}

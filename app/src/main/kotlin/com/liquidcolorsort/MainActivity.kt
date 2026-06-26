package com.liquidcolorsort

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.liquidcolorsort.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the entire application.
 *
 * Navigation is handled by the Jetpack NavController wired to the
 * [NavHostFragment] declared in [activity_main.xml].  All screens
 * (Home, Level Select, Game, Settings) are Fragments; back-stack
 * management is entirely delegated to NavController.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var adManager: com.liquidcolorsort.ads.AdService

    @javax.inject.Inject
    lateinit var musicPlayer: com.liquidcolorsort.audio.BackgroundMusicPlayer

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply window insets to the root container so that system bars
        // don't overlap game content.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // UMP / GDPR Consent check
        adManager.checkConsentAndInit(this) {
            // Initialisation complete
        }
    }

    override fun onResume() {
        super.onResume()
        musicPlayer.onActivityResume()
    }

    override fun onPause() {
        super.onPause()
        musicPlayer.onActivityPause()
    }
}

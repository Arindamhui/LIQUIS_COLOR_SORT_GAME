package com.liquidcolorsort.ui.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.liquidcolorsort.R
import com.liquidcolorsort.core.engine.SolverHint
import com.liquidcolorsort.core.model.Tube
import com.liquidcolorsort.databinding.FragmentGameBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.liquidcolorsort.ads.AdService
import javax.inject.Inject
import android.view.HapticFeedbackConstants
import com.liquidcolorsort.audio.SoundManager

/**
 * Game screen Fragment.
 *
 * Renders a dynamic grid of [TubeView]s by inflating them programmatically
 * based on the current level's tube count. Observes [GameViewModel] and
 * drives the pour animation — after which it reports animation-end back to
 * the ViewModel so input is unlocked.
 *
 * ### Animation locking contract
 * 1. User taps a tube → [GameViewModel.onTubeTapped] → [GameState] updates.
 * 2. Fragment detects the state change and identifies which tubes changed.
 * 3. Fragment starts animation, calls [GameViewModel.onAnimationStarted].
 * 4. On animation end, Fragment calls [GameViewModel.onAnimationEnded].
 * 5. ViewModel accepts input again.
 */
@AndroidEntryPoint
class GameFragment : Fragment() {

    @Inject
    lateinit var adManager: AdService

    @Inject
    lateinit var soundManager: SoundManager

    @Inject
    lateinit var networkMonitor: com.liquidcolorsort.util.NetworkMonitor

    private val viewModel: GameViewModel by viewModels()
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    /** Currently rendered [TubeView]s, indexed to match [GameState.tubes]. */
    private val tubeViews = mutableListOf<TubeView>()

    /** Snapshot of tubes from the previous state, used to detect changed tubes. */
    private var previousTubes: List<Tube> = emptyList()

    /** Full previous state snapshot to detect custom user events/actions. */
    private var previousGameState: com.liquidcolorsort.core.model.GameState? = null

    // Win overlay views (accessed via the included layout's root)
    private lateinit var winOverlay: View
    private lateinit var tvWinMoves: TextView
    private lateinit var btnNextLevel: Button
    private lateinit var btnReplayLevel: Button
    private lateinit var btnWinBack: Button

    // Deadlock overlay views
    private lateinit var deadlockOverlay: View
    private lateinit var btnDeadlockUndo: Button
    private lateinit var btnDeadlockRestart: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Wire included layout views by finding them inside the included root
        winOverlay     = binding.winOverlay.root
        tvWinMoves     = binding.winOverlay.tvWinMoves
        btnNextLevel   = binding.winOverlay.btnNextLevel
        btnReplayLevel = binding.winOverlay.btnReplayLevel
        btnWinBack     = binding.winOverlay.btnWinBack

        deadlockOverlay     = binding.deadlockOverlay.root
        btnDeadlockUndo     = binding.deadlockOverlay.btnDeadlockUndo
        btnDeadlockRestart  = binding.deadlockOverlay.btnDeadlockRestart

        setupButtonListeners()
        observeViewModel()
    }

    // ── Button wiring ──────────────────────────────────────────────────────

    private fun setupButtonListeners() {
        binding.btnUndo.setOnClickListener { viewModel.onUndoTapped() }
        binding.btnRedo.setOnClickListener { viewModel.onRedoTapped() }
        binding.btnAddTube.setOnClickListener { viewModel.onAddTubeTapped() }
        binding.btnHint.setOnClickListener { onHintRequested() }
        binding.btnRestart.setOnClickListener { viewModel.onRestartTapped() }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
    }

    // ── ViewModel observation ──────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::onUiStateChanged) }
                launch { viewModel.gameState.collect { it?.let(::onGameStateChanged) } }
                launch { viewModel.hint.collect(::onHintChanged) }
                launch { viewModel.soundEnabled.collect { soundManager.setSoundEnabled(it) } }
                launch {
                    networkMonitor.isOnline.collect { isOnline ->
                        if (!isOnline) {
                            showNoInternetDialog()
                        }
                    }
                }
                launch {
                    viewModel.level.collect { level ->
                        level?.let {
                            binding.tvLevelTitle.text = getString(R.string.level_title, it.id)
                        }
                    }
                }
            }
        }
    }

    private fun onUiStateChanged(state: GameUiState) {
        binding.progressBar.isVisible = state is GameUiState.Loading

        when (state) {
            is GameUiState.Playing        -> showPlayingUi()
            is GameUiState.Won            -> showWonDialog(state.moveCount)
            is GameUiState.Deadlocked     -> showDeadlockedDialog()
            is GameUiState.ShowingRewardedAd -> showRewardedAd()
            is GameUiState.Error          -> showError(state.message)
            GameUiState.Loading           -> Unit
        }
    }

    private fun onGameStateChanged(state: com.liquidcolorsort.core.model.GameState) {
        val newTubes = state.tubes

        // Build or update tube views
        if (tubeViews.size != newTubes.size) {
            rebuildTubeViews(
                count    = newTubes.size,
                capacity = newTubes.firstOrNull()?.capacity ?: Tube.DEFAULT_CAPACITY,
            )
        }

        // Trigger SFX & Haptics based on state transition
        val prev = previousGameState
        if (prev != null) {
            when {
                state.isSolved && !prev.isSolved -> {
                    soundManager.playWinSound()
                }
                state.moveCount > prev.moveCount -> {
                    soundManager.playPourSound()
                }
                state.moveCount < prev.moveCount -> {
                    soundManager.playUndoSound()
                }
                state.selectedTube != prev.selectedTube -> {
                    val view = view ?: binding.root
                    if (state.selectedTube != null && prev.selectedTube != null) {
                        // Reselected due to illegal move/pour attempt
                        soundManager.playErrorSound()
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    } else {
                        // Light select/deselect feedback
                        soundManager.playTapSound()
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
            }
        }

        // Detect which tubes changed (for animation)
        val changedIndices = newTubes.indices.filter { i ->
            i < previousTubes.size && newTubes[i] != previousTubes[i]
        }

        val selectedIdx = state.selectedTube

        if (changedIndices.size == 2 && previousTubes.isNotEmpty()) {
            // A pour happened — animate the destination tube
            val fromIdx = changedIndices.firstOrNull {
                it < previousTubes.size && previousTubes[it].size > newTubes[it].size
            } ?: changedIndices[0]
            val toIdx = changedIndices.firstOrNull { it != fromIdx } ?: changedIndices[1]

            viewModel.onAnimationStarted()
            tubeViews[toIdx].animatePour(
                fromSegments = previousTubes[toIdx].segments,
                toSegments   = newTubes[toIdx].segments,
                onEnd        = { viewModel.onAnimationEnded() }
            )
        }

        // Sync all tube views
        tubeViews.forEachIndexed { i, tv ->
            tv.tube       = newTubes[i]
            tv.isSelected = (i == selectedIdx)
            tv.contentDescription = getTubeAccessibilityDescription(i + 1, newTubes[i])
        }

        binding.tvMoveCount.text = getString(R.string.moves_count, state.moveCount)
        binding.btnUndo.isEnabled = state.canUndo
        binding.btnRedo.isEnabled = state.canRedo
        binding.btnAddTube.isEnabled = state.canAddExtraTube

        previousTubes = newTubes
        previousGameState = state
    }

    private fun getTubeAccessibilityDescription(index: Int, tube: Tube): String {
        if (tube.isEmpty) {
            return getString(R.string.a11y_tube_empty, index)
        }
        val colorNames = tube.segments.map { segment ->
            when (segment.id) {
                1 -> "Red"
                2 -> "Blue"
                3 -> "Green"
                4 -> "Yellow"
                5 -> "Orange"
                6 -> "Purple"
                7 -> "Cyan"
                8 -> "Pink"
                9 -> "Brown"
                10 -> "Grey"
                11 -> "Lime"
                12 -> "Rose"
                else -> "Unknown Color"
            }
        }.joinToString(", ")
        return getString(R.string.a11y_tube_colors, index, colorNames)
    }

    private fun onHintChanged(hint: SolverHint.Move?) {
        tubeViews.forEachIndexed { i, tv ->
            tv.isHintTarget = (hint != null && (i == hint.fromIdx || i == hint.toIdx))
        }
        if (hint != null) {
            // Auto-consume after 3 seconds
            binding.root.postDelayed({ viewModel.onHintConsumed() }, 3_000L)
        }
    }

    // ── Tube grid management ───────────────────────────────────────────────

    private fun rebuildTubeViews(count: Int, capacity: Int) {
        binding.tubeContainer.removeAllViews()
        tubeViews.clear()

        val displayMetrics = resources.displayMetrics
        val screenWidth    = displayMetrics.widthPixels
        val density        = displayMetrics.density

        // Multi-row responsive layout
        val cols = if (count <= 6) 3 else 4
        val rows = (count + cols - 1) / cols

        val availableWidth = screenWidth - (32 * density).toInt()
        val tubeWidth      = (availableWidth / cols) - (12 * density).toInt()
        val tubeHeight     = (tubeWidth * 2.5).toInt()
        val dp             = density
        val margin    = (6 * dp).toInt()

        repeat(count) { idx ->
            val tv = TubeView(requireContext()).apply {
                tube = Tube(capacity = capacity)
                layoutParams = ViewGroup.MarginLayoutParams(tubeWidth, tubeHeight).apply {
                    setMargins(margin, margin, margin, margin)
                }
                setOnClickListener { if (!viewModel.isAnimating.value) viewModel.onTubeTapped(idx) }
                contentDescription = getString(R.string.tube_description, idx + 1)
            }
            tubeViews.add(tv)
            binding.tubeContainer.addView(tv)
        }
    }

    // ── Dialogs / overlays ─────────────────────────────────────────────────

    private fun showPlayingUi() {
        winOverlay.visibility     = View.GONE
        deadlockOverlay.visibility = View.GONE
    }

    private fun showWonDialog(moveCount: Int) {
        winOverlay.visibility = View.VISIBLE
        tvWinMoves.text = getString(R.string.win_moves, moveCount)

        btnNextLevel.setOnClickListener {
            adManager.onLevelComplete(requireActivity(), viewModel.levelId) {
                val nextId = viewModel.levelId + 1
                val action = GameFragmentDirections.actionGameSelf(levelId = nextId)
                findNavController().navigate(action)
            }
        }
        btnReplayLevel.setOnClickListener { viewModel.onRestartTapped() }
        btnWinBack.setOnClickListener { findNavController().popBackStack() }
    }

    private fun showDeadlockedDialog() {
        deadlockOverlay.visibility = View.VISIBLE
        btnDeadlockUndo.setOnClickListener {
            deadlockOverlay.visibility = View.GONE
            viewModel.onUndoTapped()
        }
        btnDeadlockRestart.setOnClickListener {
            deadlockOverlay.visibility = View.GONE
            viewModel.onRestartTapped()
        }
    }

    private fun onHintRequested() {
        viewModel.onHintRequested()
        showRewardedAd()
    }

    private fun showRewardedAd() {
        adManager.showRewarded(
            requireActivity(),
            onRewarded = { viewModel.grantHint() },
            onDismissed = {}
        )
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun showNoInternetDialog() {
        val existing = childFragmentManager.findFragmentByTag("NoInternetDialog")
        if (existing == null) {
            com.liquidcolorsort.ui.dialog.NoInternetDialogFragment()
                .show(childFragmentManager, "NoInternetDialog")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        tubeViews.clear()
    }
}

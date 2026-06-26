package com.liquidcolorsort.ui.levelselect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.liquidcolorsort.R
import com.liquidcolorsort.ads.AdService
import com.liquidcolorsort.databinding.FragmentLevelSelectBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LevelSelectFragment : Fragment() {

    private val viewModel: LevelSelectViewModel by viewModels()
    private var _binding: FragmentLevelSelectBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var adManager: AdService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLevelSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adManager.loadBanner(binding.bannerContainer)

        // Responsive grid: 5 columns on tablets (sw600dp), 3 on phones
        val columns = resources.getInteger(R.integer.level_grid_columns)
        val adapter = LevelAdapter { levelId ->
            val action = LevelSelectFragmentDirections.actionLevelSelectToGame(levelId)
            findNavController().navigate(action)
        }
        binding.recyclerLevels.layoutManager = GridLayoutManager(context, columns)
        binding.recyclerLevels.adapter = adapter

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.isLoading.collect { binding.progressBar.isVisible = it } }
                launch { viewModel.levels.collect { adapter.submitList(it) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ── RecyclerView Adapter ──────────────────────────────────────────────────────

class LevelAdapter(
    private val onLevelClicked: (Int) -> Unit,
) : RecyclerView.Adapter<LevelAdapter.LevelVH>() {

    private var items: List<LevelSelectViewModel.LevelItem> = emptyList()

    fun submitList(newItems: List<LevelSelectViewModel.LevelItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_level, parent, false)
        return LevelVH(v)
    }

    override fun onBindViewHolder(holder: LevelVH, position: Int) {
        holder.bind(items[position], onLevelClicked)
    }

    override fun getItemCount(): Int = items.size

    class LevelVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvId: TextView   = itemView.findViewById(R.id.tv_level_id)
        private val tvStars: TextView = itemView.findViewById(R.id.tv_stars)

        fun bind(item: LevelSelectViewModel.LevelItem, onClick: (Int) -> Unit) {
            val id       = item.summary.id
            val progress = item.progress
            tvId.text    = id.toString()
            tvStars.text = when (progress.stars) {
                3    -> "⭐⭐⭐"
                2    -> "⭐⭐"
                1    -> "⭐"
                else -> ""
            }
            itemView.alpha = if (progress.isUnlocked) 1f else 0.4f
            itemView.isClickable = progress.isUnlocked
            if (progress.isUnlocked) {
                itemView.setOnClickListener { onClick(id) }
            }
        }
    }
}

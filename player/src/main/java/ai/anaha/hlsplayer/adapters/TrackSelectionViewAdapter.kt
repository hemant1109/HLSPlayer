package ai.anaha.hlsplayer.adapters

import ai.anaha.hlsplayer.R
import ai.anaha.hlsplayer.fragments.TrackSelectionFragment
import ai.anaha.hlsplayer.hlsutils.PlayerSettingsDialog.OnCustomTrackSelectedListener
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.ui.TrackSelectionView.TrackSelectionListener
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import java.util.*

class TrackSelectionViewAdapter(
    private val mappedTrackInfo: MappedTrackInfo,
    private val trackNames: ArrayList<String>,
    private val trackInfos: ArrayList<TrackSelectionFragment.TrackInfo?>,
    private val allowMultipleOverrides: Boolean,
    overrides: List<SelectionOverride>,
    private val onCustomTrackSelectedListener: OnCustomTrackSelectedListener,
    listener: TrackSelectionListener?
) : RecyclerView.Adapter<TrackSelectionViewAdapter.SubSettingViewHolder>() {
    private val listener: TrackSelectionListener?
    private var selectedIndex = 0
    private val overrides: SparseArray<SelectionOverride?> = SparseArray()
    private var isDisabled = false
    private var lastCheckedView: View? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubSettingViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(
                R.layout.custom_exo_styled_track_list_item, parent,  /* attachToRoot= */false
            )
        return SubSettingViewHolder(v)
    }

    override fun onBindViewHolder(holder: SubSettingViewHolder, position: Int) {
        if (position < itemCount) {
            holder.textView.text = trackNames[position]
        }
        if (getItemViewType(position) == VIEW_AUTO) {
            if (selectedIndex == position && overrides[0] == null) {
                holder.checkView.visibility = View.VISIBLE
                lastCheckedView = holder.checkView
            } else {
                holder.checkView.visibility = View.INVISIBLE
            }
            holder.itemView.setOnClickListener {
                if (position != selectedIndex) {
                    lastCheckedView!!.visibility = View.INVISIBLE
                    holder.checkView.visibility = View.VISIBLE
                    onDefaultViewClicked()
                    listener!!.onTrackSelectionChanged(isDisabled, getOverrides())
                    onCustomTrackSelectedListener.onTrackSelected()
                }
            }
        } else {
            holder.checkView.tag = trackInfos[position]
            holder.itemView.setOnClickListener {
                if (position != selectedIndex) {
                    onTrackViewClicked(holder.checkView)
                    listener!!.onTrackSelectionChanged(isDisabled, getOverrides())
                    onCustomTrackSelectedListener.onTrackSelected()
                    lastCheckedView!!.visibility = View.INVISIBLE
                    holder.checkView.visibility = View.VISIBLE
                }
            }
            val override = overrides[0]
            if (override != null) {
                val trackInfo = Assertions.checkNotNull(
                    trackInfos[position]
                )
                if (override.containsTrack(trackInfo.trackIndex)) {
                    selectedIndex = holder.absoluteAdapterPosition
                    holder.checkView.visibility = View.VISIBLE
                    lastCheckedView = holder.checkView
                } else holder.checkView.visibility = View.INVISIBLE
            } else {
                holder.checkView.visibility = View.INVISIBLE
            }
        }
    }

    private fun onDefaultViewClicked() {
        isDisabled = false
        overrides.clear()
    }

    private fun getOverrides(): MutableList<SelectionOverride> {
        val overrideList: MutableList<SelectionOverride> = mutableListOf()
        for (i in 0 until overrides.size()) {
            overrides.valueAt(i)?.let { overrideList.add(it) }
        }
        return overrideList
    }

    private fun onTrackViewClicked(view: View) {
        isDisabled = false
        val trackInfo = Assertions.checkNotNull(view.tag) as TrackSelectionFragment.TrackInfo
        val groupIndex = trackInfo.groupIndex
        val trackIndex = trackInfo.trackIndex
        val override = overrides[groupIndex]
        Assertions.checkNotNull(mappedTrackInfo)
        if (override == null) {
            // Start new override.
            if (!allowMultipleOverrides && overrides.size() > 0) {
                // Removed other overrides if we don't allow multiple overrides.
                overrides.clear()
            }
            overrides.put(groupIndex, SelectionOverride(groupIndex, trackIndex))
        } else {
            // An existing override is being modified.
            val overrideLength = override.length
            val overrideTracks = override.tracks
            val isCurrentlySelected = view.visibility == View.VISIBLE
            if (isCurrentlySelected) {
                // Remove the track from the override.
                if (overrideLength == 1) {
                    // The last track is being removed, so the override becomes empty.
                    overrides.remove(groupIndex)
                } else {
                    val tracks = getTracksRemoving(overrideTracks, trackIndex)
                    overrides.put(groupIndex, SelectionOverride(groupIndex, *tracks))
                }
            } else {
                // Replace existing track in override.
                overrides.put(groupIndex, SelectionOverride(groupIndex, trackIndex))
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_AUTO else VIEW_TRACK
    }

    override fun getItemCount(): Int {
        return trackNames.size
    }

    class SubSettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView
        val checkView: View

        init {
            if (Util.SDK_INT < 26) {
                // Workaround for https://github.com/google/ExoPlayer/issues/9061.
                itemView.isFocusable = true
            }
            textView = itemView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_text)
            checkView = itemView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_check)
        }
    }

    companion object {
        private const val VIEW_AUTO = 522
        private const val VIEW_TRACK = 523
        private fun getTracksRemoving(tracks: IntArray, removedTrack: Int): IntArray {
            val newTracks = IntArray(tracks.size - 1)
            var trackCount = 0
            for (track in tracks) {
                if (track != removedTrack) {
                    newTracks[trackCount++] = track
                }
            }
            return newTracks
        }
    }

    init {
        val maxOverrides =
            if (allowMultipleOverrides) overrides.size else Math.min(overrides.size, 1)
        for (i in 0 until maxOverrides) {
            val override = overrides[i]
            this.overrides.put(override.groupIndex, override)
        }
        this.listener = listener
    }
}
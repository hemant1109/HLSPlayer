package ai.anaha.hlsplayer.fragments

import ai.anaha.hlsplayer.R
import ai.anaha.hlsplayer.adapters.TrackSelectionViewAdapter
import ai.anaha.hlsplayer.hlsutils.PlayerSettingsDialog.OnCustomTrackSelectedListener
import ai.anaha.hlsplayer.hlsutils.getColorFromAttr
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import java.util.*
import kotlin.Comparator

class TrackSelectionFragment : Fragment() {
    private lateinit var mappedTrackInfo: MappedTrackInfo
    private var rendererIndex = 0
    private var allowMultipleOverrides = false
    private lateinit var dismissListener: OnCustomTrackSelectedListener

    /* package */
    var isDisabled = false

    /* package */
    lateinit var overrides: List<SelectionOverride>
    private lateinit var trackInfos: ArrayList<TrackInfo?>
    private lateinit var trackNames: ArrayList<String>
    private var trackInfoComparator: Comparator<TrackInfo?>? = null
    private var title: String? = null
    private var tvTitle: TextView? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(
            R.layout.custom_exo_track_selection_dialog, container,  /* attachToRoot= */false
        )
        tvTitle = rootView.findViewById(R.id.tvTitle)
        val tempTitle =
            String.format("%s   •   %s", getString(R.string.quality_for_current_video_u2022), title)
        val startIndex = tempTitle.indexOf(title!!)
        val spannable = SpannableString(tempTitle)
        spannable.setSpan(
            ForegroundColorSpan(
                requireContext().getColorFromAttr(
                    R.attr.coloredTextColor,
                    TypedValue(),
                    true
                )
            ),
            startIndex, startIndex + title!!.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvTitle?.text = spannable
        val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
        for (groupIndex in 0 until trackGroups.length) {
            val group = trackGroups[groupIndex]
            trackInfos = ArrayList()
            trackNames = ArrayList()
            for (trackIndex in 0 until group.length) {
                trackInfos.add(TrackInfo(groupIndex, trackIndex, group.getFormat(trackIndex)))
            }
            trackInfoComparator?.let {
                trackInfos.sortWith(it)
            }
            for (trackIndex in trackInfos.indices) {
                trackNames.add(trackInfos[trackIndex]?.format?.height?.toString() + "p")
            }
            trackInfos.add(0, null)
            trackNames.add(
                0,
                getString(com.google.android.exoplayer2.ui.R.string.exo_track_selection_auto)
            )
        }
        val playbackSpeedAdapter = TrackSelectionViewAdapter(
            mappedTrackInfo,
            trackNames, trackInfos, allowMultipleOverrides, overrides,
            dismissListener
        ) { isDisabled: Boolean, overrides: List<SelectionOverride> ->
            this@TrackSelectionFragment.isDisabled = isDisabled
            this@TrackSelectionFragment.overrides = overrides
        }
        val settingsView: RecyclerView = rootView.findViewById(R.id.exo_settings_listview)
        settingsView.adapter = playbackSpeedAdapter
        settingsView.layoutManager = LinearLayoutManager(context)
        settingsView.suppressLayout(false)
        return rootView
    }

    fun init(
        mappedTrackInfo: MappedTrackInfo,
        rendererIndex: Int,
        initialIsDisabled: Boolean,
        initialOverride: SelectionOverride?,
        allowMultipleOverrides: Boolean,
        listener: OnCustomTrackSelectedListener, trackFormatComparator: Comparator<Format?>?
    ) {
        this.mappedTrackInfo = mappedTrackInfo
        this.rendererIndex = rendererIndex
        isDisabled = initialIsDisabled
        overrides = initialOverride?.let { listOf(it) } ?: emptyList()
        this.allowMultipleOverrides = allowMultipleOverrides
        dismissListener = listener
        trackInfoComparator =
            if (trackFormatComparator == null) null else Comparator { o1: TrackInfo?, o2: TrackInfo? ->
                trackFormatComparator.compare(o1?.format, o2?.format)
            }
    }

    fun setTitle(strTitle: String?) {
        title = strTitle
    }

    data class TrackInfo(val groupIndex: Int, val trackIndex: Int, val format: Format)
}
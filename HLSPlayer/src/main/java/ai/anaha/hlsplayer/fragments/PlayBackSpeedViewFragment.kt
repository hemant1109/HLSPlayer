package ai.anaha.hlsplayer.fragments

import ai.anaha.hlsplayer.HLSPlayerActivity.PlaybackSpeedListener
import ai.anaha.hlsplayer.R
import ai.anaha.hlsplayer.adapters.PlaybackSpeedAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlayBackSpeedViewFragment(
    private val playbackSpeed: Float,
    private val playbackSpeedListener: PlaybackSpeedListener
) : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(
            R.layout.custom_exo_track_selection_dialog, container,  /* attachToRoot= */false
        )
        rootView.findViewById<View>(R.id.tvTitle).visibility = View.GONE
        val playbackSpeedAdapter = PlaybackSpeedAdapter(
            container!!.context.resources.getStringArray(R.array.exo_playback_speeds),
            container.context.resources.getIntArray(R.array.exo_speed_multiplied_by_100),
            playbackSpeedListener
        )
        playbackSpeedAdapter.updateSelectedIndex(playbackSpeed)
        val settingsView: RecyclerView = rootView.findViewById(R.id.exo_settings_listview)
        settingsView.adapter = playbackSpeedAdapter
        settingsView.layoutManager = LinearLayoutManager(context)
        settingsView.suppressLayout(false)
        return rootView
    }
}
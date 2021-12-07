package ai.anaha.hlsplayer.fragments

import ai.anaha.hlsplayer.R
import ai.anaha.hlsplayer.adapters.SettingsAdapter
import ai.anaha.hlsplayer.hlsutils.PlayerSettingsDialog.SettingItemClickListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

class SettingsFragment(
    private val settingItemClickListener: SettingItemClickListener,
    private val strVideoQuality: String,
    private val playbackSpeed: Float
) : Fragment() {

    private lateinit var playbackSpeedTexts: Array<String>
    private lateinit var playbackSpeedsMultBy100: IntArray

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(
            R.layout.custom_exo_setting_selection_dialog, container,  /* attachToRoot= */false
        )
        playbackSpeedTexts =
            container!!.context.resources.getStringArray(R.array.exo_playback_speeds)
        playbackSpeedsMultBy100 =
            container.context.resources.getIntArray(R.array.exo_speed_multiplied_by_100)
        val playbackSpeedAdapter = SettingsAdapter(
            container.context.resources.getStringArray(R.array.exo_settings), strVideoQuality,
            container.context.resources.obtainTypedArray(R.array.exo_settings_icons),
            settingItemClickListener, playbackSpeedTexts[getSelectedIndex(playbackSpeed)]
        )
        val settingsView: RecyclerView = rootView.findViewById(R.id.exo_settings_listview)
        settingsView.adapter = playbackSpeedAdapter
        settingsView.layoutManager = LinearLayoutManager(context)
        settingsView.suppressLayout(false)
        return rootView
    }

    private fun getSelectedIndex(playbackSpeed: Float): Int {
        val currentSpeedMultBy100 = (playbackSpeed * 100).roundToInt()
        var closestMatchIndex = 0
        var closestMatchDifference = Int.MAX_VALUE
        for (i in playbackSpeedsMultBy100.indices) {
            val difference = abs(currentSpeedMultBy100 - playbackSpeedsMultBy100[i])
            if (difference < closestMatchDifference) {
                closestMatchIndex = i
                closestMatchDifference = difference
            }
        }
        return closestMatchIndex
    }
}
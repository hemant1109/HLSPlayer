package ai.anaha.player.adapters

import ai.anaha.player.PlayerActivity.PlaybackSpeedListener
import ai.anaha.player.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.util.Util
import kotlin.math.roundToInt

class PlaybackSpeedAdapter(
    private val playbackSpeedTexts: Array<String>,
    private val playbackSpeedsMultBy100: IntArray,
    private val playbackSpeedListener: PlaybackSpeedListener
) : RecyclerView.Adapter<PlaybackSpeedAdapter.SubSettingViewHolder>() {
    private var selectedIndex = 0
    fun updateSelectedIndex(playbackSpeed: Float) {
        val currentSpeedMultBy100 = (playbackSpeed * 100).roundToInt()
        var closestMatchIndex = 0
        var closestMatchDifference = Int.MAX_VALUE
        for (i in playbackSpeedsMultBy100.indices) {
            val difference = Math.abs(currentSpeedMultBy100 - playbackSpeedsMultBy100[i])
            if (difference < closestMatchDifference) {
                closestMatchIndex = i
                closestMatchDifference = difference
            }
        }
        selectedIndex = closestMatchIndex
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubSettingViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.custom_exo_styled_track_list_item, parent,  /* attachToRoot= */false)
        return SubSettingViewHolder(v)
    }

    override fun onBindViewHolder(holder: SubSettingViewHolder, position: Int) {
        if (position < playbackSpeedTexts.size) {
            holder.textView.text = playbackSpeedTexts[position]
        }
        holder.checkView.visibility =
            if (position == selectedIndex) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener {
            if (position != selectedIndex) {
                val speed = playbackSpeedsMultBy100[position] / 100.0f
                playbackSpeedListener.setPlaybackSpeed(speed)
            }
        }
    }

    override fun getItemCount(): Int {
        return playbackSpeedTexts.size
    }

    class SubSettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView
        val checkView: View

        init {
            if (Util.SDK_INT < 26) {
                // Workaround for https://github.com/google/ExoPlayer/issues/9061.
                itemView.isFocusable = true
            }
            textView = itemView.findViewById(R.id.exo_text)
            checkView = itemView.findViewById(R.id.exo_check)
        }
    }
}
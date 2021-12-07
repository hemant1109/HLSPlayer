package ai.anaha.player.adapters

import ai.anaha.player.R
import ai.anaha.player.utils.PlayerSettingsDialog.SettingItemClickListener
import ai.anaha.player.utils.getColorFromAttr
import android.content.res.TypedArray
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.util.Util

class SettingsAdapter(
    private val settingNames: Array<String>,
    private val strVideoQuality: String,
    private val settingsIcons: TypedArray,
    private val settingItemClickListener: SettingItemClickListener,
    private val selectedPlaybackSpeedTexts: String
) : RecyclerView.Adapter<SettingsAdapter.SubSettingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubSettingViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.custom_settings_list_item, parent,  /* attachToRoot= */false)
        return SubSettingViewHolder(v)
    }

    override fun onBindViewHolder(holder: SubSettingViewHolder, position: Int) {
        if (position < settingNames.size) {
            when {
                settingNames[position] == holder.textView.context.getString(R.string.quality) -> {
                    holder.textView.text =
                        getSpannableString(holder, settingNames[position], strVideoQuality)
                }
                settingNames[position] == holder.textView.context.getString(R.string.playback_speed) -> {
                    holder.textView.text =
                        getSpannableString(
                            holder,
                            settingNames[position],
                            selectedPlaybackSpeedTexts
                        )
                }
                else -> holder.textView.text = settingNames[position]
            }
        }
        holder.checkImageView.visibility = View.VISIBLE
        holder.checkImageView.setImageResource(settingsIcons.getResourceId(position, 0))
        holder.itemView.setOnClickListener {
            settingItemClickListener.settingItemClick(
                holder.absoluteAdapterPosition
            )
        }
    }

    private fun getSpannableString(
        holder: SubSettingViewHolder,
        settingName: String,
        coloredText: String
    ): SpannableString {
        val tempStr = String.format("%s   •   %s", settingName, coloredText)
        val startIndex = tempStr.indexOf(coloredText)
        val spannable = SpannableString(tempStr)
        spannable.setSpan(
            ForegroundColorSpan(holder.textView.context.getColorFromAttr(R.attr.coloredTextColor)),
            startIndex, startIndex + coloredText.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    override fun getItemCount(): Int {
        return settingNames.size
    }

    class SubSettingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView
        val checkImageView: ImageView

        init {
            if (Util.SDK_INT < 26) {
                // Workaround for https://github.com/google/ExoPlayer/issues/9061.
                itemView.isFocusable = true
            }
            textView = itemView.findViewById(R.id.exo_setting_name)
            checkImageView = itemView.findViewById(R.id.exo_setting_icon)
        }
    }
}
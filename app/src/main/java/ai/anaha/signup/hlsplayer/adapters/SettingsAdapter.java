package ai.anaha.signup.hlsplayer.adapters;

import android.content.res.TypedArray;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Util;

import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.hlsutils.PlayerSettingsDialog;

public final class SettingsAdapter extends RecyclerView.Adapter<SettingsAdapter.SubSettingViewHolder> {

    private final String[] settingNames;
    private final TypedArray settingsIcons;
    private final PlayerSettingsDialog.SettingItemClickListener settingItemClickListener;
    private final String strVideoQuality;
    private final String selectedPlaybackSpeedTexts;

    public SettingsAdapter(String[] settingNames, String strVideoQuality, TypedArray settingsIcons,
                           PlayerSettingsDialog.SettingItemClickListener settingItemClickListener, String selectedPlaybackSpeedTexts) {
        this.settingNames = settingNames;
        this.strVideoQuality = strVideoQuality;
        this.settingsIcons = settingsIcons;
        this.selectedPlaybackSpeedTexts = selectedPlaybackSpeedTexts;
        this.settingItemClickListener = settingItemClickListener;
    }

    @Override
    public SubSettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.custom_settings_list_item, parent, /* attachToRoot= */ false);
        return new SubSettingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SubSettingViewHolder holder, int position) {
        if (position < settingNames.length) {
            if (position == 1) {
                holder.textView.setText(getSpannableString(holder, settingNames[position], strVideoQuality));
            } else if (position == 2) {
                holder.textView.setText(getSpannableString(holder, settingNames[position], selectedPlaybackSpeedTexts));
            } else
                holder.textView.setText(settingNames[position]);
        }
        holder.checkImageView.setVisibility(View.VISIBLE);
        holder.checkImageView.setImageResource(settingsIcons.getResourceId(position, 0));
        holder.itemView.setOnClickListener(
                v -> {
                    settingItemClickListener.settingItemClick(holder.getAdapterPosition());
                });
    }

    @NonNull
    private SpannableString getSpannableString(@NonNull SubSettingViewHolder holder, String settingName, String coloredText) {
        String tempStr = String.format("%s   •   %s", settingName, coloredText);
        int startIndex = tempStr.indexOf(coloredText);
        SpannableString spannable = new SpannableString(tempStr);
        spannable.setSpan(
                new ForegroundColorSpan(holder.textView.getContext().getColor(R.color.exo_white_opacity_50)),
                startIndex, startIndex + coloredText.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @Override
    public int getItemCount() {
        return settingNames.length;
    }

    protected static class SubSettingViewHolder extends RecyclerView.ViewHolder {

        public final TextView textView;
        public final ImageView checkImageView;

        public SubSettingViewHolder(View itemView) {
            super(itemView);
            if (Util.SDK_INT < 26) {
                // Workaround for https://github.com/google/ExoPlayer/issues/9061.
                itemView.setFocusable(true);
            }
            textView = itemView.findViewById(R.id.exo_setting_name);
            checkImageView = itemView.findViewById(R.id.exo_setting_icon);
        }
    }
}
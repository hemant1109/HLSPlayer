package ai.anaha.signup.hlsplayer.adapters;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Util;

import ai.anaha.signup.hlsplayer.AnahaPlayerActivity;
import ai.anaha.signup.hlsplayer.R;

public final class PlaybackSpeedAdapter extends RecyclerView.Adapter<PlaybackSpeedAdapter.SubSettingViewHolder> {

    private final String[] playbackSpeedTexts;
    private final int[] playbackSpeedsMultBy100;
    private final AnahaPlayerActivity.PlaybackSpeedListener playbackSpeedListener;
    private int selectedIndex;

    public PlaybackSpeedAdapter(String[] playbackSpeedTexts, int[] playbackSpeedsMultBy100, AnahaPlayerActivity.PlaybackSpeedListener playbackSpeedListener) {
        this.playbackSpeedTexts = playbackSpeedTexts;
        this.playbackSpeedsMultBy100 = playbackSpeedsMultBy100;
        this.playbackSpeedListener = playbackSpeedListener;
    }

    public void updateSelectedIndex(float playbackSpeed) {
        int currentSpeedMultBy100 = Math.round(playbackSpeed * 100);
        int closestMatchIndex = 0;
        int closestMatchDifference = Integer.MAX_VALUE;
        for (int i = 0; i < playbackSpeedsMultBy100.length; i++) {
            int difference = Math.abs(currentSpeedMultBy100 - playbackSpeedsMultBy100[i]);
            if (difference < closestMatchDifference) {
                closestMatchIndex = i;
                closestMatchDifference = difference;
            }
        }
        selectedIndex = closestMatchIndex;
    }

    @Override
    public SubSettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.custom_exo_styled_track_list_item, parent, /* attachToRoot= */ false);
        return new SubSettingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SubSettingViewHolder holder, int position) {
        if (position < playbackSpeedTexts.length) {
            holder.textView.setText(playbackSpeedTexts[position]);
        }
        holder.checkView.setVisibility(position == selectedIndex ? VISIBLE : INVISIBLE);
        holder.itemView.setOnClickListener(
                v -> {
                    if (position != selectedIndex) {
                        float speed = playbackSpeedsMultBy100[position] / 100.0f;
                        playbackSpeedListener.setPlaybackSpeed(speed);
                    }
                });
    }

    @Override
    public int getItemCount() {
        return playbackSpeedTexts.length;
    }

    protected static class SubSettingViewHolder extends RecyclerView.ViewHolder {

        public final TextView textView;
        public final View checkView;

        public SubSettingViewHolder(View itemView) {
            super(itemView);
            if (Util.SDK_INT < 26) {
                // Workaround for https://github.com/google/ExoPlayer/issues/9061.
                itemView.setFocusable(true);
            }
            textView = itemView.findViewById(R.id.exo_text);
            checkView = itemView.findViewById(R.id.exo_check);
        }
    }
}
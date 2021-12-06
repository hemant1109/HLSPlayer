package ai.anaha.signup.hlsplayer.adapters;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.TrackSelectionView;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.List;

import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.fragments.TrackSelectionFragment;
import ai.anaha.signup.hlsplayer.hlsutils.PlayerSettingsDialog;

public final class TrackSelectionViewAdapter extends RecyclerView.Adapter<TrackSelectionViewAdapter.SubSettingViewHolder> {

    private final ArrayList<String> trackNames;
    private final PlayerSettingsDialog.OnCustomTrackSelectedListener onCustomTrackSelectedListener;
    private final ArrayList<TrackSelectionFragment.TrackInfo> trackInfos;
    private final TrackSelectionView.TrackSelectionListener listener;
    private final MappingTrackSelector.MappedTrackInfo mappedTrackInfo;
    private final boolean allowMultipleOverrides;
    private int selectedIndex = 0;
    private final SparseArray<DefaultTrackSelector.SelectionOverride> overrides;
    private boolean isDisabled = false;
    private static final int VIEW_AUTO = 522;
    private static final int VIEW_TRACK = 523;
    private View lastCheckedView;

    public TrackSelectionViewAdapter(MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
                                     ArrayList<String> trackNames, ArrayList<TrackSelectionFragment.TrackInfo> trackInfos, boolean allowMultipleOverrides,
                                     List<DefaultTrackSelector.SelectionOverride> overrides, PlayerSettingsDialog.OnCustomTrackSelectedListener onCustomTrackSelectedListener,
                                     @Nullable TrackSelectionView.TrackSelectionListener listener) {
        this.mappedTrackInfo = mappedTrackInfo;
        this.trackNames = trackNames;
        this.trackInfos = trackInfos;
        this.onCustomTrackSelectedListener = onCustomTrackSelectedListener;
        this.overrides = new SparseArray<>();
        this.allowMultipleOverrides = allowMultipleOverrides;
        int maxOverrides = allowMultipleOverrides ? overrides.size() : Math.min(overrides.size(), 1);
        for (int i = 0; i < maxOverrides; i++) {
            DefaultTrackSelector.SelectionOverride override = overrides.get(i);
            this.overrides.put(override.groupIndex, override);
        }
        this.listener = listener;
    }

    /*public void updateSelectedIndex(float playbackSpeed) {
        int currentSpeedMultBy100 = Math.round(playbackSpeed * 100);
        int closestMatchIndex = 0;
        int closestMatchDifference = Integer.MAX_VALUE;
        for (int i = 0; i < trackNames.length; i++) {
            int difference = Math.abs(currentSpeedMultBy100 - trackNames[i]);
            if (difference < closestMatchDifference) {
                closestMatchIndex = i;
                closestMatchDifference = difference;
            }
        }
        selectedIndex = closestMatchIndex;
    }*/

    @Override
    public SubSettingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v =
                LayoutInflater.from(parent.getContext())
                        .inflate(
                                R.layout.custom_exo_styled_track_list_item, parent, /* attachToRoot= */ false);
        return new SubSettingViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull SubSettingViewHolder holder, int position) {
        if (position < getItemCount()) {
            holder.textView.setText(trackNames.get(position));
        }
        if (getItemViewType(position) == VIEW_AUTO) {
            if (selectedIndex == position && overrides.get(0) == null) {
                holder.checkView.setVisibility(VISIBLE);
                lastCheckedView = holder.checkView;
            } else {
                holder.checkView.setVisibility(INVISIBLE);
            }
            /*((LinearLayout) holder.itemView).addView(LayoutInflater.from(holder.itemView.getContext())
                    .inflate(R.layout.exo_list_divider, (LinearLayout) holder.itemView, false));*/
            holder.itemView.setOnClickListener(
                    v -> {
                        if (position != selectedIndex) {
                            lastCheckedView.setVisibility(INVISIBLE);
                            holder.checkView.setVisibility(VISIBLE);
                            onDefaultViewClicked();
                            listener.onTrackSelectionChanged(isDisabled, getOverrides());
                            onCustomTrackSelectedListener.onTrackSelected();
                        }
                    });
        } else {
            holder.checkView.setTag(trackInfos.get(position));
            holder.itemView.setOnClickListener(
                    v -> {
                        if (position != selectedIndex) {
                            onTrackViewClicked(holder.checkView);
                            listener.onTrackSelectionChanged(isDisabled, getOverrides());
                            onCustomTrackSelectedListener.onTrackSelected();
                            lastCheckedView.setVisibility(INVISIBLE);
                            holder.checkView.setVisibility(VISIBLE);
                        }
                    });

            DefaultTrackSelector.SelectionOverride override = overrides.get(0);
            if (override != null) {
                TrackSelectionFragment.TrackInfo trackInfo = Assertions.checkNotNull(trackInfos.get(position));
                if (override.containsTrack(trackInfo.trackIndex)) {
                    selectedIndex = holder.getAdapterPosition();
                    holder.checkView.setVisibility(VISIBLE);
                    lastCheckedView = holder.checkView;
                } else
                    holder.checkView.setVisibility(INVISIBLE);
            } else {
                holder.checkView.setVisibility(INVISIBLE);
            }
        }

    }

    private void onDefaultViewClicked() {
        isDisabled = false;
        overrides.clear();
    }

    public List<DefaultTrackSelector.SelectionOverride> getOverrides() {
        List<DefaultTrackSelector.SelectionOverride> overrideList = new ArrayList<>(overrides.size());
        for (int i = 0; i < overrides.size(); i++) {
            overrideList.add(overrides.valueAt(i));
        }
        return overrideList;
    }

    private void onTrackViewClicked(View view) {
        isDisabled = false;
        TrackSelectionFragment.TrackInfo trackInfo = (TrackSelectionFragment.TrackInfo) Assertions.checkNotNull(view.getTag());
        int groupIndex = trackInfo.groupIndex;
        int trackIndex = trackInfo.trackIndex;
        DefaultTrackSelector.SelectionOverride override = overrides.get(groupIndex);
        Assertions.checkNotNull(mappedTrackInfo);
        if (override == null) {
            // Start new override.
            if (!allowMultipleOverrides && overrides.size() > 0) {
                // Removed other overrides if we don't allow multiple overrides.
                overrides.clear();
            }
            overrides.put(groupIndex, new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex));
        } else {
            // An existing override is being modified.
            int overrideLength = override.length;
            int[] overrideTracks = override.tracks;
            boolean isCurrentlySelected = view.getVisibility() == VISIBLE;
            if (isCurrentlySelected) {
                // Remove the track from the override.
                if (overrideLength == 1) {
                    // The last track is being removed, so the override becomes empty.
                    overrides.remove(groupIndex);
                } else {
                    int[] tracks = getTracksRemoving(overrideTracks, trackIndex);
                    overrides.put(groupIndex, new DefaultTrackSelector.SelectionOverride(groupIndex, tracks));
                }
            } else {
                // Replace existing track in override.
                overrides.put(groupIndex, new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex));
            }
        }
    }


    private static int[] getTracksRemoving(int[] tracks, int removedTrack) {
        int[] newTracks = new int[tracks.length - 1];
        int trackCount = 0;
        for (int track : tracks) {
            if (track != removedTrack) {
                newTracks[trackCount++] = track;
            }
        }
        return newTracks;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? VIEW_AUTO : VIEW_TRACK;
    }

    @Override
    public int getItemCount() {
        return trackNames.size();
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
            textView = itemView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_text);
            checkView = itemView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_check);
        }
    }
}
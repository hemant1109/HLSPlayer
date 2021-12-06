package ai.anaha.signup.hlsplayer.fragments;

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.adapters.TrackSelectionViewAdapter;
import ai.anaha.signup.hlsplayer.hlsutils.PlayerSettingsDialog;

public final class TrackSelectionFragment extends Fragment {

    private MappingTrackSelector.MappedTrackInfo mappedTrackInfo;
    private int rendererIndex;
    private boolean allowMultipleOverrides;
    PlayerSettingsDialog.OnCustomTrackSelectedListener dismissListener;
    /* package */ public boolean isDisabled;
    /* package */ public List<DefaultTrackSelector.SelectionOverride> overrides;
    ArrayList<TrackInfo> trackInfos;
    ArrayList<String> trackNames;
    private Comparator<TrackInfo> trackInfoComparator;
    private String title;
    private TextView tvTitle;

    public TrackSelectionFragment() {
        // Retain instance across activity re-creation to prevent losing access to init data.
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView =
                inflater.inflate(
                        R.layout.custom_exo_track_selection_dialog, container, /* attachToRoot= */ false);
        tvTitle = rootView.findViewById(R.id.tvTitle);
        String tempTitle = String.format("%s   •   %s",getString(R.string.quality_for_current_video_u2022), title);
        int startIndex = tempTitle.indexOf(title);
        SpannableString spannable = new SpannableString(tempTitle);
        spannable.setSpan(
                new ForegroundColorSpan(requireContext().getColor(R.color.exo_white_opacity_50)),
                startIndex, startIndex + title.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvTitle.setText(spannable);

        TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
            TrackGroup group = trackGroups.get(groupIndex);
            trackInfos = new ArrayList<>();
            trackNames = new ArrayList<>();
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                trackInfos.add(new TrackInfo(groupIndex, trackIndex, group.getFormat(trackIndex)));
            }
            if (trackInfoComparator != null) {
                Collections.sort(trackInfos, trackInfoComparator);
            }
            for (int trackIndex = 0; trackIndex < trackInfos.size(); trackIndex++) {
                trackNames.add(trackInfos.get(trackIndex).format.height + "p");
            }
            trackInfos.add(0, null);
            trackNames.add(0, getString(com.google.android.exoplayer2.ui.R.string.exo_track_selection_auto));
        }

        TrackSelectionViewAdapter playbackSpeedAdapter = new TrackSelectionViewAdapter(mappedTrackInfo,
                trackNames, trackInfos, allowMultipleOverrides, overrides,
                dismissListener, (isDisabled, overrides) -> {
            TrackSelectionFragment.this.isDisabled = isDisabled;
            TrackSelectionFragment.this.overrides = overrides;
        });
        RecyclerView settingsView = rootView.findViewById(R.id.exo_settings_listview);
        settingsView.setAdapter(playbackSpeedAdapter);
        settingsView.setLayoutManager(new LinearLayoutManager(getContext()));
        settingsView.suppressLayout(false);
        return rootView;
    }

    public void init(
            MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
            int rendererIndex,
            boolean initialIsDisabled,
            @Nullable DefaultTrackSelector.SelectionOverride initialOverride,
            boolean allowAdaptiveSelections,
            boolean allowMultipleOverrides, PlayerSettingsDialog.OnCustomTrackSelectedListener listener,
            Comparator<Format> trackFormatComparator) {
        this.mappedTrackInfo = mappedTrackInfo;
        this.rendererIndex = rendererIndex;
        isDisabled = initialIsDisabled;
        overrides =
                initialOverride == null
                        ? Collections.emptyList()
                        : Collections.singletonList(initialOverride);
        this.allowMultipleOverrides = allowMultipleOverrides;
        this.dismissListener = listener;
        this.trackInfoComparator =
                trackFormatComparator == null
                        ? null
                        : (o1, o2) -> trackFormatComparator.compare(o1.format, o2.format);
    }

    public void setTitle(String strTitle) {
        this.title = strTitle;
    }

    public static final class TrackInfo {
        public final int groupIndex;
        public final int trackIndex;
        public final Format format;

        public TrackInfo(int groupIndex, int trackIndex, Format format) {
            this.groupIndex = groupIndex;
            this.trackIndex = trackIndex;
            this.format = format;
        }
    }
}
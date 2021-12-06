package ai.anaha.signup.hlsplayer.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.TrackSelectionView;

import java.util.Collections;
import java.util.List;

import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.hlsutils.PlayerSettingsDialog;

/**
 * Fragment to show a track selection in tab of the track selection dialog.
 */
public final class TrackSelectionViewFragment extends Fragment
        implements TrackSelectionView.TrackSelectionListener {

    private MappingTrackSelector.MappedTrackInfo mappedTrackInfo;
    private int rendererIndex;
    private boolean allowAdaptiveSelections;
    private boolean allowMultipleOverrides;
    PlayerSettingsDialog.OnCustomTrackSelectedListener dismissListener;
    /* package */ public boolean isDisabled;
    /* package */ public List<DefaultTrackSelector.SelectionOverride> overrides;

    public TrackSelectionViewFragment() {
        // Retain instance across activity re-creation to prevent losing access to init data.
        setRetainInstance(true);
    }

    public void init(
            MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
            int rendererIndex,
            boolean initialIsDisabled,
            @Nullable DefaultTrackSelector.SelectionOverride initialOverride,
            boolean allowAdaptiveSelections,
            boolean allowMultipleOverrides, PlayerSettingsDialog.OnCustomTrackSelectedListener listener) {
        this.mappedTrackInfo = mappedTrackInfo;
        this.rendererIndex = rendererIndex;
        this.isDisabled = initialIsDisabled;
        this.overrides =
                initialOverride == null
                        ? Collections.emptyList()
                        : Collections.singletonList(initialOverride);
        this.allowAdaptiveSelections = allowAdaptiveSelections;
        this.allowMultipleOverrides = allowMultipleOverrides;
        this.dismissListener = listener;
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View rootView =
                inflater.inflate(
                        R.layout.exo_track_selection_dialog, container, /* attachToRoot= */ false);
        rootView.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        rootView.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        TrackSelectionView trackSelectionView = rootView.findViewById(R.id.exo_track_selection_view);
        trackSelectionView.setShowDisableOption(true);
        trackSelectionView.setAllowMultipleOverrides(allowMultipleOverrides);
        trackSelectionView.setAllowAdaptiveSelections(allowAdaptiveSelections);
        trackSelectionView.setTrackNameProvider(format -> format.height + "p");
        trackSelectionView.setShowDisableOption(false);
        trackSelectionView.init(
                mappedTrackInfo,
                rendererIndex,
                isDisabled,
                overrides,
                /* trackFormatComparator= */ null,
                /* listener= */ this);
        return rootView;
    }

    @Override
    public void onTrackSelectionChanged(boolean isDisabled, List<DefaultTrackSelector.SelectionOverride> overrides) {
        this.isDisabled = isDisabled;
        this.overrides = overrides;
        this.dismissListener.onTrackSelected();
    }
}
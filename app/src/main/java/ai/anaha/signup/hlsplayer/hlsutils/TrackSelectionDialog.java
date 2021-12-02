/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.anaha.signup.hlsplayer.hlsutils;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.ui.TrackSelectionView;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ai.anaha.signup.hlsplayer.AnahaPlayerActivity;
import ai.anaha.signup.hlsplayer.R;

/**
 * Dialog to select tracks.
 */
public final class TrackSelectionDialog extends BottomSheetDialogFragment {

    private static boolean isVideoSelectionEnabled, isAudioSelectionEnabled, isSubtitleSelectionEnabled = true;
    private final SparseArray<TrackSelectionViewFragment> tabFragments;
    private final ArrayList<Integer> tabTrackTypes;

    private int titleId;
    private DialogInterface.OnClickListener onClickListener;
    private DialogInterface.OnDismissListener onDismissListener;


    /**
     * Returns whether a track selection dialog will have content to display if initialized with the
     * specified {@link DefaultTrackSelector} in its current state.
     */
    public static boolean willHaveContent(DefaultTrackSelector trackSelector) {
        MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        return mappedTrackInfo != null && willHaveContent(mappedTrackInfo);
    }

    /**
     * Returns whether a track selection dialog will have content to display if initialized with the
     * specified {@link MappedTrackInfo}.
     */
    public static boolean willHaveContent(MappedTrackInfo mappedTrackInfo) {
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (showTabForRenderer(mappedTrackInfo, i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a dialog for a given {@link DefaultTrackSelector}, whose parameters will be
     * automatically updated when tracks are selected.
     *
     * @param trackSelector     The {@link DefaultTrackSelector}.
     * @param onDismissListener A {@link DialogInterface.OnDismissListener} to call when the dialog is
     *                          dismissed.
     */
    public static TrackSelectionDialog createForTrackSelector(Boolean isVideoSelectionEnabled, Boolean isAudioSelectionEnabled, Boolean isSubtitleSelectionEnabled,
                                                              DefaultTrackSelector trackSelector, AnahaPlayerActivity.TrackSelectionName trackSelectionName,
                                                              DialogInterface.OnDismissListener onDismissListener) {
        MappedTrackInfo mappedTrackInfo =
                Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
        TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
        DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
        trackSelectionDialog.init(isVideoSelectionEnabled, isAudioSelectionEnabled, isSubtitleSelectionEnabled,
                /* titleId= */ R.string.track_selection_title,
                mappedTrackInfo,
                /* initialParameters = */ parameters,
                /* allowAdaptiveSelections= */ false,
                /* allowMultipleOverrides= */ false,
                /* onClickListener= */ (dialog, which) -> {
                    DefaultTrackSelector.ParametersBuilder builder = parameters.buildUpon();
                    for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
                        builder
                                .clearSelectionOverrides(/* rendererIndex= */ i)
                                .setRendererDisabled(
                                        /* rendererIndex= */ i,
                                        trackSelectionDialog.getIsDisabled(/* rendererIndex= */ i));
                        List<SelectionOverride> overrides =
                                trackSelectionDialog.getOverrides(/* rendererIndex= */ i);
                        if (!overrides.isEmpty()) {
                            builder.setSelectionOverride(
                                    /* rendererIndex= */ i,
                                    mappedTrackInfo.getTrackGroups(/* rendererIndex= */ i),
                                    overrides.get(0));
                        }
                        int trackType = mappedTrackInfo.getRendererType(i);
                        if (C.TRACK_TYPE_VIDEO == trackType) {
                            //0 for video fragment in dialog
                            if (!trackSelectionDialog.getIsDisabled(/* rendererIndex= */ i)
                                    && overrides.size() == 0) {
                                trackSelectionName.selectedTrackName(-1, -1);
                            } else {
                                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(/* rendererIndex= */i);
                                for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                                    TrackGroup group = trackGroups.get(groupIndex);
                                    for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                                        Format format = group.getFormat(trackIndex);
                                        if (overrides.get(0).containsTrack(Integer.parseInt(
                                                Objects.requireNonNull(format.id)))) {
                                            trackSelectionName.selectedTrackName(format.width, format.height);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    trackSelector.setParameters(builder);
                },
                onDismissListener);
        return trackSelectionDialog;
    }

    /**
     * Creates a dialog for given {@link MappedTrackInfo} and {@link DefaultTrackSelector.Parameters}.
     *
     * @param titleId                 The resource id of the dialog title.
     * @param mappedTrackInfo         The {@link MappedTrackInfo} to display.
     * @param initialParameters       The {@link DefaultTrackSelector.Parameters} describing the
     *                                initial track selection.
     * @param allowAdaptiveSelections Whether adaptive selections (consisting of more than one track)
     *                                can be made.
     * @param allowMultipleOverrides  Whether tracks from multiple track groups can be selected.
     * @param onClickListener         {@link DialogInterface.OnClickListener} called when tracks are
     *                                selected.
     * @param onDismissListener       {@link DialogInterface.OnDismissListener} called when the dialog
     *                                is dismissed.
     */
    public static TrackSelectionDialog createForMappedTrackInfoAndParameters(Boolean isVideoSelectionEnabled, Boolean isAudioSelectionEnabled, Boolean isSubtitleSelectionEnabled,
                                                                             int titleId,
                                                                             MappedTrackInfo mappedTrackInfo, TextView selectedTrack,
                                                                             DefaultTrackSelector.Parameters initialParameters,
                                                                             boolean allowAdaptiveSelections,
                                                                             boolean allowMultipleOverrides,
                                                                             DialogInterface.OnClickListener onClickListener,
                                                                             DialogInterface.OnDismissListener onDismissListener) {
        TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
        trackSelectionDialog.init(isVideoSelectionEnabled, isAudioSelectionEnabled, isSubtitleSelectionEnabled,
                titleId,
                mappedTrackInfo,
                initialParameters,
                allowAdaptiveSelections,
                allowMultipleOverrides,
                onClickListener,
                onDismissListener);
        return trackSelectionDialog;
    }

    public TrackSelectionDialog() {
        tabFragments = new SparseArray<>();
        tabTrackTypes = new ArrayList<>();
        // Retain instance across activity re-creation to prevent losing access to init data.
        setRetainInstance(true);
    }

    private void init(Boolean isVideoSelectionEnabled, Boolean isAudioSelectionEnabled, Boolean isSubtitleSelectionEnabled,
                      int titleId,
                      MappedTrackInfo mappedTrackInfo,
                      DefaultTrackSelector.Parameters initialParameters,
                      boolean allowAdaptiveSelections,
                      boolean allowMultipleOverrides,
                      DialogInterface.OnClickListener onClickListener,
                      DialogInterface.OnDismissListener onDismissListener) {
        this.titleId = titleId;
        this.onClickListener = onClickListener;
        this.onDismissListener = onDismissListener;
        TrackSelectionDialog.isVideoSelectionEnabled = isVideoSelectionEnabled;
        TrackSelectionDialog.isAudioSelectionEnabled = isAudioSelectionEnabled;
        TrackSelectionDialog.isSubtitleSelectionEnabled = isSubtitleSelectionEnabled;
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (showTabForRenderer(mappedTrackInfo, i)) {
                int trackType = mappedTrackInfo.getRendererType(/* rendererIndex= */ i);
                TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
                TrackSelectionViewFragment tabFragment = new TrackSelectionViewFragment();
                tabFragment.init(
                        mappedTrackInfo,
                        /* rendererIndex= */ i,
                        initialParameters.getRendererDisabled(/* rendererIndex= */ i),
                        initialParameters.getSelectionOverride(/* rendererIndex= */ i, trackGroupArray),
                        allowAdaptiveSelections,
                        allowMultipleOverrides);
                tabFragments.put(i, tabFragment);
                tabTrackTypes.add(trackType);
            }
        }
    }

    /**
     * Returns whether a renderer is disabled.
     *
     * @param rendererIndex Renderer index.
     * @return Whether the renderer is disabled.
     */
    public boolean getIsDisabled(int rendererIndex) {
        TrackSelectionViewFragment rendererView = tabFragments.get(rendererIndex);
        return rendererView != null && rendererView.isDisabled;
    }

    /**
     * Returns the list of selected track selection overrides for the specified renderer. There will
     * be at most one override for each track group.
     *
     * @param rendererIndex Renderer index.
     * @return The list of track selection overrides for this renderer.
     */
    public List<SelectionOverride> getOverrides(int rendererIndex) {
        TrackSelectionViewFragment rendererView = tabFragments.get(rendererIndex);
        return rendererView == null ? Collections.emptyList() : rendererView.overrides;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // We need to own the view to let tab layout work correctly on all API levels. We can't use
        // AlertDialog because it owns the view itself, so we use AppCompatDialog instead, themed using
        // the AlertDialog theme overlay with force-enabled title.
        /*AppCompatDialog dialog =
                new AppCompatDialog(getActivity(), R.style.TrackSelectionDialogThemeOverlay);
        dialog.setTitle(titleId);*/
        setCancelable(false);
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public int getTheme() {
        return R.style.TrackSelectionDialogThemeOverlay;
    }

    public int getTitleId() {
        return titleId;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        onDismissListener.onDismiss(dialog);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false);
        TabLayout tabLayout = dialogView.findViewById(R.id.track_selection_dialog_tab_layout);
        ViewPager viewPager = dialogView.findViewById(R.id.track_selection_dialog_view_pager);
        Button cancelButton = dialogView.findViewById(R.id.track_selection_dialog_cancel_button);
        Button okButton = dialogView.findViewById(R.id.track_selection_dialog_ok_button);
        viewPager.setAdapter(new FragmentAdapter(getChildFragmentManager()));
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setVisibility(tabFragments.size() > 1 ? View.VISIBLE : View.GONE);
        cancelButton.setOnClickListener(view -> dismiss());
        okButton.setOnClickListener(
                view -> {
                    onClickListener.onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
                    dismiss();
                });
        return dialogView;
    }

    private static boolean showTabForRenderer(MappedTrackInfo mappedTrackInfo, int rendererIndex) {
        TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
        if (trackGroupArray.length == 0) {
            return false;
        }
        int trackType = mappedTrackInfo.getRendererType(rendererIndex);
        return isSupportedTrackType(trackType);
    }

    private static boolean isSupportedTrackType(int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_VIDEO: {
                return isVideoSelectionEnabled;
            }
            case C.TRACK_TYPE_AUDIO: {
                return isAudioSelectionEnabled;
            }
            case C.TRACK_TYPE_TEXT: {
                return isSubtitleSelectionEnabled;
            }
            default:
                return false;
        }
    }

    private static String getTrackTypeString(Resources resources, int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_VIDEO:
                return resources.getString(R.string.exo_track_selection_title_video);
            case C.TRACK_TYPE_AUDIO:
                return resources.getString(R.string.exo_track_selection_title_audio);
            case C.TRACK_TYPE_TEXT:
                return resources.getString(R.string.exo_track_selection_title_text);
            default:
                throw new IllegalArgumentException();
        }
    }

    private final class FragmentAdapter extends FragmentPagerAdapter {

        public FragmentAdapter(FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        public Fragment getItem(int position) {
            return tabFragments.valueAt(position);
        }

        @Override
        public int getCount() {
            return tabFragments.size();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getTrackTypeString(getResources(), tabTrackTypes.get(position));
        }
    }

    /**
     * Fragment to show a track selection in tab of the track selection dialog.
     */
    public static final class TrackSelectionViewFragment extends Fragment
            implements TrackSelectionView.TrackSelectionListener {

        private MappedTrackInfo mappedTrackInfo;
        private int rendererIndex;
        private boolean allowAdaptiveSelections;
        private boolean allowMultipleOverrides;

        /* package */ boolean isDisabled;
        /* package */ List<SelectionOverride> overrides;

        public TrackSelectionViewFragment() {
            // Retain instance across activity re-creation to prevent losing access to init data.
            setRetainInstance(true);
        }

        public void init(
                MappedTrackInfo mappedTrackInfo,
                int rendererIndex,
                boolean initialIsDisabled,
                @Nullable SelectionOverride initialOverride,
                boolean allowAdaptiveSelections,
                boolean allowMultipleOverrides) {
            this.mappedTrackInfo = mappedTrackInfo;
            this.rendererIndex = rendererIndex;
            this.isDisabled = initialIsDisabled;
            this.overrides =
                    initialOverride == null
                            ? Collections.emptyList()
                            : Collections.singletonList(initialOverride);
            this.allowAdaptiveSelections = allowAdaptiveSelections;
            this.allowMultipleOverrides = allowMultipleOverrides;
        }

        @Override
        public View onCreateView(
                LayoutInflater inflater,
                @Nullable ViewGroup container,
                @Nullable Bundle savedInstanceState) {
            View rootView =
                    inflater.inflate(
                            R.layout.exo_track_selection_dialog, container, /* attachToRoot= */ false);
            ((ViewGroup.LayoutParams) rootView.getLayoutParams()).height = ViewGroup.LayoutParams.WRAP_CONTENT;
            ((ViewGroup.LayoutParams) rootView.getLayoutParams()).width = ViewGroup.LayoutParams.WRAP_CONTENT;
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
        public void onTrackSelectionChanged(boolean isDisabled, List<SelectionOverride> overrides) {
            this.isDisabled = isDisabled;
            this.overrides = overrides;
        }
    }
}

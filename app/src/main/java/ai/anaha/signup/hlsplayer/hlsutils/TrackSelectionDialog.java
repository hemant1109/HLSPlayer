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

import static android.view.View.VISIBLE;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ai.anaha.signup.hlsplayer.AnahaPlayerActivity;
import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.adapters.FragmentAdapter;
import ai.anaha.signup.hlsplayer.fragments.PlayBackSpeedViewFragment;
import ai.anaha.signup.hlsplayer.fragments.TrackSelectionFragment;
import ai.anaha.signup.hlsplayer.fragments.TrackSelectionViewFragment;

/**
 * Dialog to select tracks.
 */
public final class TrackSelectionDialog extends BottomSheetDialogFragment {

    private static boolean isVideoSelectionEnabled, isAudioSelectionEnabled, isSubtitleSelectionEnabled = true;
    private final SparseArray<Fragment> tabFragments;
    private final ArrayList<Integer> tabTrackTypes;

    //private DialogInterface.OnClickListener onClickListener;
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
     * @param trackSelector         The {@link DefaultTrackSelector}.
     * @param onDismissListener     A {@link DialogInterface.OnDismissListener} to call when the dialog is
     * @param playbackSpeedListener
     */
    public static TrackSelectionDialog createForTrackSelector(Boolean isVideoSelectionEnabled, Boolean isAudioSelectionEnabled, Boolean isSubtitleSelectionEnabled,
                                                              DefaultTrackSelector trackSelector, AnahaPlayerActivity.TrackSelectionName trackSelectionName,
                                                              DialogInterface.OnDismissListener onDismissListener, float playbackSpeed,
                                                              AnahaPlayerActivity.PlaybackSpeedListener playbackSpeedListener) {
        MappedTrackInfo mappedTrackInfo =
                Assertions.checkNotNull(trackSelector.getCurrentMappedTrackInfo());
        TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
        DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
        trackSelectionDialog.init(isVideoSelectionEnabled, isAudioSelectionEnabled, isSubtitleSelectionEnabled,
                mappedTrackInfo,
                /* initialParameters = */ parameters,
                /* allowAdaptiveSelections= */ false,
                /* allowMultipleOverrides= */ false,
                /* onCustomTrackSelectedListener= */ () -> {
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
                    trackSelectionDialog.dismiss();
                },
                onDismissListener, playbackSpeed, playbackSpeedListener);
        return trackSelectionDialog;
    }

    /**
     * Creates a dialog for given {@link MappedTrackInfo} and {@link DefaultTrackSelector.Parameters}.
     *
     * @param mappedTrackInfo         The {@link MappedTrackInfo} to display.
     * @param initialParameters       The {@link DefaultTrackSelector.Parameters} describing the
     *                                initial track selection.
     * @param allowAdaptiveSelections Whether adaptive selections (consisting of more than one track)
     *                                can be made.
     * @param allowMultipleOverrides  Whether tracks from multiple track groups can be selected.
     * @param onDismissListener       {@link DialogInterface.OnDismissListener} called when the dialog
     *                                is dismissed.
     */
    public static TrackSelectionDialog createForMappedTrackInfoAndParameters(Boolean isVideoSelectionEnabled, Boolean isAudioSelectionEnabled, Boolean isSubtitleSelectionEnabled,
                                                                             MappedTrackInfo mappedTrackInfo,
                                                                             DefaultTrackSelector.Parameters initialParameters,
                                                                             boolean allowAdaptiveSelections,
                                                                             boolean allowMultipleOverrides, OnCustomTrackSelectedListener onCustomTrackSelectedListener,
                                                                             DialogInterface.OnDismissListener onDismissListener) {
        TrackSelectionDialog trackSelectionDialog = new TrackSelectionDialog();
        trackSelectionDialog.init(isVideoSelectionEnabled, isAudioSelectionEnabled, isSubtitleSelectionEnabled,
                mappedTrackInfo,
                initialParameters,
                allowAdaptiveSelections,
                allowMultipleOverrides, onCustomTrackSelectedListener,
                onDismissListener, 1,/*playbackSpeedListener*/null);
        return trackSelectionDialog;
    }

    public TrackSelectionDialog() {
        tabFragments = new SparseArray<>();
        tabTrackTypes = new ArrayList<>();
        // Retain instance across activity re-creation to prevent losing access to init data.
        setRetainInstance(true);
    }

    private void init(Boolean isVideoSelectionEnabled, Boolean isAudioSelectionEnabled, Boolean isSubtitleSelectionEnabled,
                      MappedTrackInfo mappedTrackInfo,
                      DefaultTrackSelector.Parameters initialParameters,
                      boolean allowAdaptiveSelections,
                      boolean allowMultipleOverrides, OnCustomTrackSelectedListener customTrackSelectedListener,
                      DialogInterface.OnDismissListener onDismissListener, float playbackSpeed, AnahaPlayerActivity.PlaybackSpeedListener playbackSpeedListener) {
        this.onDismissListener = onDismissListener;
        TrackSelectionDialog.isVideoSelectionEnabled = isVideoSelectionEnabled;
        TrackSelectionDialog.isAudioSelectionEnabled = isAudioSelectionEnabled;
        TrackSelectionDialog.isSubtitleSelectionEnabled = isSubtitleSelectionEnabled;
        for (int i = 0; i < mappedTrackInfo.getRendererCount(); i++) {
            if (showTabForRenderer(mappedTrackInfo, i)) {
                int trackType = mappedTrackInfo.getRendererType(/* rendererIndex= */ i);
                TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(i);
                TrackSelectionFragment tabFragment = new TrackSelectionFragment();
                tabFragment.init(
                        mappedTrackInfo,
                        /* rendererIndex= */ i,
                        initialParameters.getRendererDisabled(/* rendererIndex= */ i),
                        initialParameters.getSelectionOverride(/* rendererIndex= */ i, trackGroupArray),
                        allowAdaptiveSelections,
                        allowMultipleOverrides, customTrackSelectedListener,null);
                tabFragments.put(i, tabFragment);
                tabTrackTypes.add(trackType);
            }
        }
        tabFragments.put(tabFragments.size(), new PlayBackSpeedViewFragment(playbackSpeed, playbackSpeedListener));
        tabTrackTypes.add(C.TRACK_TYPE_CUSTOM_BASE);
    }

    public interface OnCustomTrackSelectedListener {
        void onTrackSelected();
    }

    /**
     * Returns whether a renderer is disabled.
     *
     * @param rendererIndex Renderer index.
     * @return Whether the renderer is disabled.
     */
    public boolean getIsDisabled(int rendererIndex) {
        if (tabFragments.get(rendererIndex) instanceof TrackSelectionFragment) {
            TrackSelectionFragment rendererView = (TrackSelectionFragment) tabFragments.get(rendererIndex);
            return rendererView != null && rendererView.isDisabled;
        }
        return false;
    }

    /**
     * Returns the list of selected track selection overrides for the specified renderer. There will
     * be at most one override for each track group.
     *
     * @param rendererIndex Renderer index.
     * @return The list of track selection overrides for this renderer.
     */
    public List<SelectionOverride> getOverrides(int rendererIndex) {
        if (tabFragments.get(rendererIndex) instanceof TrackSelectionFragment) {
            TrackSelectionFragment rendererView = (TrackSelectionFragment) tabFragments.get(rendererIndex);
            return rendererView == null ? Collections.emptyList() : rendererView.overrides;
        }
        return Collections.emptyList();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // We need to own the view to let tab layout work correctly on all API levels. We can't use
        // AlertDialog because it owns the view itself, so we use AppCompatDialog instead, themed using
        // the AlertDialog theme overlay with force-enabled title.
        /*AppCompatDialog dialog =
                new AppCompatDialog(getActivity(), R.style.TrackSelectionDialogThemeOverlay);
        dialog.setTitle(titleId);*/
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.getBehavior().setPeekHeight(Resources.getSystem().getDisplayMetrics().heightPixels);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setDraggable(false);
        return dialog;
    }

    @Override
    public int getTheme() {
        return R.style.TrackSelectionDialogThemeOverlay;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        onDismissListener.onDismiss(dialog);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false);
        TabLayout tabLayout = dialogView.findViewById(R.id.track_selection_dialog_tab_layout);
        ViewPager viewPager = dialogView.findViewById(R.id.track_selection_dialog_view_pager);
        /*Button cancelButton = dialogView.findViewById(R.id.track_selection_dialog_cancel_button);
        Button okButton = dialogView.findViewById(R.id.track_selection_dialog_ok_button);*/
        viewPager.setAdapter(new FragmentAdapter(getResources(), getChildFragmentManager(), tabFragments, tabTrackTypes));
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.setVisibility(tabFragments.size() > 1 ? VISIBLE : View.GONE);
        //cancelButton.setOnClickListener(view -> dismiss());
        /*okButton.setOnClickListener(
                view -> {
                    onClickListener.onClick(getDialog(), DialogInterface.BUTTON_POSITIVE);
                    dismiss();
                });*/

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

    public static String getTrackTypeString(Resources resources, int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_VIDEO:
                return resources.getString(R.string.exo_track_selection_title_video);
            case C.TRACK_TYPE_AUDIO:
                return resources.getString(R.string.exo_track_selection_title_audio);
            case C.TRACK_TYPE_TEXT:
                return resources.getString(R.string.exo_track_selection_title_text);
            case C.TRACK_TYPE_CUSTOM_BASE:
                return resources.getString(R.string.playback_speed);
            default:
                throw new IllegalArgumentException();
        }
    }

}

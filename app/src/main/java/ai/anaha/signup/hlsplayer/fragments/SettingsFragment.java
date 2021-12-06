package ai.anaha.signup.hlsplayer.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.adapters.SettingsAdapter;
import ai.anaha.signup.hlsplayer.hlsutils.PlayerSettingsDialog;

public final class SettingsFragment extends Fragment {

    private final PlayerSettingsDialog.SettingItemClickListener settingItemClickListener;
    private final String strVideoQuality;
    private final float playbackSpeed;
    private String[] playbackSpeedTexts;
    private int[] playbackSpeedsMultBy100;

    public SettingsFragment(PlayerSettingsDialog.SettingItemClickListener settingItemClickListener, String strVideoQuality, float playbackSpeed) {
        this.settingItemClickListener = settingItemClickListener;
        this.strVideoQuality = strVideoQuality;
        this.playbackSpeed = playbackSpeed;

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
                        R.layout.custom_exo_setting_selection_dialog, container, /* attachToRoot= */ false);
        playbackSpeedTexts = container.getContext().getResources().getStringArray(R.array.exo_playback_speeds);
        playbackSpeedsMultBy100 = container.getContext().getResources().getIntArray(R.array.exo_speed_multiplied_by_100);

        SettingsAdapter playbackSpeedAdapter = new SettingsAdapter(
                container.getContext().getResources().getStringArray(R.array.exo_settings), strVideoQuality,
                container.getContext().getResources().obtainTypedArray(R.array.exo_settings_icons),
                settingItemClickListener, playbackSpeedTexts[getSelectedIndex(playbackSpeed)]);
        RecyclerView settingsView = rootView.findViewById(R.id.exo_settings_listview);
        settingsView.setAdapter(playbackSpeedAdapter);
        settingsView.setLayoutManager(new LinearLayoutManager(getContext()));
        settingsView.suppressLayout(false);
        return rootView;
    }

    public int getSelectedIndex(float playbackSpeed) {
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
        return closestMatchIndex;
    }
}
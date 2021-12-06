package ai.anaha.signup.hlsplayer.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import ai.anaha.signup.hlsplayer.AnahaPlayerActivity;
import ai.anaha.signup.hlsplayer.R;
import ai.anaha.signup.hlsplayer.adapters.PlaybackSpeedAdapter;

public final class PlayBackSpeedViewFragment extends Fragment {

    private final AnahaPlayerActivity.PlaybackSpeedListener playbackSpeedListener;
    private float playbackSpeed;

    public PlayBackSpeedViewFragment(float playbackSpeed, AnahaPlayerActivity.PlaybackSpeedListener playbackSpeedListener) {
        // Retain instance across activity re-creation to prevent losing access to init data.
        this.playbackSpeed = playbackSpeed;
        this.playbackSpeedListener = playbackSpeedListener;
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
        rootView.findViewById(R.id.tvTitle).setVisibility(View.GONE);
        PlaybackSpeedAdapter playbackSpeedAdapter = new PlaybackSpeedAdapter(
                container.getContext().getResources().getStringArray(R.array.exo_playback_speeds),
                container.getContext().getResources().getIntArray(R.array.exo_speed_multiplied_by_100),
                playbackSpeedListener);
        playbackSpeedAdapter.updateSelectedIndex(playbackSpeed);
        RecyclerView settingsView = rootView.findViewById(R.id.exo_settings_listview);
        settingsView.setAdapter(playbackSpeedAdapter);
        settingsView.setLayoutManager(new LinearLayoutManager(getContext()));
        settingsView.suppressLayout(false);
        return rootView;
    }
}
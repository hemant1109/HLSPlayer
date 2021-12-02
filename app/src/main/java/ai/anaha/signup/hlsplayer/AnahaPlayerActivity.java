/*
 * Copyright (C) 2016 The Android Open Source Project
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
package ai.anaha.signup.hlsplayer;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.DebugTextViewHelper;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import ai.anaha.signup.hlsplayer.hlsutils.DemoUtil;
import ai.anaha.signup.hlsplayer.hlsutils.IntentUtil;
import ai.anaha.signup.hlsplayer.hlsutils.TrackSelectionDialog;

/**
 * An activity that plays media using {@link ExoPlayer}.
 */
public class AnahaPlayerActivity extends AppCompatActivity implements OnClickListener,
        StyledPlayerControlView.VisibilityListener,
        StyledPlayerControlView.OnFullScreenModeChangedListener {

    // Saved instance state keys.

    private static final String KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters";
    private static final String KEY_ITEM_INDEX = "item_index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";

    protected StyledPlayerView playerView;
    protected @Nullable
    ExoPlayer player;

    private boolean isShowingTrackSelectionDialog;
    private TextView selectTracks;
    private ImageView exoFullscreen;

    private DataSource.Factory dataSourceFactory;
    private List<MediaItem> mediaItems;
    private DefaultTrackSelector trackSelector;
    private DefaultTrackSelector.Parameters trackSelectionParameters;
    private DebugTextViewHelper debugViewHelper;
    private TracksInfo lastSeenTracksInfo;
    private boolean startAutoPlay;
    private int startItemIndex;
    private long startPosition;

    // For ad playback only.
    // Activity lifecycle.

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataSourceFactory = DemoUtil.getDataSourceFactory(/* context= */ this);

        setContentView();
        //make activity full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        selectTracks = findViewById(R.id.select_tracks_button);
        selectTracks.setOnClickListener(this);
        exoFullscreen = findViewById(R.id.exo_fullscreen);
        exoFullscreen.setEnabled(false);
        exoFullscreen.getDrawable().setTint(getColor(R.color.unplayed));
        //exoPause = findViewById(R.id.exo_pause);

        playerView = findViewById(R.id.player_view);
        playerView.setControllerVisibilityListener(this);
        playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
        playerView.setControllerOnFullScreenModeChangedListener(this);
        playerView.requestFocus();

        if (savedInstanceState != null) {
            // Restore as DefaultTrackSelector.Parameters in case ExoPlayer specific parameters were set.
            trackSelectionParameters =
                    DefaultTrackSelector.Parameters.CREATOR.fromBundle(
                            savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS));
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
        } else {
            trackSelectionParameters =
                    new DefaultTrackSelector.ParametersBuilder(/* context= */ this).build();
            clearStartPosition();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        releasePlayer();
        playerView.getOverlayFrameLayout().removeAllViews();
        clearStartPosition();
        setIntent(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Util.SDK_INT > 23) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer();
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        playerView.getOverlayFrameLayout().removeAllViews();

    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer();
        } else {
            showToast(R.string.storage_permission_denied);
            finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        updateTrackSelectorParameters();
        updateStartPosition();
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
        outState.putInt(KEY_ITEM_INDEX, startItemIndex);
        outState.putLong(KEY_POSITION, startPosition);
    }

    // Activity input

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    // OnClickListener methods

    @Override
    public void onClick(View view) {
        if (view == selectTracks
                && !isShowingTrackSelectionDialog
                && TrackSelectionDialog.willHaveContent(trackSelector)) {
            isShowingTrackSelectionDialog = true;
            TrackSelectionDialog trackSelectionDialog =
                    TrackSelectionDialog.createForTrackSelector(true, false, false,
                            trackSelector, (width, height) -> {
                                String string = height + "p";
                                if (width == -1 && height == -1) {
                                    string = String.format("%s (%s)",
                                            getString(R.string.exo_track_selection_auto),
                                            getVideoQualityString(Objects.requireNonNull(Objects.requireNonNull(player).getVideoFormat()).width, Objects.requireNonNull(player.getVideoFormat()).height));
                                }/* else if (width == 1920 && height == 1080) {
                                    string = getString(R.string.exo_track_selection_fhd);
                                } else if (width == 1600 && height == 900) {
                                    string = getString(R.string.exo_track_selection_hdp);
                                } else if (width == 1280 && height == 720) {
                                    string = getString(R.string.exo_track_selection_hd);
                                } else if (width == 960 && height == 540) {
                                    string = getString(R.string.exo_track_selection_qhd);
                                } else if (width == 852 && height == 480) {
                                    string = getString(R.string.exo_track_selection_480p);
                                } else if (width == 640 && height == 360) {
                                    string = getString(R.string.exo_track_selection_360p);
                                } else if (width == 576 && height == 324) {
                                    string = getString(R.string.exo_track_selection_360p);
                                } else if (width == 480 && height == 270) {
                                    string = getString(com.google.android.exoplayer2.ui.R.string.exo_track_resolution,
                                            width, height);
                                } else {
                                    string = getString(com.google.android.exoplayer2.ui.R.string.exo_track_resolution,
                                            width, height);
                                }*/
                                selectTracks.setText(string);
                            },
                            /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);
            trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
        }
    }

    String getVideoQualityString(int width, int height) {
        String string = height + "p";
        /*if (width == 1920 && height == 1080) {
            string = height+"p";
        } else if (width == 1600 && height == 900) {
            string = getString(R.string.exo_track_selection_hdp);
        } else if (width == 1280 && height == 720) {
            string = getString(R.string.exo_track_selection_hd);
        } else if (width == 960 && height == 540) {
            string = getString(R.string.exo_track_selection_qhd);
        } else if (width == 852 && height == 480) {
            string = getString(R.string.exo_track_selection_480p);
        } else if (width == 640 && height == 360) {
            string = getString(R.string.exo_track_selection_360p);
        } else if (width == 576 && height == 324) {
            string = getString(R.string.exo_track_selection_360p);
        }*/
        return string;
    }

    public interface TrackSelectionName {

        void selectedTrackName(int width, int height);
    }
    // PlayerControlView.VisibilityListener implementation

    @Override
    public void onVisibilityChange(int visibility) {

    }

    // Internal methods

    protected void setContentView() {
        setContentView(R.layout.anaha_player_activity);
    }

    /**
     * @return Whether initialization was successful.
     */
    protected boolean initializePlayer() {
        if (player == null) {
            Intent intent = getIntent();

            mediaItems = createMediaItems(intent);
            if (mediaItems.isEmpty()) {
                return false;
            }

           /*boolean preferExtensionDecoders =
                    playerActivityIntent.getBooleanExtra(IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA, false);*/
            RenderersFactory renderersFactory =
                    DemoUtil.buildRenderersFactory(/* context= */ this, /*preferExtensionDecoders*/false);
            MediaSourceFactory mediaSourceFactory =
                    new DefaultMediaSourceFactory(dataSourceFactory)
                            .setAdViewProvider(playerView);

            trackSelector = new DefaultTrackSelector(/* context= */ this);
            lastSeenTracksInfo = TracksInfo.EMPTY;
            player = new ExoPlayer.Builder(/* context= */ this)
                    .setRenderersFactory(renderersFactory)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setTrackSelector(trackSelector)
                    .setSeekForwardIncrementMs(10 * 1000)
                    .build();
            player.setTrackSelectionParameters(trackSelectionParameters);
            player.addListener(new PlayerEventListener());
            player.addAnalyticsListener(new EventLogger(trackSelector));
            player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true);
            player.setPlayWhenReady(startAutoPlay);
            playerView.setPlayer(player);
            /*debugViewHelper = new DebugTextViewHelper(player, debugTextView);
            debugViewHelper.start();*/
        }
        boolean haveStartPosition = startItemIndex != C.INDEX_UNSET;
        if (haveStartPosition) {
            player.seekTo(startItemIndex, startPosition);
        }
        player.setMediaItems(mediaItems, /* resetPosition= */ !haveStartPosition);
        player.prepare();
        updateButtonVisibility();
        return true;
    }

    private List<MediaItem> createMediaItems(Intent intent) {
        String action = intent.getAction();
        boolean actionIsListView = IntentUtil.ACTION_VIEW_LIST.equals(action);
        if (!actionIsListView && !IntentUtil.ACTION_VIEW.equals(action)) {
            showToast(getString(R.string.unexpected_intent_action, action));
            finish();
            return Collections.emptyList();
        }

        List<MediaItem> mediaItems = new ArrayList<>();
        for (MediaItem item : IntentUtil.createMediaItemsFromIntent(intent)) {
            if (item.localConfiguration != null) {
                MediaItem.Builder builder = item.buildUpon();
                builder.setMediaId(item.mediaId)
                        .setUri(item.localConfiguration.uri)
                        .setCustomCacheKey(item.localConfiguration.customCacheKey)
                        .setMimeType(item.localConfiguration.mimeType)
                        .setStreamKeys(item.localConfiguration.streamKeys);
                @Nullable
                MediaItem.DrmConfiguration drmConfiguration = item.localConfiguration.drmConfiguration;
                if (drmConfiguration != null) {
                    builder.setDrmConfiguration(
                            drmConfiguration.buildUpon().setKeySetId(item.localConfiguration.drmConfiguration.getKeySetId()).build());
                }
                mediaItems.add(builder.build());
            } else {
                mediaItems.add(item);
            }
        }
        for (int i = 0; i < mediaItems.size(); i++) {
            MediaItem mediaItem = mediaItems.get(i);

            if (!Util.checkCleartextTrafficPermitted(mediaItem)) {
                showToast(R.string.error_cleartext_not_permitted);
                finish();
                return Collections.emptyList();
            }
            if (Util.maybeRequestReadExternalStoragePermission(/* activity= */ this, mediaItem)) {
                // The player will be reinitialized if the permission is granted.
                return Collections.emptyList();
            }

            MediaItem.DrmConfiguration drmConfiguration =
                    checkNotNull(mediaItem.localConfiguration).drmConfiguration;
            if (drmConfiguration != null) {
                if (Util.SDK_INT < 18) {
                    showToast(R.string.error_drm_unsupported_before_api_18);
                    finish();
                    return Collections.emptyList();
                } else if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.scheme)) {
                    showToast(R.string.error_drm_unsupported_scheme);
                    finish();
                    return Collections.emptyList();
                }
            }
        }
        return mediaItems;
    }

    protected void releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters();
            updateStartPosition();
            /*debugViewHelper.stop();
            debugViewHelper = null;*/
            player.release();
            player = null;
            mediaItems = Collections.emptyList();
        }
    }

    private void updateTrackSelectorParameters() {
        if (player != null) {
            // Until the demo app is fully migrated to TrackSelectionParameters, rely on ExoPlayer to use
            // DefaultTrackSelector by default.
            trackSelectionParameters =
                    (DefaultTrackSelector.Parameters) player.getTrackSelectionParameters();
        }
    }

    private void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            startItemIndex = player.getCurrentMediaItemIndex();
            startPosition = Math.max(0, player.getContentPosition());
        }
    }

    protected void clearStartPosition() {
        startAutoPlay = true;
        startItemIndex = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    // User controls

    private void updateButtonVisibility() {
        selectTracks.setEnabled(
                player != null && TrackSelectionDialog.willHaveContent(trackSelector));
        exoFullscreen.setEnabled(true);
        exoFullscreen.getDrawable().setTint(selectTracks.getCurrentTextColor());
    }

    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onFullScreenModeChanged(boolean isFullScreen) {
        if (isFullScreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            exoFullscreen.postDelayed(() -> exoFullscreen.setImageResource(R.drawable.exo_ic_fullscreen_exit), 200);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            exoFullscreen.postDelayed(() -> exoFullscreen.setImageResource(R.drawable.exo_ic_fullscreen_enter), 200);
        }
    }

    private class PlayerEventListener implements Player.Listener {

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
//      if (playbackState == Player.STATE_ENDED) {
//        //showControls();
//      } else if (playbackState == Player.STATE_READY) {
//        //exoPlayPauseButton.setImageResource(R.drawable.exo_ic_pause_circle_filled);
//      } else if (playbackState == Player.STATE_IDLE) {
//        //exoPlayPauseButton.setImageResource(R.drawable.exo_ic_play_circle_filled);
//      }
            updateButtonVisibility();
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
            if (selectTracks.getText().toString().contains(getString(R.string.exo_track_selection_auto))) {
                if (player != null && player.getVideoFormat() != null) {
                    selectTracks.setText(String.format("%s (%s)",
                            getString(R.string.exo_track_selection_auto),
                            getVideoQualityString(player.getVideoFormat().width,
                                    player.getVideoFormat().height)));
                }
            }
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException error) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                assert player != null;
                player.seekToDefaultPosition();
                player.prepare();
            } else {
                updateButtonVisibility();
                //showControls();
            }
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksInfoChanged(TracksInfo tracksInfo) {
            updateButtonVisibility();
            if (tracksInfo == lastSeenTracksInfo) {
                return;
            }
            if (!tracksInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_VIDEO)) {
                showToast(R.string.error_unsupported_video);
            }
            if (!tracksInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_AUDIO)) {
                showToast(R.string.error_unsupported_audio);
            }
            lastSeenTracksInfo = tracksInfo;
        }
    }

    private class PlayerErrorMessageProvider implements ErrorMessageProvider<PlaybackException> {

        @Override
        public Pair<Integer, String> getErrorMessage(PlaybackException e) {
            String errorString = getString(R.string.error_generic);
            Throwable cause = e.getCause();
            if (cause instanceof DecoderInitializationException) {
                // Special case for decoder initialization failures.
                DecoderInitializationException decoderInitializationException =
                        (DecoderInitializationException) cause;
                if (decoderInitializationException.codecInfo == null) {
                    if (decoderInitializationException.getCause() instanceof DecoderQueryException) {
                        errorString = getString(R.string.error_querying_decoders);
                    } else if (decoderInitializationException.secureDecoderRequired) {
                        errorString =
                                getString(
                                        R.string.error_no_secure_decoder, decoderInitializationException.mimeType);
                    } else {
                        errorString =
                                getString(R.string.error_no_decoder, decoderInitializationException.mimeType);
                    }
                } else {
                    errorString =
                            getString(
                                    R.string.error_instantiating_decoder,
                                    decoderInitializationException.codecInfo.name);
                }
            }
            return Pair.create(0, errorString);
        }
    }
}

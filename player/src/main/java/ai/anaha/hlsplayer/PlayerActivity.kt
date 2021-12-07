package ai.anaha.hlsplayer

import ai.anaha.hlsplayer.hlsutils.DemoUtil.buildRenderersFactory
import ai.anaha.hlsplayer.hlsutils.DemoUtil.getDataSourceFactory
import ai.anaha.hlsplayer.hlsutils.IntentUtil
import ai.anaha.hlsplayer.hlsutils.IntentUtil.createMediaItemsFromIntent
import ai.anaha.hlsplayer.hlsutils.PlayerSettingsDialog
import ai.anaha.hlsplayer.hlsutils.PlayerSettingsDialog.Companion.createForTrackSelector
import ai.anaha.hlsplayer.hlsutils.PlayerSettingsDialog.SettingItemClickListener
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Pair
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.drm.FrameworkMediaDrm
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer.DecoderInitializationException
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil.DecoderQueryException
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.ParametersBuilder
import com.google.android.exoplayer2.ui.StyledPlayerControlView
import com.google.android.exoplayer2.ui.StyledPlayerControlView.OnFullScreenModeChangedListener
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.ErrorMessageProvider
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize
import java.util.*
import kotlin.math.max

/**
 * An activity that plays media using [ExoPlayer].
 */
class PlayerActivity : AppCompatActivity(), View.OnClickListener,
    StyledPlayerControlView.VisibilityListener, OnFullScreenModeChangedListener {
    private var playerView: StyledPlayerView? = null
    private var player: ExoPlayer? = null
    private var isShowingTrackSelectionDialog = false
    private var exoDuration: TextView? = null
    private var exoFullscreen: ImageView? = null
    private var ivPlayerMenu: ImageView? = null
    private lateinit var dataSourceFactory: DataSource.Factory
    private lateinit var mediaItems: List<MediaItem>
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var trackSelectionParameters: DefaultTrackSelector.Parameters
    private var lastSeenTracksInfo: TracksInfo? = null
    private var startAutoPlay = false
    private var startItemIndex = 0
    private var startPosition: Long = 0
    private var playbackSpeed = 1f
    private var playerSettingsDialog: PlayerSettingsDialog? = null
    private var root: View? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataSourceFactory = getDataSourceFactory( /* context= */this)
        setContentView(R.layout.anaha_player_activity)
        exoDuration = findViewById(R.id.exo_duration)
        ivPlayerMenu = findViewById(R.id.ivPlayerMenu)
        ivPlayerMenu?.setOnClickListener(this)
        exoFullscreen = findViewById(R.id.exo_fullscreen)
        exoFullscreen?.isEnabled = false
        exoFullscreen?.drawable?.setTint(getColor(R.color.unplayed))
        root = findViewById(R.id.root)
        playerView = findViewById(R.id.player_view)
        playerView?.setControllerVisibilityListener(this)
        playerView?.setErrorMessageProvider(PlayerErrorMessageProvider())
        playerView?.setControllerOnFullScreenModeChangedListener(this)
        playerView?.requestFocus()
        if (savedInstanceState != null) {
            // Restore as DefaultTrackSelector.Parameters in case ExoPlayer specific parameters were set.
            trackSelectionParameters = DefaultTrackSelector.Parameters.CREATOR.fromBundle(
                savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS)!!
            )
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY)
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX)
            startPosition = savedInstanceState.getLong(KEY_POSITION)
        } else {
            trackSelectionParameters = ParametersBuilder( /* context= */this).build()
            clearStartPosition()
        }
        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            exoFullscreen?.performClick()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    override fun onBackPressed() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            exoFullscreen?.performClick()
        } else super.onBackPressed()
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        releasePlayer()
        playerView?.overlayFrameLayout?.removeAllViews()
        clearStartPosition()
        setIntent(intent)
    }

    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
            if (playerView != null) {
                playerView?.onResume()
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
            if (playerView != null) {
                playerView?.onResume()
            }
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            if (playerView != null) {
                playerView?.onPause()
            }
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView?.onPause()
            }
            releasePlayer()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        playerView?.overlayFrameLayout?.removeAllViews()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty()) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initializePlayer()
        } else {
            showToast(R.string.storage_permission_denied)
            finish()
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        updateTrackSelectorParameters()
        updateStartPosition()
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle())
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay)
        outState.putInt(KEY_ITEM_INDEX, startItemIndex)
        outState.putLong(KEY_POSITION, startPosition)
    }

    // Activity input
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // See whether the player view wants to handle media or DPAD keys events.
        return playerView?.dispatchKeyEvent(event) == true || super.dispatchKeyEvent(event)
    }

    // OnClickListener methods
    override fun onClick(view: View) {
        if (view === ivPlayerMenu && !isShowingTrackSelectionDialog
            && PlayerSettingsDialog.willHaveContent(trackSelector)
        ) {
            isShowingTrackSelectionDialog = true
            val settingItemClickListener = object : SettingItemClickListener {
                override fun settingItemClick(adapterPosition: Int) {
                    playerSettingsDialog?.dismiss()
                    when (adapterPosition) {
                        0, 1 -> {
                            playerSettingsDialog = createForTrackSelector(
                                adapterPosition,
                                getVideoQualityString(player?.videoFormat?.height),
                                isVideoSelectionEnabled = true,
                                isAudioSelectionEnabled = false,
                                isSubtitleSelectionEnabled = false,
                                trackSelector = trackSelector,
                                onDismissListener = {
                                    isShowingTrackSelectionDialog = false
                                }, playbackSpeed,
                                playbackSpeedListener = object : PlaybackSpeedListener {
                                    override fun setPlaybackSpeed(speed: Float) {
                                        if (player != null) {
                                            playbackSpeed = speed
                                            player?.setPlaybackSpeed(speed)
                                            playerSettingsDialog?.dismiss()
                                        }
                                    }
                                })
                            playerSettingsDialog?.show(supportFragmentManager,  /* tag= */null)
                        }
                        2 ->
                            //Help and feedback
                            Toast.makeText(
                                this@PlayerActivity,
                                "Help and feedback clicked",
                                Toast.LENGTH_SHORT
                            ).show()
                        3 ->
                            //Report
                            Toast.makeText(
                                this@PlayerActivity,
                                "Report clicked",
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                }
            }
            playerSettingsDialog = PlayerSettingsDialog(
                getVideoQualityString(player?.videoFormat?.height),
                playbackSpeed
            )
            playerSettingsDialog?.setSettingItemClickListener(settingItemClickListener)
            playerSettingsDialog?.setOnDismissListener {
                isShowingTrackSelectionDialog = false
            }
            playerSettingsDialog?.show(supportFragmentManager,  /* tag= */null)
        }
    }

    fun getVideoQualityString(height: Int?): String {
        return height?.toString() + "p"
    }

    interface PlaybackSpeedListener {
        fun setPlaybackSpeed(@FloatRange(from = 0.0, fromInclusive = false) speed: Float)
    }

    // PlayerControlView.VisibilityListener implementation
    override fun onVisibilityChange(visibility: Int) {}

    /**
     */
    private fun initializePlayer() {
        if (player == null) {
            val intent = intent
            mediaItems = createMediaItems(intent)
            if (mediaItems.isEmpty()) {
                return
            }

            /*boolean preferExtensionDecoders =
                    playerActivityIntent.getBooleanExtra(IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA, false);*/
            val renderersFactory =
                buildRenderersFactory( /* context= */this,  /*preferExtensionDecoders*/false)
            val mediaSourceFactory: MediaSourceFactory =
                DefaultMediaSourceFactory(dataSourceFactory)
            trackSelector = DefaultTrackSelector( /* context= */this)
            lastSeenTracksInfo = TracksInfo.EMPTY
            player = ExoPlayer.Builder( /* context= */this)
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
                .setSeekForwardIncrementMs((10 * 1000).toLong())
                .build()
            player?.trackSelectionParameters = trackSelectionParameters
            player?.addListener(PlayerEventListener())
            player?.addAnalyticsListener(EventLogger(trackSelector))
            player?.setAudioAttributes(AudioAttributes.DEFAULT,  /* handleAudioFocus= */true)
            player?.playWhenReady = startAutoPlay
            playerView?.player = player
        }
        val haveStartPosition = startItemIndex != C.INDEX_UNSET
        if (haveStartPosition) {
            player?.seekTo(startItemIndex, startPosition)
        }
        player?.setMediaItems(mediaItems,  /* resetPosition= */!haveStartPosition)
        player?.prepare()
        updateButtonVisibility()
    }

    private fun createMediaItems(intent: Intent): List<MediaItem> {
        val action = intent.action
        if (IntentUtil.ACTION_VIEW != action) {
            showToast(getString(R.string.unexpected_intent_action, action))
            finish()
            return emptyList()
        }
        val mediaItems: MutableList<MediaItem> = ArrayList()
        for (item in createMediaItemsFromIntent(intent)) {
            if (item.localConfiguration != null) {
                val builder = item.buildUpon()
                builder.setMediaId(item.mediaId)
                    .setUri(item.localConfiguration?.uri)
                    .setCustomCacheKey(item.localConfiguration?.customCacheKey)
                    .setMimeType(item.localConfiguration?.mimeType)
                    .setStreamKeys(item.localConfiguration?.streamKeys)
                val drmConfiguration = item.localConfiguration?.drmConfiguration
                if (drmConfiguration != null) {
                    builder.setDrmConfiguration(
                        drmConfiguration.buildUpon()
                            .setKeySetId(item.localConfiguration?.drmConfiguration?.keySetId)
                            .build()
                    )
                }
                mediaItems.add(builder.build())
            } else {
                mediaItems.add(item)
            }
        }
        for (i in mediaItems.indices) {
            val mediaItem = mediaItems[i]
            if (!Util.checkCleartextTrafficPermitted(mediaItem)) {
                showToast(R.string.error_cleartext_not_permitted)
                finish()
                return emptyList()
            }
            if (Util.maybeRequestReadExternalStoragePermission( /* activity= */this, mediaItem)) {
                // The player will be reinitialized if the permission is granted.
                return emptyList()
            }
            val drmConfiguration =
                Assertions.checkNotNull(mediaItem.localConfiguration).drmConfiguration
            if (drmConfiguration != null) {
                if (Util.SDK_INT < 18) {
                    showToast(R.string.error_drm_unsupported_before_api_18)
                    finish()
                    return emptyList()
                } else if (!FrameworkMediaDrm.isCryptoSchemeSupported(drmConfiguration.scheme)) {
                    showToast(R.string.error_drm_unsupported_scheme)
                    finish()
                    return emptyList()
                }
            }
        }
        return mediaItems
    }

    private fun releasePlayer() {
        if (player != null) {
            updateTrackSelectorParameters()
            updateStartPosition()
            /*debugViewHelper.stop();
            debugViewHelper = null;*/
            player?.release()
            player = null
            mediaItems = emptyList()
        }
    }

    private fun updateTrackSelectorParameters() {
        if (player != null) {
            // Until the demo app is fully migrated to TrackSelectionParameters, rely on ExoPlayer to use
            // DefaultTrackSelector by default.
            trackSelectionParameters =
                player?.trackSelectionParameters as DefaultTrackSelector.Parameters
        }
    }

    private fun updateStartPosition() {
        if (player != null) {
            startAutoPlay = player?.playWhenReady == true
            startItemIndex = player?.currentMediaItemIndex!!
            startPosition = max(0L, player?.contentPosition!!)
        }
    }

    private fun clearStartPosition() {
        startAutoPlay = true
        startItemIndex = C.INDEX_UNSET
        startPosition = C.TIME_UNSET
    }

    // User controls
    private fun updateButtonVisibility() {
        /*selectTracks.setEnabled(
                player != null && TrackSelectionDialog.willHaveContent(trackSelector));*/
        exoFullscreen?.isEnabled = true
        exoDuration?.currentTextColor?.let { exoFullscreen?.drawable?.setTint(it) }
    }

    private fun showToast(messageId: Int) {
        showToast(getString(messageId))
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onFullScreenModeChanged(isFullScreen: Boolean) {
        if (isFullScreen) {
            root?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            exoFullscreen?.postDelayed(
                { exoFullscreen?.setImageResource(R.drawable.exo_ic_fullscreen_exit) },
                200
            )
        } else {
            root?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            exoFullscreen?.postDelayed(
                { exoFullscreen?.setImageResource(R.drawable.exo_ic_fullscreen_enter) },
                200
            )
        }
    }

    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
            updateButtonVisibility()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
        }

        override fun onPlayerError(error: PlaybackException) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                assert(player != null)
                player?.seekToDefaultPosition()
                player?.prepare()
            } else {
                updateButtonVisibility()
                //showControls();
            }
        }

        override fun onTracksInfoChanged(tracksInfo: TracksInfo) {
            updateButtonVisibility()
            if (tracksInfo === lastSeenTracksInfo) {
                return
            }
            if (!tracksInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_VIDEO)) {
                showToast(R.string.error_unsupported_video)
            }
            if (!tracksInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_AUDIO)) {
                showToast(R.string.error_unsupported_audio)
            }
            lastSeenTracksInfo = tracksInfo
        }
    }

    private inner class PlayerErrorMessageProvider : ErrorMessageProvider<PlaybackException> {
        override fun getErrorMessage(e: PlaybackException): Pair<Int, String> {
            var errorString = getString(R.string.error_generic)
            val cause = e.cause
            if (cause is DecoderInitializationException) {
                // Special case for decoder initialization failures.
                errorString = if (cause.codecInfo == null) {
                    when {
                        cause.cause is DecoderQueryException -> {
                            getString(R.string.error_querying_decoders)
                        }
                        cause.secureDecoderRequired -> {
                            getString(
                                R.string.error_no_secure_decoder,
                                cause.mimeType
                            )
                        }
                        else -> {
                            getString(
                                R.string.error_no_decoder,
                                cause.mimeType
                            )
                        }
                    }
                } else {
                    getString(
                        R.string.error_instantiating_decoder,
                        cause.codecInfo?.name
                    )
                }
            }
            return Pair.create(0, errorString)
        }
    }

    companion object {
        // Saved instance state keys.
        private const val KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters"
        private const val KEY_ITEM_INDEX = "item_index"
        private const val KEY_POSITION = "position"
        private const val KEY_AUTO_PLAY = "auto_play"
    }
}
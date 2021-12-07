package ai.anaha.player.utils

import ai.anaha.player.PlayerActivity.PlaybackSpeedListener
import ai.anaha.player.R
import ai.anaha.player.fragments.PlayBackSpeedViewFragment
import ai.anaha.player.fragments.SettingsFragment
import ai.anaha.player.fragments.TrackSelectionFragment
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector.SelectionOverride
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo
import com.google.android.exoplayer2.util.Assertions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.*

/**
 * Dialog to select tracks.
 */
class PlayerSettingsDialog(strVideoQuality: String, playbackSpeed: Float) :
    BottomSheetDialogFragment() {
    private val tabFragments: SparseArray<Fragment>
    private val tabTrackTypes: ArrayList<Int>
    private val playbackSpeed: Float

    private lateinit var onDismissListener: DialogInterface.OnDismissListener
    private lateinit var settingItemClickListener: SettingItemClickListener
    private var fragment: Fragment? = null
    fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener) {
        this.onDismissListener = onDismissListener
    }

    private fun init(
        position: Int,
        isVideoSelectionEnabled: Boolean,
        isAudioSelectionEnabled: Boolean,
        isSubtitleSelectionEnabled: Boolean,
        mappedTrackInfo: MappedTrackInfo,
        initialParameters: DefaultTrackSelector.Parameters,
        customTrackSelectedListener: OnCustomTrackSelectedListener,
        onDismissListener: DialogInterface.OnDismissListener,
        playbackSpeed: Float,
        playbackSpeedListener: PlaybackSpeedListener
    ) {
        this.onDismissListener = onDismissListener
        Companion.isVideoSelectionEnabled = isVideoSelectionEnabled
        Companion.isAudioSelectionEnabled = isAudioSelectionEnabled
        Companion.isSubtitleSelectionEnabled = isSubtitleSelectionEnabled
        for (i in 0 until mappedTrackInfo.rendererCount) {
            val trackType = mappedTrackInfo.getRendererType( /* rendererIndex= */i)
            if (C.TRACK_TYPE_VIDEO == trackType) {
                val trackGroupArray = mappedTrackInfo.getTrackGroups(i)
                val tabFragment = TrackSelectionFragment()
                tabFragment.setTitle(strVideoQuality)
                tabFragment.init(
                    mappedTrackInfo,  /* rendererIndex= */
                    i,
                    initialParameters.getRendererDisabled( /* rendererIndex= */i),
                    initialParameters.getSelectionOverride( /* rendererIndex= */i, trackGroupArray),
                    false,
                    customTrackSelectedListener, null
                )
                tabFragments.put(i, tabFragment)
                tabTrackTypes.add(trackType)
                break
            }
        }
        tabFragments.put(
            tabFragments.size(),
            PlayBackSpeedViewFragment(playbackSpeed, playbackSpeedListener)
        )
        tabTrackTypes.add(C.TRACK_TYPE_CUSTOM_BASE)
        setFragment(tabFragments[position])
    }

    private fun setFragment(fragment: Fragment) {
        this.fragment = fragment
    }

    fun setSettingItemClickListener(settingItemClickListener: SettingItemClickListener) {
        this.settingItemClickListener = settingItemClickListener
    }

    interface OnCustomTrackSelectedListener {
        fun onTrackSelected()
    }

    /**
     * Returns whether a renderer is disabled.
     *
     * @param rendererIndex Renderer index.
     * @return Whether the renderer is disabled.
     */
    fun getIsDisabled(rendererIndex: Int): Boolean {
        if (tabFragments[rendererIndex] is TrackSelectionFragment) {
            val rendererView = tabFragments[rendererIndex] as TrackSelectionFragment?
            return rendererView != null && rendererView.isDisabled
        }
        return false
    }

    /**
     * Returns the list of selected track selection overrides for the specified renderer. There will
     * be at most one override for each track group.
     *
     * @param rendererIndex Renderer index.
     * @return The list of track selection overrides for this renderer.
     */
    fun getOverrides(rendererIndex: Int): List<SelectionOverride> {
        if (tabFragments[rendererIndex] is TrackSelectionFragment) {
            val rendererView = tabFragments[rendererIndex] as TrackSelectionFragment?
            return rendererView?.overrides ?: emptyList()
        }
        return emptyList()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // We need to own the view to let tab layout work correctly on all API levels. We can't use
        // AlertDialog because it owns the view itself, so we use AppCompatDialog instead, themed using
        // the AlertDialog theme overlay with force-enabled title.
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.peekHeight =
            Resources.getSystem().displayMetrics.heightPixels
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.isDraggable = false
        return dialog
    }

    override fun getTheme(): Int {
        return R.style.TrackSelectionDialogThemeOverlay
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val dialogView = inflater.inflate(R.layout.track_selection_dialog, container, false)
        if (fragment == null) {
            fragment = SettingsFragment(settingItemClickListener, strVideoQuality, playbackSpeed)
        }
        childFragmentManager.beginTransaction().replace(R.id.fragment_container_view, fragment!!)
            .commit()
        return dialogView
    }

    interface SettingItemClickListener {
        fun settingItemClick(adapterPosition: Int)
    }

    companion object {
        private var isVideoSelectionEnabled = false
        private var isAudioSelectionEnabled = false
        private var isSubtitleSelectionEnabled = true
        private lateinit var strVideoQuality: String

        /**
         * Creates a dialog for a given [DefaultTrackSelector], whose parameters will be
         * automatically updated when tracks are selected.
         *
         * @param trackSelector         The [DefaultTrackSelector].
         * @param onDismissListener     A [DialogInterface.OnDismissListener] to call when the dialog is
         * @param playbackSpeedListener
         */
        @JvmStatic
        fun createForTrackSelector(
            position: Int,
            strVideoQuality: String,
            isVideoSelectionEnabled: Boolean,
            isAudioSelectionEnabled: Boolean,
            isSubtitleSelectionEnabled: Boolean,
            trackSelector: DefaultTrackSelector,
            onDismissListener: DialogInterface.OnDismissListener,
            playbackSpeed: Float,
            playbackSpeedListener: PlaybackSpeedListener
        ): PlayerSettingsDialog {
            val mappedTrackInfo = Assertions.checkNotNull(trackSelector.currentMappedTrackInfo)
            val playerSettingsDialog = PlayerSettingsDialog(strVideoQuality, playbackSpeed)
            val parameters = trackSelector.parameters
            playerSettingsDialog.init(
                position,
                isVideoSelectionEnabled,
                isAudioSelectionEnabled,
                isSubtitleSelectionEnabled,
                mappedTrackInfo,  /* initialParameters = */
                parameters,  /* allowAdaptiveSelections= */ /* allowMultipleOverrides= */ /* onCustomTrackSelectedListener= */
                object : OnCustomTrackSelectedListener {
                    override fun onTrackSelected() {
                        val builder = parameters.buildUpon()
                        for (i in 0 until mappedTrackInfo.rendererCount) {
                            builder
                                .clearSelectionOverrides( /* rendererIndex= */i)
                                .setRendererDisabled( /* rendererIndex= */
                                    i,
                                    playerSettingsDialog.getIsDisabled( /* rendererIndex= */i)
                                )
                            val overrides =
                                playerSettingsDialog.getOverrides( /* rendererIndex= */i)
                            if (overrides.isNotEmpty()) {
                                builder.setSelectionOverride( /* rendererIndex= */
                                    i,
                                    mappedTrackInfo.getTrackGroups( /* rendererIndex= */i),
                                    overrides[0]
                                )
                            }
                        }
                        trackSelector.setParameters(builder)
                        playerSettingsDialog.dismiss()
                    }
                },
                onDismissListener,
                playbackSpeed,
                playbackSpeedListener
            )
            return playerSettingsDialog
        }

        /**
         * Returns whether a track selection dialog will have content to display if initialized with the
         * specified [DefaultTrackSelector] in its current state.
         */
        fun willHaveContent(trackSelector: DefaultTrackSelector): Boolean {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo
            return mappedTrackInfo != null && willHaveContent(mappedTrackInfo)
        }

        /**
         * Returns whether a track selection dialog will have content to display if initialized with the
         * specified [MappedTrackInfo].
         */
        private fun willHaveContent(mappedTrackInfo: MappedTrackInfo): Boolean {
            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (showTabForRenderer(mappedTrackInfo, i)) {
                    return true
                }
            }
            return false
        }

        private fun showTabForRenderer(
            mappedTrackInfo: MappedTrackInfo,
            rendererIndex: Int
        ): Boolean {
            val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)
            if (trackGroupArray.length == 0) {
                return false
            }
            val trackType = mappedTrackInfo.getRendererType(rendererIndex)
            return isSupportedTrackType(trackType)
        }

        private fun isSupportedTrackType(trackType: Int): Boolean {
            return when (trackType) {
                C.TRACK_TYPE_VIDEO -> {
                    isVideoSelectionEnabled
                }
                C.TRACK_TYPE_AUDIO -> {
                    isAudioSelectionEnabled
                }
                C.TRACK_TYPE_TEXT -> {
                    isSubtitleSelectionEnabled
                }
                else -> false
            }
        }
    }

    init {
        Companion.strVideoQuality = strVideoQuality
        this.playbackSpeed = playbackSpeed
        tabFragments = SparseArray()
        tabTrackTypes = ArrayList()
    }
}
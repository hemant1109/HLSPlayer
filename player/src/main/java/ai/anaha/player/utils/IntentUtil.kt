package ai.anaha.player.utils

import android.content.Intent
import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaItem.*
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import java.util.*

/**
 * Util to read from and populate an intent.
 */
object IntentUtil {
    // Actions.
    const val ACTION_VIEW = "com.google.android.exoplayer.demo.action.VIEW"

    // Activity extras.
    const val PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders"

    private const val TITLE_EXTRA = "title"
    private const val MIME_TYPE_EXTRA = "mime_type"
    private const val CLIP_START_POSITION_MS_EXTRA = "clip_start_position_ms"
    private const val CLIP_END_POSITION_MS_EXTRA = "clip_end_position_ms"
    private const val AD_TAG_URI_EXTRA = "ad_tag_uri"
    private const val DRM_SCHEME_EXTRA = "drm_scheme"
    private const val DRM_LICENSE_URI_EXTRA = "drm_license_uri"
    private const val DRM_KEY_REQUEST_PROPERTIES_EXTRA = "drm_key_request_properties"
    private const val DRM_SESSION_FOR_CLEAR_CONTENT = "drm_session_for_clear_content"
    private const val DRM_MULTI_SESSION_EXTRA = "drm_multi_session"
    private const val DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA = "drm_force_default_license_uri"
    private const val SUBTITLE_URI_EXTRA = "subtitle_uri"
    private const val SUBTITLE_MIME_TYPE_EXTRA = "subtitle_mime_type"
    private const val SUBTITLE_LANGUAGE_EXTRA = "subtitle_language"

    /**
     * Creates a list of [media items][MediaItem] from an [Intent].
     */
    @JvmStatic
    fun createMediaItemsFromIntent(intent: Intent): List<MediaItem> {
        val mediaItems: MutableList<MediaItem> = ArrayList()
        val uri = intent.data
        mediaItems.add(createMediaItemFromIntent(uri, intent))
        return mediaItems
    }

    fun addToIntent(uri: String?, title: String?, intent: Intent) {
        val mediaItem = Builder()
        val clippingConfiguration = ClippingConfiguration.Builder()
        val adaptiveMimeType =
            Util.getAdaptiveMimeTypeForContentType(Util.inferContentType(Uri.parse(uri), null))
        mediaItem
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title).build()
            )
            .setMimeType(adaptiveMimeType)
            .setClippingConfiguration(clippingConfiguration.build())
        addToIntent(listOf(mediaItem.build()), intent)
    }

    /**
     * Populates the intent with the given list of [media items][MediaItem].
     */
    fun addToIntent(mediaItems: List<MediaItem>, intent: Intent) {
        Assertions.checkArgument(mediaItems.isNotEmpty())
        if (mediaItems.size == 1) {
            val mediaItem = mediaItems[0]
            val localConfiguration = Assertions.checkNotNull(mediaItem.localConfiguration)
            intent.setAction(ACTION_VIEW).data = mediaItem.localConfiguration!!.uri
            if (mediaItem.mediaMetadata.title != null) {
                intent.putExtra(TITLE_EXTRA, mediaItem.mediaMetadata.title)
            }
            addPlaybackPropertiesToIntent(localConfiguration, intent)
            addClippingConfigurationToIntent(
                mediaItem.clippingConfiguration, intent
            )
        }
    }

    private fun createMediaItemFromIntent(
        uri: Uri?, intent: Intent, extrasKeySuffix: String = ""
    ): MediaItem {
        val mimeType = intent.getStringExtra(MIME_TYPE_EXTRA + extrasKeySuffix)
        val title = intent.getStringExtra(TITLE_EXTRA + extrasKeySuffix)
        val adTagUri = intent.getStringExtra(AD_TAG_URI_EXTRA + extrasKeySuffix)
        val subtitleConfiguration = createSubtitleConfiguration(intent, extrasKeySuffix)
        val builder = Builder()
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setClippingConfiguration(
                ClippingConfiguration.Builder()
                    .setStartPositionMs(
                        intent.getLongExtra(CLIP_START_POSITION_MS_EXTRA + extrasKeySuffix, 0)
                    )
                    .setEndPositionMs(
                        intent.getLongExtra(
                            CLIP_END_POSITION_MS_EXTRA + extrasKeySuffix, C.TIME_END_OF_SOURCE
                        )
                    )
                    .build()
            )
        if (adTagUri != null) {
            builder.setAdsConfiguration(
                AdsConfiguration.Builder(Uri.parse(adTagUri)).build()
            )
        }
        if (subtitleConfiguration != null) {
            builder.setSubtitleConfigurations(ImmutableList.of(subtitleConfiguration))
        }
        return populateDrmPropertiesFromIntent(builder, intent, extrasKeySuffix).build()
    }

    private fun createSubtitleConfiguration(
        intent: Intent, extrasKeySuffix: String
    ): SubtitleConfiguration? {
        return if (!intent.hasExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix)) {
            null
        } else SubtitleConfiguration.Builder(
            Uri.parse(intent.getStringExtra(SUBTITLE_URI_EXTRA + extrasKeySuffix))
        )
            .setMimeType(
                Assertions.checkNotNull(
                    intent.getStringExtra(
                        SUBTITLE_MIME_TYPE_EXTRA + extrasKeySuffix
                    )
                )
            )
            .setLanguage(intent.getStringExtra(SUBTITLE_LANGUAGE_EXTRA + extrasKeySuffix))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    private fun populateDrmPropertiesFromIntent(
        builder: Builder, intent: Intent, extrasKeySuffix: String
    ): Builder {
        val schemeKey = DRM_SCHEME_EXTRA + extrasKeySuffix
        val drmSchemeExtra = intent.getStringExtra(schemeKey) ?: return builder
        val headers: MutableMap<String, String> = HashMap()
        val keyRequestPropertiesArray = intent.getStringArrayExtra(
            DRM_KEY_REQUEST_PROPERTIES_EXTRA + extrasKeySuffix
        )
        if (keyRequestPropertiesArray != null) {
            var i = 0
            while (i < keyRequestPropertiesArray.size) {
                headers[keyRequestPropertiesArray[i]] = keyRequestPropertiesArray[i + 1]
                i += 2
            }
        }
        val drmUuid = Util.getDrmUuid(Util.castNonNull(drmSchemeExtra))
        if (drmUuid != null) {
            builder.setDrmConfiguration(
                DrmConfiguration.Builder(drmUuid)
                    .setLicenseUri(intent.getStringExtra(DRM_LICENSE_URI_EXTRA + extrasKeySuffix))
                    .setMultiSession(
                        intent.getBooleanExtra(DRM_MULTI_SESSION_EXTRA + extrasKeySuffix, false)
                    )
                    .setForceDefaultLicenseUri(
                        intent.getBooleanExtra(
                            DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA + extrasKeySuffix, false
                        )
                    )
                    .setLicenseRequestHeaders(headers)
                    .forceSessionsForAudioAndVideoTracks(
                        intent.getBooleanExtra(
                            DRM_SESSION_FOR_CLEAR_CONTENT + extrasKeySuffix,
                            false
                        )
                    )
                    .build()
            )
        }
        return builder
    }

    private fun addPlaybackPropertiesToIntent(
        localConfiguration: LocalConfiguration, intent: Intent, extrasKeySuffix: String = ""
    ) {
        intent
            .putExtra(MIME_TYPE_EXTRA + extrasKeySuffix, localConfiguration.mimeType)
            .putExtra(
                AD_TAG_URI_EXTRA + extrasKeySuffix,
                if (localConfiguration.adsConfiguration != null) localConfiguration.adsConfiguration!!.adTagUri.toString() else null
            )
        if (localConfiguration.drmConfiguration != null) {
            addDrmConfigurationToIntent(
                localConfiguration.drmConfiguration,
                intent,
                extrasKeySuffix
            )
        }
        if (!localConfiguration.subtitleConfigurations.isEmpty()) {
            Assertions.checkState(localConfiguration.subtitleConfigurations.size == 1)
            val subtitleConfiguration = localConfiguration.subtitleConfigurations[0]
            intent.putExtra(
                SUBTITLE_URI_EXTRA + extrasKeySuffix,
                subtitleConfiguration.uri.toString()
            )
            intent.putExtra(
                SUBTITLE_MIME_TYPE_EXTRA + extrasKeySuffix,
                subtitleConfiguration.mimeType
            )
            intent.putExtra(
                SUBTITLE_LANGUAGE_EXTRA + extrasKeySuffix,
                subtitleConfiguration.language
            )
        }
    }

    private fun addDrmConfigurationToIntent(
        drmConfiguration: DrmConfiguration?, intent: Intent, extrasKeySuffix: String
    ) {
        intent.putExtra(DRM_SCHEME_EXTRA + extrasKeySuffix, drmConfiguration!!.scheme.toString())
        intent.putExtra(
            DRM_LICENSE_URI_EXTRA + extrasKeySuffix,
            if (drmConfiguration.licenseUri != null) drmConfiguration.licenseUri.toString() else null
        )
        intent.putExtra(DRM_MULTI_SESSION_EXTRA + extrasKeySuffix, drmConfiguration.multiSession)
        intent.putExtra(
            DRM_FORCE_DEFAULT_LICENSE_URI_EXTRA + extrasKeySuffix,
            drmConfiguration.forceDefaultLicenseUri
        )
        val drmKeyRequestProperties = arrayOfNulls<String>(
            drmConfiguration.licenseRequestHeaders.size * 2
        )
        var index = 0
        for ((key, value) in drmConfiguration.licenseRequestHeaders) {
            drmKeyRequestProperties[index++] = key
            drmKeyRequestProperties[index++] = value
        }
        intent.putExtra(DRM_KEY_REQUEST_PROPERTIES_EXTRA + extrasKeySuffix, drmKeyRequestProperties)
        val forcedDrmSessionTrackTypes: List<Int> = drmConfiguration.forcedSessionTrackTypes
        if (forcedDrmSessionTrackTypes.isNotEmpty()) {
            // Only video and audio together are supported.
            Assertions.checkState(
                forcedDrmSessionTrackTypes.size == 2 && forcedDrmSessionTrackTypes.contains(C.TRACK_TYPE_VIDEO)
                        && forcedDrmSessionTrackTypes.contains(C.TRACK_TYPE_AUDIO)
            )
            intent.putExtra(DRM_SESSION_FOR_CLEAR_CONTENT + extrasKeySuffix, true)
        }
    }

    private fun addClippingConfigurationToIntent(
        clippingConfiguration: ClippingConfiguration,
        intent: Intent,
        extrasKeySuffix: String = ""
    ) {
        if (clippingConfiguration.startPositionMs != 0L) {
            intent.putExtra(
                CLIP_START_POSITION_MS_EXTRA + extrasKeySuffix,
                clippingConfiguration.startPositionMs
            )
        }
        if (clippingConfiguration.endPositionMs != C.TIME_END_OF_SOURCE) {
            intent.putExtra(
                CLIP_END_POSITION_MS_EXTRA + extrasKeySuffix, clippingConfiguration.endPositionMs
            )
        }
    }
}
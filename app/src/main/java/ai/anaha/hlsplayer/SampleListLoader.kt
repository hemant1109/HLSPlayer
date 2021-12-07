package ai.anaha.hlsplayer

import ai.anaha.hlsplayer.SampleListLoader.PlaylistGroup
import ai.anaha.hlsplayer.hlsutils.DemoUtil
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.util.JsonReader
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaItem.*
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.upstream.DataSourceInputStream
import com.google.android.exoplayer2.upstream.DataSourceUtil
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

@SuppressLint("StaticFieldLeak")
class SampleListLoader(
    private val context: Context,
    val onDataLoadListener: SampleChooserActivity.OnDataLoadListener,
) : AsyncTask<String?, Void?, List<PlaylistGroup>>() {
    private var sawError = false

    override fun doInBackground(vararg uris: String?): List<PlaylistGroup> {
        val result: MutableList<PlaylistGroup> = ArrayList()
        val dataSource = DemoUtil.getDataSourceFactory(context).createDataSource()
        for (uri in uris) {
            val dataSpec = DataSpec(Uri.parse(uri))
            val inputStream: InputStream = DataSourceInputStream(dataSource, dataSpec)
            try {
                readPlaylistGroups(
                    JsonReader(
                        InputStreamReader(
                            inputStream,
                            StandardCharsets.UTF_8
                        )
                    ), result
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sample list: $uri", e)
                sawError = true
            } finally {
                DataSourceUtil.closeQuietly(dataSource)
            }
        }
        return result
    }

    override fun onPostExecute(result: List<PlaylistGroup>) {
        if (sawError) {
            onDataLoadListener.onError()
        }
        onDataLoadListener.onDataLoad(result)
    }

    @Throws(IOException::class)
    private fun readPlaylistGroups(reader: JsonReader, groups: MutableList<PlaylistGroup>) {
        reader.beginArray()
        while (reader.hasNext()) {
            readPlaylistGroup(reader, groups)
        }
        reader.endArray()
    }

    @Throws(IOException::class)
    private fun readPlaylistGroup(reader: JsonReader, groups: MutableList<PlaylistGroup>) {
        var groupName = ""
        val playlistHolders = ArrayList<PlaylistHolder>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "name" -> groupName = reader.nextString()
                "samples" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        playlistHolders.add(readEntry(reader, false))
                    }
                    reader.endArray()
                }
                "_comment" -> reader.nextString() // Ignore.
                else -> throw IOException("Unsupported name: $name",  /* cause= */null)
            }
        }
        reader.endObject()
        val group = getGroup(groupName, groups)
        group.playlists.addAll(playlistHolders)
    }

    @Throws(IOException::class)
    private fun readEntry(reader: JsonReader, insidePlaylist: Boolean): PlaylistHolder {
        var uri: Uri? = null
        var extension: String? = null
        var title: String? = null
        var children: ArrayList<PlaylistHolder>? = null
        var subtitleUri: Uri? = null
        var subtitleMimeType: String? = null
        var subtitleLanguage: String? = null
        var drmUuid: UUID? = null
        var drmLicenseUri: String? = null
        var drmLicenseRequestHeaders = ImmutableMap.of<String?, String?>()
        var drmSessionForClearContent = false
        var drmMultiSession = false
        var drmForceDefaultLicenseUri = false
        val clippingConfiguration = ClippingConfiguration.Builder()
        val mediaItem = Builder()
        reader.beginObject()
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "name" -> title = reader.nextString()
                "uri" -> uri = Uri.parse(reader.nextString())
                "extension" -> extension = reader.nextString()
                "clip_start_position_ms" -> clippingConfiguration.setStartPositionMs(reader.nextLong())
                "clip_end_position_ms" -> clippingConfiguration.setEndPositionMs(reader.nextLong())
                "ad_tag_uri" -> mediaItem.setAdsConfiguration(
                    AdsConfiguration.Builder(Uri.parse(reader.nextString())).build()
                )
                "drm_scheme" -> drmUuid = Util.getDrmUuid(reader.nextString())
                "drm_license_uri", "drm_license_url" -> drmLicenseUri = reader.nextString()
                "drm_key_request_properties" -> {
                    val requestHeaders: MutableMap<String?, String?> = HashMap()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        requestHeaders[reader.nextName()] = reader.nextString()
                    }
                    reader.endObject()
                    drmLicenseRequestHeaders = ImmutableMap.copyOf(requestHeaders)
                }
                "drm_session_for_clear_content" -> drmSessionForClearContent = reader.nextBoolean()
                "drm_multi_session" -> drmMultiSession = reader.nextBoolean()
                "drm_force_default_license_uri" -> drmForceDefaultLicenseUri = reader.nextBoolean()
                "subtitle_uri" -> subtitleUri = Uri.parse(reader.nextString())
                "subtitle_mime_type" -> subtitleMimeType = reader.nextString()
                "subtitle_language" -> subtitleLanguage = reader.nextString()
                "playlist" -> {
                    Assertions.checkState(!insidePlaylist, "Invalid nesting of playlists")
                    children = ArrayList()
                    reader.beginArray()
                    while (reader.hasNext()) {
                        children.add(readEntry(reader,  /* insidePlaylist= */true))
                    }
                    reader.endArray()
                }
                else -> throw IOException("Unsupported attribute name: $name",  /* cause= */null)
            }
        }
        reader.endObject()
        return if (children != null) {
            val mediaItems: MutableList<MediaItem> =
                ArrayList()
            for (i in children.indices) {
                mediaItems.addAll(children[i].mediaItems)
            }
            PlaylistHolder(title, mediaItems)
        } else {
            val adaptiveMimeType =
                uri?.let { Util.inferContentType(it, extension) }?.let {
                    Util.getAdaptiveMimeTypeForContentType(
                        it
                    )
                }
            mediaItem
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(title).build()
                )
                .setMimeType(adaptiveMimeType)
                .setClippingConfiguration(clippingConfiguration.build())
            if (drmUuid != null) {
                mediaItem.setDrmConfiguration(
                    DrmConfiguration.Builder(drmUuid)
                        .setLicenseUri(drmLicenseUri)
                        .setLicenseRequestHeaders(drmLicenseRequestHeaders)
                        .forceSessionsForAudioAndVideoTracks(drmSessionForClearContent)
                        .setMultiSession(drmMultiSession)
                        .setForceDefaultLicenseUri(drmForceDefaultLicenseUri)
                        .build()
                )
            } else {
                Assertions.checkState(
                    drmLicenseUri == null,
                    "drm_uuid is required if drm_license_uri is set."
                )
                Assertions.checkState(
                    drmLicenseRequestHeaders.isEmpty(),
                    "drm_uuid is required if drm_key_request_properties is set."
                )
                Assertions.checkState(
                    !drmSessionForClearContent,
                    "drm_uuid is required if drm_session_for_clear_content is set."
                )
                Assertions.checkState(
                    !drmMultiSession,
                    "drm_uuid is required if drm_multi_session is set."
                )
                Assertions.checkState(
                    !drmForceDefaultLicenseUri,
                    "drm_uuid is required if drm_force_default_license_uri is set."
                )
            }
            if (subtitleUri != null && subtitleMimeType != null) {
                val subtitleConfiguration = SubtitleConfiguration.Builder(subtitleUri)
                    .setMimeType(subtitleMimeType)
                    .setLanguage(subtitleLanguage)
                    .build()
                mediaItem.setSubtitleConfigurations(
                    ImmutableList.of(
                        subtitleConfiguration
                    )
                )
            }
            PlaylistHolder(title, listOf(mediaItem.build()))
        }
    }

    private fun getGroup(groupName: String, groups: MutableList<PlaylistGroup>): PlaylistGroup {
        for (i in groups.indices) {
            if (Util.areEqual(groupName, groups[i].title)) {
                return groups[i]
            }
        }
        val group = PlaylistGroup(groupName)
        groups.add(group)
        return group
    }

    class PlaylistHolder(title: String?, mediaItems: List<MediaItem>) {
        val title: String?
        val mediaItems: List<MediaItem>

        init {
            Assertions.checkArgument(mediaItems.isNotEmpty())
            this.title = title
            this.mediaItems = Collections.unmodifiableList(ArrayList(mediaItems))
        }
    }

    class PlaylistGroup(val title: String) {
        val playlists: MutableList<PlaylistHolder>

        init {
            playlists = ArrayList()
        }
    }

    companion object {
        private const val TAG = "SampleListLoader"
    }
}
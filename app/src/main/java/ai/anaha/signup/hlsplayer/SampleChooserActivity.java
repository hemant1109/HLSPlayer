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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.JsonReader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaItem.ClippingConfiguration;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceInputStream;
import com.google.android.exoplayer2.upstream.DataSourceUtil;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import ai.anaha.signup.hlsplayer.hlsutils.DemoUtil;
import ai.anaha.signup.hlsplayer.hlsutils.IntentUtil;

/**
 * An activity for selecting from a list of media samples.
 */
public class SampleChooserActivity extends AppCompatActivity implements OnChildClickListener {

    private static final String TAG = "SampleChooserActivity";
    private static final String GROUP_POSITION_PREFERENCE_KEY = "sample_chooser_group_position";
    private static final String CHILD_POSITION_PREFERENCE_KEY = "sample_chooser_child_position";

    private String[] uris;
    private boolean useExtensionRenderers;
    private SampleAdapter sampleAdapter;
    private MenuItem preferExtensionDecodersMenuItem;
    private ExpandableListView sampleListView;
    private Button btnPlay;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_chooser_activity);

        sampleAdapter = new SampleAdapter();
        sampleListView = findViewById(R.id.sample_list);

        sampleListView.setAdapter(sampleAdapter);
        sampleListView.setOnChildClickListener(this);

        Intent intent = getIntent();
        String dataUri = intent.getDataString();
        if (dataUri != null) {
            uris = new String[]{dataUri};
        } else {
            ArrayList<String> uriList = new ArrayList<>();
            AssetManager assetManager = getAssets();
            try {
                for (String asset : assetManager.list("")) {
                    if (asset.endsWith(".exolist.json")) {
                        uriList.add("asset:///" + asset);
                    }
                }
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
                        .show();
            }
            uris = new String[uriList.size()];
            uriList.toArray(uris);
            Arrays.sort(uris);
        }

        useExtensionRenderers = DemoUtil.useExtensionRenderers();
        loadSample();

        TextInputEditText txtUrls = findViewById(R.id.txtUrls);
        btnPlay = findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(v -> {
            if (Objects.requireNonNull(txtUrls.getText()).length() > 0) {
                // Save the selected item first to be able to restore it if the tested code crashes.
                SharedPreferences.Editor prefEditor = getPreferences(MODE_PRIVATE).edit();
                prefEditor.putInt(GROUP_POSITION_PREFERENCE_KEY, -1);
                prefEditor.putInt(CHILD_POSITION_PREFERENCE_KEY, -1);
                prefEditor.apply();

                MediaItem.Builder mediaItem = new MediaItem.Builder();
                String uri = txtUrls.getText().toString();

                ClippingConfiguration.Builder clippingConfiguration =
                        new ClippingConfiguration.Builder();
                @Nullable
                String adaptiveMimeType =
                        Util.getAdaptiveMimeTypeForContentType(Util.inferContentType(Uri.parse(uri), null));
                mediaItem
                        .setUri(uri)
                        .setMediaMetadata(new MediaMetadata.Builder().setTitle("Anaha").build())
                        .setMimeType(adaptiveMimeType)
                        .setClippingConfiguration(clippingConfiguration.build());
                Intent playerActivityIntent = new Intent(this, AnahaPlayerActivity.class);
                playerActivityIntent.putExtra(
                        IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
                        isNonNullAndChecked(preferExtensionDecodersMenuItem));
                IntentUtil.addToIntent(Collections.singletonList(mediaItem.build()), playerActivityIntent);
                startActivity(playerActivityIntent);
                txtUrls.getText().clear();
            }
        });

        txtUrls.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                btnPlay.setEnabled(s.length() > 0);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.sample_chooser_menu, menu);
        preferExtensionDecodersMenuItem = menu.findItem(R.id.prefer_extension_decoders);
        preferExtensionDecodersMenuItem.setVisible(useExtensionRenderers);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        item.setChecked(!item.isChecked());
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        sampleAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            // Empty results are triggered if a permission is requested while another request was already
            // pending and can be safely ignored in this case.
            return;
        }
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSample();
        } else {
            Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
                    .show();
            finish();
        }
    }

    private void loadSample() {
        checkNotNull(uris);
        for (String s : uris) {
            Uri uri = Uri.parse(s);
            if (Util.maybeRequestReadExternalStoragePermission(this, uri)) {
                return;
            }
        }

        SampleListLoader loaderTask = new SampleListLoader();
        loaderTask.execute(uris);
    }

    private void onPlaylistGroups(final List<PlaylistGroup> groups, boolean sawError) {
        if (sawError) {
            Toast.makeText(getApplicationContext(), R.string.sample_list_load_error, Toast.LENGTH_LONG)
                    .show();
        }
        sampleAdapter.setPlaylistGroups(groups);

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        int groupPosition = preferences.getInt(GROUP_POSITION_PREFERENCE_KEY, /* defValue= */ -1);
        int childPosition = preferences.getInt(CHILD_POSITION_PREFERENCE_KEY, /* defValue= */ -1);
        // Clear the group and child position if either are unset or if either are out of bounds.
        if (groupPosition != -1
                && childPosition != -1
                && groupPosition < groups.size()
                && childPosition < groups.get(groupPosition).playlists.size()) {
            sampleListView.expandGroup(groupPosition); // shouldExpandGroup does not work without this.
            sampleListView.setSelectedChild(groupPosition, childPosition, /* shouldExpandGroup= */ true);
        }
    }

    @Override
    public boolean onChildClick(
            ExpandableListView parent, View view, int groupPosition, int childPosition, long id) {
        // Save the selected item first to be able to restore it if the tested code crashes.
        SharedPreferences.Editor prefEditor = getPreferences(MODE_PRIVATE).edit();
        prefEditor.putInt(GROUP_POSITION_PREFERENCE_KEY, groupPosition);
        prefEditor.putInt(CHILD_POSITION_PREFERENCE_KEY, childPosition);
        prefEditor.apply();

        PlaylistHolder playlistHolder = (PlaylistHolder) view.getTag();
        Intent intent = new Intent(this, AnahaPlayerActivity.class);
        intent.putExtra(
                IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
                isNonNullAndChecked(preferExtensionDecodersMenuItem));
        IntentUtil.addToIntent(playlistHolder.mediaItems, intent);
        startActivity(intent);
        return true;
    }

    private static boolean isNonNullAndChecked(@Nullable MenuItem menuItem) {
        // Temporary workaround for layouts that do not inflate the options menu.
        return menuItem != null && menuItem.isChecked();
    }

    @SuppressLint("StaticFieldLeak")
    private final class SampleListLoader extends AsyncTask<String, Void, List<PlaylistGroup>> {

        private boolean sawError;

        @Override
        protected List<PlaylistGroup> doInBackground(String... uris) {
            List<PlaylistGroup> result = new ArrayList<>();
            Context context = getApplicationContext();
            DataSource dataSource = DemoUtil.getDataSourceFactory(context).createDataSource();
            for (String uri : uris) {
                DataSpec dataSpec = new DataSpec(Uri.parse(uri));
                InputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
                try {
                    readPlaylistGroups(new JsonReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)), result);
                } catch (Exception e) {
                    Log.e(TAG, "Error loading sample list: " + uri, e);
                    sawError = true;
                } finally {
                    DataSourceUtil.closeQuietly(dataSource);
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(List<PlaylistGroup> result) {
            onPlaylistGroups(result, sawError);
        }

        private void readPlaylistGroups(JsonReader reader, List<PlaylistGroup> groups)
                throws IOException {
            reader.beginArray();
            while (reader.hasNext()) {
                readPlaylistGroup(reader, groups);
            }
            reader.endArray();
        }

        private void readPlaylistGroup(JsonReader reader, List<PlaylistGroup> groups)
                throws IOException {
            String groupName = "";
            ArrayList<PlaylistHolder> playlistHolders = new ArrayList<>();

            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "name":
                        groupName = reader.nextString();
                        break;
                    case "samples":
                        reader.beginArray();
                        while (reader.hasNext()) {
                            playlistHolders.add(readEntry(reader, false));
                        }
                        reader.endArray();
                        break;
                    case "_comment":
                        reader.nextString(); // Ignore.
                        break;
                    default:
                        throw new IOException("Unsupported name: " + name, /* cause= */ null);
                }
            }
            reader.endObject();

            PlaylistGroup group = getGroup(groupName, groups);
            group.playlists.addAll(playlistHolders);
        }

        private PlaylistHolder readEntry(JsonReader reader, boolean insidePlaylist) throws IOException {
            Uri uri = null;
            String extension = null;
            String title = null;
            ArrayList<PlaylistHolder> children = null;
            Uri subtitleUri = null;
            String subtitleMimeType = null;
            String subtitleLanguage = null;
            UUID drmUuid = null;
            String drmLicenseUri = null;
            ImmutableMap<String, String> drmLicenseRequestHeaders = ImmutableMap.of();
            boolean drmSessionForClearContent = false;
            boolean drmMultiSession = false;
            boolean drmForceDefaultLicenseUri = false;
            ClippingConfiguration.Builder clippingConfiguration =
                    new ClippingConfiguration.Builder();

            MediaItem.Builder mediaItem = new MediaItem.Builder();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "name":
                        title = reader.nextString();
                        break;
                    case "uri":
                        uri = Uri.parse(reader.nextString());
                        break;
                    case "extension":
                        extension = reader.nextString();
                        break;
                    case "clip_start_position_ms":
                        clippingConfiguration.setStartPositionMs(reader.nextLong());
                        break;
                    case "clip_end_position_ms":
                        clippingConfiguration.setEndPositionMs(reader.nextLong());
                        break;
                    case "ad_tag_uri":
                        mediaItem.setAdsConfiguration(
                                new MediaItem.AdsConfiguration.Builder(Uri.parse(reader.nextString())).build());
                        break;
                    case "drm_scheme":
                        drmUuid = Util.getDrmUuid(reader.nextString());
                        break;
                    case "drm_license_uri":
                    case "drm_license_url": // For backward compatibility only.
                        drmLicenseUri = reader.nextString();
                        break;
                    case "drm_key_request_properties":
                        Map<String, String> requestHeaders = new HashMap<>();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            requestHeaders.put(reader.nextName(), reader.nextString());
                        }
                        reader.endObject();
                        drmLicenseRequestHeaders = ImmutableMap.copyOf(requestHeaders);
                        break;
                    case "drm_session_for_clear_content":
                        drmSessionForClearContent = reader.nextBoolean();
                        break;
                    case "drm_multi_session":
                        drmMultiSession = reader.nextBoolean();
                        break;
                    case "drm_force_default_license_uri":
                        drmForceDefaultLicenseUri = reader.nextBoolean();
                        break;
                    case "subtitle_uri":
                        subtitleUri = Uri.parse(reader.nextString());
                        break;
                    case "subtitle_mime_type":
                        subtitleMimeType = reader.nextString();
                        break;
                    case "subtitle_language":
                        subtitleLanguage = reader.nextString();
                        break;
                    case "playlist":
                        checkState(!insidePlaylist, "Invalid nesting of playlists");
                        children = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            children.add(readEntry(reader, /* insidePlaylist= */ true));
                        }
                        reader.endArray();
                        break;
                    default:
                        throw new IOException("Unsupported attribute name: " + name, /* cause= */ null);
                }
            }
            reader.endObject();

            if (children != null) {
                List<MediaItem> mediaItems = new ArrayList<>();
                for (int i = 0; i < children.size(); i++) {
                    mediaItems.addAll(children.get(i).mediaItems);
                }
                return new PlaylistHolder(title, mediaItems);
            } else {
                @Nullable
                String adaptiveMimeType =
                        Util.getAdaptiveMimeTypeForContentType(Util.inferContentType(Objects.requireNonNull(uri), extension));
                mediaItem
                        .setUri(uri)
                        .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build())
                        .setMimeType(adaptiveMimeType)
                        .setClippingConfiguration(clippingConfiguration.build());
                if (drmUuid != null) {
                    mediaItem.setDrmConfiguration(
                            new MediaItem.DrmConfiguration.Builder(drmUuid)
                                    .setLicenseUri(drmLicenseUri)
                                    .setLicenseRequestHeaders(drmLicenseRequestHeaders)
                                    .forceSessionsForAudioAndVideoTracks(drmSessionForClearContent)
                                    .setMultiSession(drmMultiSession)
                                    .setForceDefaultLicenseUri(drmForceDefaultLicenseUri)
                                    .build());
                } else {
                    checkState(drmLicenseUri == null, "drm_uuid is required if drm_license_uri is set.");
                    checkState(
                            drmLicenseRequestHeaders.isEmpty(),
                            "drm_uuid is required if drm_key_request_properties is set.");
                    checkState(
                            !drmSessionForClearContent,
                            "drm_uuid is required if drm_session_for_clear_content is set.");
                    checkState(!drmMultiSession, "drm_uuid is required if drm_multi_session is set.");
                    checkState(
                            !drmForceDefaultLicenseUri,
                            "drm_uuid is required if drm_force_default_license_uri is set.");
                }
                if (subtitleUri != null) {
                    MediaItem.SubtitleConfiguration subtitleConfiguration =
                            new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                    .setMimeType(
                                            Objects.requireNonNull(checkNotNull(
                                                    subtitleMimeType,
                                                    "subtitle_mime_type is required if subtitle_uri is set.")))
                                    .setLanguage(subtitleLanguage)
                                    .build();
                    mediaItem.setSubtitleConfigurations(ImmutableList.of(subtitleConfiguration));
                }
                return new PlaylistHolder(title, Collections.singletonList(mediaItem.build()));
            }
        }

        private PlaylistGroup getGroup(String groupName, List<PlaylistGroup> groups) {
            for (int i = 0; i < groups.size(); i++) {
                if (Util.areEqual(groupName, groups.get(i).title)) {
                    return groups.get(i);
                }
            }
            PlaylistGroup group = new PlaylistGroup(groupName);
            groups.add(group);
            return group;
        }
    }

    private final class SampleAdapter extends BaseExpandableListAdapter implements OnClickListener {

        private List<PlaylistGroup> playlistGroups;

        public SampleAdapter() {
            playlistGroups = Collections.emptyList();
        }

        public void setPlaylistGroups(List<PlaylistGroup> playlistGroups) {
            this.playlistGroups = playlistGroups;
            notifyDataSetChanged();
        }

        @Override
        public PlaylistHolder getChild(int groupPosition, int childPosition) {
            return getGroup(groupPosition).playlists.get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public View getChildView(
                int groupPosition,
                int childPosition,
                boolean isLastChild,
                View convertView,
                ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = getLayoutInflater().inflate(R.layout.sample_list_item, parent, false);
            }
            initializeChildView(view, getChild(groupPosition, childPosition));
            return view;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return getGroup(groupPosition).playlists.size();
        }

        @Override
        public PlaylistGroup getGroup(int groupPosition) {
            return playlistGroups.get(groupPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public View getGroupView(
                int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view =
                        getLayoutInflater()
                                .inflate(android.R.layout.simple_expandable_list_item_1, parent, false);
            }
            ((TextView) view).setText(getGroup(groupPosition).title);
            return view;
        }

        @Override
        public int getGroupCount() {
            return playlistGroups.size();
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public void onClick(View view) {

        }

        private void initializeChildView(View view, PlaylistHolder playlistHolder) {
            view.setTag(playlistHolder);
            TextView sampleTitle = view.findViewById(R.id.sample_title);
            sampleTitle.setText(playlistHolder.title);
        }
    }

    private static final class PlaylistHolder {

        public final String title;
        public final List<MediaItem> mediaItems;

        private PlaylistHolder(String title, List<MediaItem> mediaItems) {
            checkArgument(!mediaItems.isEmpty());
            this.title = title;
            this.mediaItems = Collections.unmodifiableList(new ArrayList<>(mediaItems));
        }
    }

    private static final class PlaylistGroup {

        public final String title;
        public final List<PlaylistHolder> playlists;

        public PlaylistGroup(String title) {
            this.title = title;
            this.playlists = new ArrayList<>();
        }
    }
}
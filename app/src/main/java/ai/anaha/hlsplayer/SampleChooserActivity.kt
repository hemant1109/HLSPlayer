package ai.anaha.hlsplayer


import ai.anaha.hlsplayer.SampleListLoader.PlaylistHolder
import ai.anaha.hlsplayer.hlsutils.DemoUtil
import ai.anaha.hlsplayer.hlsutils.IntentUtil
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ExpandableListView
import android.widget.ExpandableListView.OnChildClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.util.Util
import com.google.android.material.textfield.TextInputEditText
import java.io.IOException
import java.util.*
import kotlin.jvm.internal.Intrinsics

/**
 * An activity for selecting from a list of media samples.
 */
class SampleChooserActivity : AppCompatActivity(), OnChildClickListener {
    private lateinit var uris: Array<String?>
    private var useExtensionRenderers = false
    private var sampleAdapter: SampleAdapter? = null
    private var preferExtensionDecodersMenuItem: MenuItem? = null
    private var btnPlay: Button? = null
    private var sampleListView: ExpandableListView? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sample_chooser_activity)
        sampleAdapter = SampleAdapter()
        sampleListView = findViewById(R.id.sample_list)
        sampleListView?.setAdapter(sampleAdapter)
        sampleListView?.setOnChildClickListener(this)
        val intent = intent
        val dataUri = intent.dataString
        if (dataUri != null) {
            uris = arrayOf(dataUri)
        } else {
            val uriList = ArrayList<String>()
            val assetManager = assets
            try {
                for (asset in assetManager.list("")!!) {
                    if (asset.endsWith(".exolist.json")) {
                        uriList.add("asset:///$asset")
                    }
                }
            } catch (e: IOException) {
                Toast.makeText(
                    applicationContext,
                    R.string.sample_list_load_error,
                    Toast.LENGTH_LONG
                )
                    .show()
            }
            uris = arrayOfNulls(uriList.size)
            uriList.toArray(uris)
            Arrays.sort(uris)
        }
        useExtensionRenderers = DemoUtil.useExtensionRenderers()
        loadSample()
        val txtUrls = findViewById<TextInputEditText>(R.id.txtUrls)
        btnPlay = findViewById(R.id.btn_play)
        btnPlay?.setOnClickListener {
            if (txtUrls.text?.isNotEmpty() == true) {
                // Save the selected item first to be able to restore it if the tested code crashes.
                val prefEditor = getPreferences(MODE_PRIVATE).edit()
                prefEditor.putInt(GROUP_POSITION_PREFERENCE_KEY, -1)
                prefEditor.putInt(CHILD_POSITION_PREFERENCE_KEY, -1)
                prefEditor.apply()
                val uri = txtUrls.text.toString()
                val playerActivityIntent = Intent(this, HLSPlayerActivity::class.java)
                playerActivityIntent.putExtra(
                    IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
                    isNonNullAndChecked(preferExtensionDecodersMenuItem)
                )
                IntentUtil.addToIntent(uri, "Anaha", playerActivityIntent)
                startActivity(playerActivityIntent)
                txtUrls.text!!.clear()
            }
        }
        txtUrls.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                btnPlay?.isEnabled = s.isNotEmpty()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.sample_chooser_menu, menu)
        preferExtensionDecodersMenuItem = menu.findItem(R.id.prefer_extension_decoders)
        preferExtensionDecodersMenuItem?.isVisible = useExtensionRenderers
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        item.isChecked = !item.isChecked
        return true
    }

    public override fun onStart() {
        super.onStart()
        sampleAdapter!!.notifyDataSetChanged()
    }

    public override fun onStop() {
        super.onStop()
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
            loadSample()
        } else {
            Toast.makeText(applicationContext, R.string.sample_list_load_error, Toast.LENGTH_LONG)
                .show()
            finish()
        }
    }

    private fun loadSample() {
        Intrinsics.checkNotNull(uris)
        for (s in uris) {
            val uri = Uri.parse(s)
            if (Util.maybeRequestReadExternalStoragePermission(this, uri)) {
                return
            }
        }
        val loaderTask = SampleListLoader(this, object : OnDataLoadListener {
            override fun onDataLoad(groups: List<SampleListLoader.PlaylistGroup>) {
                sampleAdapter!!.setPlaylistGroups(groups)
                val preferences = getPreferences(Context.MODE_PRIVATE)
                val groupPosition = preferences.getInt(
                    GROUP_POSITION_PREFERENCE_KEY,  /* defValue= */
                    -1
                )
                val childPosition = preferences.getInt(
                    CHILD_POSITION_PREFERENCE_KEY,  /* defValue= */
                    -1
                )
                // Clear the group and child position if either are unset or if either are out of bounds.
                if (groupPosition != -1 && childPosition != -1 && groupPosition < groups.size && childPosition < groups[groupPosition].playlists.size) {
                    sampleListView!!.expandGroup(groupPosition) // shouldExpandGroup does not work without this.
                    sampleListView?.setSelectedChild(
                        groupPosition,
                        childPosition,
                        true
                    )
                }
            }

            override fun onError() {
                Toast.makeText(
                    this@SampleChooserActivity,
                    R.string.sample_list_load_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        })
        loaderTask.execute(*uris)
    }

    interface OnDataLoadListener {
        fun onDataLoad(groups: List<SampleListLoader.PlaylistGroup>)
        fun onError()
    }

    override fun onChildClick(
        parent: ExpandableListView, view: View, groupPosition: Int, childPosition: Int, id: Long
    ): Boolean {
        // Save the selected item first to be able to restore it if the tested code crashes.
        val prefEditor = getPreferences(MODE_PRIVATE).edit()
        prefEditor.putInt(GROUP_POSITION_PREFERENCE_KEY, groupPosition)
        prefEditor.putInt(CHILD_POSITION_PREFERENCE_KEY, childPosition)
        prefEditor.apply()
        val playlistHolder = view.tag as PlaylistHolder
        val intent = Intent(this, HLSPlayerActivity::class.java)
        intent.putExtra(
            IntentUtil.PREFER_EXTENSION_DECODERS_EXTRA,
            isNonNullAndChecked(preferExtensionDecodersMenuItem)
        )
        IntentUtil.addToIntent(playlistHolder.mediaItems, intent)
        startActivity(intent)
        return true
    }

    companion object {
        const val GROUP_POSITION_PREFERENCE_KEY = "sample_chooser_group_position"
        const val CHILD_POSITION_PREFERENCE_KEY = "sample_chooser_child_position"
        private fun isNonNullAndChecked(menuItem: MenuItem?): Boolean {
            // Temporary workaround for layouts that do not inflate the options menu.
            return menuItem != null && menuItem.isChecked
        }
    }
}
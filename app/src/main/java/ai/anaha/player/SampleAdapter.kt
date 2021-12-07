package ai.anaha.player

import ai.anaha.player.SampleListLoader.PlaylistGroup
import ai.anaha.player.SampleListLoader.PlaylistHolder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView

class SampleAdapter : BaseExpandableListAdapter(), View.OnClickListener {
    private var playlistGroups: List<PlaylistGroup>

    fun setPlaylistGroups(playlistGroups: List<PlaylistGroup>) {
        this.playlistGroups = playlistGroups
        notifyDataSetChanged()
    }

    override fun getChild(groupPosition: Int, childPosition: Int): PlaylistHolder {
        return getGroup(groupPosition).playlists[childPosition]
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View? {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(parent?.context)
                .inflate(R.layout.sample_list_item, parent, false)
        }
        initializeChildView(view, getChild(groupPosition, childPosition))
        return view
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        return getGroup(groupPosition).playlists.size
    }

    override fun getGroup(groupPosition: Int): PlaylistGroup {
        return playlistGroups[groupPosition]
    }

    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        var view = convertView
        if (view == null) {
            view = LayoutInflater.from(parent?.context)
                .inflate(android.R.layout.simple_expandable_list_item_1, parent, false)
        }
        (view as TextView).text = getGroup(groupPosition).title
        return view
    }

    override fun getGroupCount(): Int {
        return playlistGroups.size
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
        return true
    }

    override fun onClick(view: View) {}

    private fun initializeChildView(view: View?, playlistHolder: PlaylistHolder) {
        view?.tag = playlistHolder
        val sampleTitle = view?.findViewById<TextView>(R.id.sample_title)
        sampleTitle?.text = playlistHolder.title
    }

    init {
        playlistGroups = emptyList()
    }
}
package ai.anaha.signup.hlsplayer.adapters;

import static ai.anaha.signup.hlsplayer.hlsutils.TrackSelectionDialog.getTrackTypeString;

import android.content.res.Resources;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import java.util.ArrayList;

public final class FragmentAdapter extends FragmentPagerAdapter {

    private final SparseArray<Fragment> tabFragments;
    private final Resources resources;
    private final ArrayList<Integer> tabTrackTypes;

    public FragmentAdapter(Resources resources, FragmentManager fragmentManager,
                           SparseArray<Fragment> tabFragments, ArrayList<Integer> tabTrackTypes) {
        super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        this.tabFragments = tabFragments;
        this.resources = resources;
        this.tabTrackTypes = tabTrackTypes;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return tabFragments.valueAt(position);
    }

    @Override
    public int getCount() {
        return tabFragments.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return getTrackTypeString(resources, tabTrackTypes.get(position));
    }
}

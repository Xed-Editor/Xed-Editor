package com.rk.xededitor.MainActivity;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;

import java.util.ArrayList;

import io.github.rosemoe.sora.text.Content;

public class mAdapter extends FragmentStatePagerAdapter {
    public static ArrayList<Fragment> fragments = new ArrayList<>();
    public static ArrayList<String> titles = new ArrayList<>();
    public static ArrayList<Uri> uris = new ArrayList<>();
    private boolean removeing = false;

    public mAdapter(@NonNull FragmentManager fm) {
        super(fm);
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        return fragments.get(position);
    }

    @Override
    public int getCount() {
        return fragments.size();
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        if (removeing) {
            return PagerAdapter.POSITION_NONE;
        } else {
            final int index = fragments.indexOf(object);
            if (index == -1) {
                return POSITION_NONE;
            } else {
                return index;
            }
        }
    }

    public boolean addFragment(Fragment frag, String title, DocumentFile file) {
        if (fragments.contains(frag) || uris.contains(file.getUri())) {
            return true;
        }
        uris.add(file.getUri());
        fragments.add(frag);
        titles.add(title);
        notifyDataSetChanged();
        return false;
    }

    public void removeFragment(int position) {
        fragments.remove(position);
        titles.remove(position);
        MainActivity.fileList.remove(position);
        removeing = true;
        DynamicFragment.contents.remove(position);
        notifyDataSetChanged();
        removeing = false;
        uris.remove(position);
    }

    public void closeOthers(int index) {
        Fragment selectedObj = fragments.get(index);
        DocumentFile selectedFile = MainActivity.fileList.get(index);
        MainActivity.fileList.clear();
        MainActivity.fileList.add(selectedFile);
        fragments.clear();
        fragments.add(selectedObj);
        String title = titles.get(index);
        titles.clear();
        titles.add(title);
        Uri suri = uris.get(index);
        uris.clear();
        uris.add(suri);
        Content content = DynamicFragment.contents.get(index);
        DynamicFragment.contents.clear();
        DynamicFragment.contents.add(content);

        notifyDataSetChanged();
    }

    public void clear() {
        fragments.clear();
        titles.clear();
        uris.clear();
        MainActivity.fileList.clear();
        DynamicFragment.contents.clear();
        notifyDataSetChanged();
    }
}

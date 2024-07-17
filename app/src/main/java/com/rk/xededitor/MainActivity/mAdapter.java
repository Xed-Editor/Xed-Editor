package com.rk.xededitor.MainActivity;

import static com.rk.xededitor.MainActivity.StaticData.fragments;
import static com.rk.xededitor.MainActivity.StaticData.mTabLayout;
import static com.rk.xededitor.MainActivity.StaticData.menu;


import android.net.Uri;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;

import com.rk.xededitor.R;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

public class mAdapter extends FragmentStatePagerAdapter {
  
  private boolean removing = false;
  
  public mAdapter(@NonNull FragmentManager fm) {
    super(fm);
  }
  
  public static CodeEditor getCurrentEditor() {
    return fragments.get(mTabLayout.getSelectedTabPosition()).editor;
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
    if (removing) {
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
  
  public boolean addFragment(DynamicFragment frag, String title, DocumentFile file) {
    if (fragments.contains(frag)) {
      return true;
    }else {
      var uri = file.getUri();
      for(DynamicFragment f : fragments){
        if (f.file.getUri().equals(uri)){
          return true;
        }
      }
    }
    fragments.add(frag);
    notifyDataSetChanged();
    return false;
  }
  
  public void onEditorRemove(DynamicFragment fragment) {
    fragment.releaseEditor();
    if (fragments.size() <= 1) {
      MenuItem undo = menu.findItem(R.id.undo);
      MenuItem redo = menu.findItem(R.id.redo);
      undo.setVisible(false);
      redo.setVisible(false);
      
    }
    
    
  }
  
  public void removeFragment(int position) {
    onEditorRemove(fragments.get(position));
    fragments.remove(position);
    removing = true;
    notifyDataSetChanged();
    removing = false;
  }
  
  
  public void closeOthers(int index) {
    DynamicFragment selectedObj = fragments.get(index);
    for (DynamicFragment fragment : fragments) {
      if (!fragment.equals(selectedObj)) {
        onEditorRemove(fragment);
      }
    }
    fragments.clear();
    fragments.add(selectedObj);
    notifyDataSetChanged();
  }
  
  public void clear() {
    for (DynamicFragment fragment : fragments) {
      onEditorRemove(fragment);
    }
    fragments.clear();
    notifyDataSetChanged();
  }
}

package com.rk.xededitor.MainActivity;

import static com.rk.xededitor.MainActivity.Data.contents;
import static com.rk.xededitor.MainActivity.Data.fileList;
import static com.rk.xededitor.MainActivity.Data.fragments;
import static com.rk.xededitor.MainActivity.Data.mTabLayout;
import static com.rk.xededitor.MainActivity.Data.menu;
import static com.rk.xededitor.MainActivity.Data.titles;
import static com.rk.xededitor.MainActivity.Data.uris;

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
  
  @NonNull
  @Override
  public Fragment getItem(int position) {
    return fragments.get(position);
  }
  
  @Override
  public int getCount() {
    return fragments.size();
  }
  
  public static CodeEditor getCurrentEditor(){
    return fragments.get(mTabLayout.getSelectedTabPosition()).editor;
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
    if (fragments.contains(frag) || uris.contains(file.getUri())) {
      return true;
    }
    uris.add(file.getUri());
    fragments.add(frag);
    titles.add(title);
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
    titles.remove(position);
    fileList.remove(position);
    removing = true;
    if(!contents.isEmpty()){
      contents.remove(position);
    }
    notifyDataSetChanged();
    removing = false;
    uris.remove(position);
  }
  
  
  public void closeOthers(int index) {
    DynamicFragment selectedObj = fragments.get(index);
    for (DynamicFragment fragment : fragments) {
      if (!fragment.equals(selectedObj)) {
        onEditorRemove(fragment);
      }
    }
    DocumentFile selectedFile = fileList.get(index);
    fileList.clear();
    fileList.add(selectedFile);
    fragments.clear();
    fragments.add(selectedObj);
    String title = titles.get(index);
    titles.clear();
    titles.add(title);
    Uri suri = uris.get(index);
    uris.clear();
    uris.add(suri);
    Content content = contents.get(index);
    contents.clear();
    contents.add(content);
    
    notifyDataSetChanged();
  }
  
  public void clear() {
    for (DynamicFragment fragment : fragments) {
      onEditorRemove(fragment);
    }
    fragments.clear();
    titles.clear();
    uris.clear();
    fileList.clear();
    contents.clear();
    notifyDataSetChanged();
  }
}

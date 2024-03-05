package com.rk.xededitor.activities.MainActivity;

import android.content.Context;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.PopupMenu;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.R;
import com.rk.xededitor.activities.MainActivity.MainActivity;
import com.rk.xededitor.activities.MainActivity.mFragment;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.text.*;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.*;
import java.util.*;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public class EditorManager {
  private FragmentManager fragmentManager;
  //  private final CodeEditor editor;
  private final Context ctx;
  private static TabLayout tablayout;
  public static HashMap<TabLayout.Tab, Uri> uris;
  public static HashSet<String> strs;
  public static HashMap<TabLayout.Tab, mFragment> fragments;
  private static PopupMenu popupMenu;
  
  private final JavaLanguage java;
  private final EmptyLanguage text;
  private mFragment first_fragmnt;

  // CodeEditor editor,
  public EditorManager(Context ctx) {

    //   this.editor = editor;
    this.ctx = ctx;
    tablayout = MainActivity.getBinding().editorTabLayout;
    // map = new HashMap<>();
    strs = new HashSet<>();
    uris = new HashMap<>();
    fragments = new HashMap<>();
    fragmentManager = MainActivity.getManager();
    java = new JavaLanguage();
    text = new EmptyLanguage();

    tablayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            mFragment f;
            if (tab.getPosition() == 0) {
              f = first_fragmnt;
            } else {
              f = fragments.get(tab);
            }

            if (f != null) {
              FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
              fragmentTransaction.replace(R.id.fragment_container, f);
              fragmentTransaction.commitNow();
            }
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {}
        });
  }

  private Language getLang(String name) {
    if (name.endsWith(".java")) {
      return java;
    } else {
      return text;
    }
  }

  public void newEditor(DocumentFile file) {
    // this method will run when a new tab is opened
    Uri uri = file.getUri();
    if (uris.containsValue(uri)) {
      rkUtils.toast(ctx, "already there ");
      return;
    }
    

    if (MainActivity.getBinding().fragmentContainer.getVisibility() == View.GONE) {
      rkUtils.setVisibility(MainActivity.binding.empty, false);
      rkUtils.setVisibility(MainActivity.getBinding().fragmentContainer, true);
    }
    if (tablayout.getVisibility() == View.GONE) {
      rkUtils.setVisibility(tablayout, true);
    }

    String name = file.getName();
    if (strs.contains(name)) {
      name = file.getParentFile().getName() + "/" + name;
      strs.add(name);
    } else {
      strs.add(name);
    }
    final String final_name = name;
    name = null;

    TabLayout.Tab tab = tablayout.newTab();
    tab.setText(final_name);
    final mFragment fragment = new mFragment(ctx, uri, getLang(final_name));

    fragments.put(tab, fragment);

    uris.put(tab, uri);
    tablayout.addTab(tab);

    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
    fragmentTransaction.replace(R.id.fragment_container, fragment);
    fragmentTransaction.commitNow();

    if (tablayout.getVisibility() == View.GONE) {
      rkUtils.setVisibility(tablayout, true);
    }

    // don't open drawer if its first time
    if(tablayout.getTabCount() == 1){
      first_fragmnt = fragment;
    }else{
      MainActivity.getBinding().drawerLayout.close();
    }
 
    tab.select();

    tab.view.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            if (tablayout.getTabAt(tablayout.getSelectedTabPosition()).equals(tab)) {
              if (popupMenu != null) {
                // remove click listner of previous tab
                popupMenu.setOnMenuItemClickListener(null);
              }

              popupMenu = new PopupMenu(ctx, tab.view);
              popupMenu.getMenuInflater().inflate(R.menu.popup_menu, popupMenu.getMenu());
              popupMenu.setOnMenuItemClickListener(
                  new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                      /*Note : any variable refrenced here in a different scope
                      any modifications done to them may not result in any effect
                      unless they are static*/
                      int id = item.getItemId();
                      if (id == R.id.close_this) {

                        
                        if (tablayout.getTabCount() == 1) {
                          uris.clear();
                          strs.clear();
                          
                            for (mFragment fr : fragments.values()) {
                          FragmentTransaction transaction =
                              MainActivity.getManager().beginTransaction();
                          transaction.remove(fr);
                          transaction.commit();
                        }
                          

                          fragments.clear();
                          tablayout.removeAllTabs();
                          rkUtils.setVisibility(tablayout, false);
                          rkUtils.setVisibility(MainActivity.getBinding().fragmentContainer, false);
                          rkUtils.setVisibility(MainActivity.binding.empty, true);
                          return true;
                        }
            
                          FragmentTransaction transaction =
                                MainActivity.getManager().beginTransaction();
                            transaction.remove(fragments.get(tablayout.getTabAt(tablayout.getSelectedTabPosition())));
                            transaction.commit();
                    
                    
                    
                        tablayout.removeTab(tab);
                        fragments.remove(tab);

                        uris.remove(uri);
                        strs.remove(final_name);
                        final int tab_count = tablayout.getTabCount();

                        if (tab_count > 0) {
                          if (tab_count - 1 >= 0) {
                            tablayout.selectTab(tablayout.getTabAt(tab_count - 1));
                          } else {
                            tablayout.selectTab(tablayout.getTabAt(0));
                          }
                        }

                      } else if (id == R.id.close_others) {

                        for (int i = tablayout.getTabCount() - 1; i >= 0; i--) {
                          TabLayout.Tab t = tablayout.getTabAt(i);
                          if (!t.equals(tab)) {
                            tablayout.removeTab(t);
                          }
                        }
                        for (mFragment fr : fragments.values()) {
                          if(fr != fragment){
                            FragmentTransaction transaction =
                              MainActivity.getManager().beginTransaction();
                          transaction.remove(fr);
                          transaction.commit();
                          }
                          
                        }

                        fragments.clear();
                        fragments.put(tab, fragment);
                        uris.clear();
                        uris.put(tab, uri);
                        strs.clear();
                        strs.add(final_name);

                      } else if (id == R.id.close_all) {
                        tablayout.removeAllTabs();
                        for (mFragment fr : fragments.values()) {
                          FragmentTransaction transaction =
                              MainActivity.getManager().beginTransaction();
                          transaction.remove(fr);
                          transaction.commit();
                        }
                        fragments.clear();
                        uris.clear();
                        strs.clear();
                        rkUtils.setVisibility(tablayout, false);
                        rkUtils.setVisibility(MainActivity.getBinding().fragmentContainer, false);
                        rkUtils.setVisibility(MainActivity.binding.empty, true);
                      }
                      return true;
                    }
                  });
              popupMenu.show();
            }
          }
        });
  }

  public static String save_files(Context ctx) throws Exception {

    if (fragments == null || uris == null) {
      return "Can't Save";
    }
    if (fragments.isEmpty() || uris.isEmpty()) {
      return "Nothing to Save here";
    }
    for (int i = 0; i < tablayout.getTabCount(); i++) {
      TabLayout.Tab tab = tablayout.getTabAt(i);
      Content content = fragments.get(tab).getContent();
      Uri uri = uris.get(tab);

      if (content == null || uri == null) {
        continue;
      }
      OutputStream outputStream = ctx.getContentResolver().openOutputStream(uri, "wt");
      if (outputStream == null) {
        return "OutputStream is null";
      }
      ContentIO.writeTo(content, outputStream, true);
      // output stream closed automatically by writeTo method
      outputStream = null;
    }
    return "saved!";
  }
}

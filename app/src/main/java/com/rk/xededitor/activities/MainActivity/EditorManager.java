package com.rk.xededitor.activities.MainActivity;

import android.content.Context;
import android.net.Uri;
import androidx.appcompat.widget.PopupMenu;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.util.*;
import java.util.HashMap;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.rk.xededitor.R;

public class EditorManager {

  private final CodeEditor editor;
  private final Context ctx;
  private static TabLayout tablayout;
  private static HashMap<TabLayout.Tab, Uri> uris;
  private static HashSet<String> strs;
  private static HashMap<TabLayout.Tab, Content> map;
  private static PopupMenu popupMenu;

  public EditorManager(CodeEditor editor, Context ctx) {

    this.editor = editor;
    this.ctx = ctx;
    tablayout = MainActivity.getTabLayout();
    map = new HashMap<>();
    strs = new HashSet<>();
    uris = new HashMap<>();

    tablayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {

            Content contnt = map.get(tab);
            if (contnt != null) {
              editor.setText(contnt);
            } else {
              Log.e("tab click", "tabData is null");
            }
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {}
        });
  }

  public void newEditor(DocumentFile file) {
    // this method will run when a new tab is opened

    Uri uri = file.getUri();
    String name = file.getName();

    if (uris.containsValue(uri)) {
      // MainActivity.getBinding().drawerLayout.close();
      rkUtils.toast(ctx, "already there ");
      return;
    }

    if (editor.getVisibility() == View.GONE) {
      rkUtils.setVisibility(MainActivity.binding.empty, false);
      rkUtils.setVisibility(editor, true);
    }
    if (tablayout.getVisibility() == View.GONE) {
      rkUtils.setVisibility(tablayout, true);
    }

    if (strs.contains(name)) {
      name = file.getParentFile().getName() + "/" + name;
      strs.add(name);
    } else {
      strs.add(name);
    }
    final String final_name = name;

    Content contntx = null;
    try {
      InputStream inputStream;
      inputStream = ctx.getContentResolver().openInputStream(uri);
      contntx = ContentIO.createFrom(inputStream);
      inputStream.close();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
    final Content contnt = contntx;
    TabLayout.Tab tab = tablayout.newTab();
    tab.setText(name);
    map.put(tab, contnt);
    uris.put(tab, uri);
    tablayout.addTab(tab);

    if (tablayout.getVisibility() == View.GONE) {
      rkUtils.setVisibility(tablayout, true);
    }

    if (tablayout.getTabCount() == 1) {
      editor.setText(contnt);
    }
    tab.select();
    MainActivity.getBinding().drawerLayout.close();
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
                          map.clear();
                      
                      
                          tablayout.removeAllTabs();
                          rkUtils.setVisibility(tablayout, false);
                          rkUtils.setVisibility(editor, false);
                          rkUtils.setVisibility(MainActivity.binding.empty, true);
                          return true;
                        }

                        tablayout.removeTab(tab);
                        map.remove(tab);
                        uris.remove(uri);
                        strs.remove(final_name);
                        if (tablayout.getTabCount() > 0) {
                          tablayout.selectTab(tablayout.getTabAt(0));
                        } else {

                          rkUtils.setVisibility(tablayout, false);
                          rkUtils.setVisibility(editor, false);
                          rkUtils.setVisibility(MainActivity.binding.empty, true);
                        }

                      } else if (id == R.id.close_others) {

                        for (int i = tablayout.getTabCount() - 1; i >= 0; i--) {
                          TabLayout.Tab t = tablayout.getTabAt(i);
                          if (!t.equals(tab)) {
                            tablayout.removeTab(t);
                          }
                        }

                        map.clear();
                        map.putIfAbsent(tab, contnt);
                        uris.clear();
                        uris.put(tab, uri);
                        strs.clear();
                        strs.add(final_name);

                      } else if (id == R.id.close_all) {
                        tablayout.removeAllTabs();
                        map.clear();
                        uris.clear();
                        strs.clear();
                        rkUtils.setVisibility(tablayout, false);
                        rkUtils.setVisibility(editor, false);
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

  public static void save_files(Context ctx) {
    if (map == null || uris == null) {
      rkUtils.toast(ctx, "Can't save");
    }
    if (map.isEmpty() || uris.isEmpty()) {
      rkUtils.toast(ctx, "map or uri is empty");
    }
    for (int i = 0; i < tablayout.getTabCount(); i++) {
      TabLayout.Tab tab = tablayout.getTabAt(i);
      Content contentx = map.get(tab);
      Uri uri = uris.get(tab);

      if (contentx == null || uri == null) {
        rkUtils.toast(ctx, "content or uri is null");
        continue;
      }
      try {
        OutputStream outputStream = ctx.getContentResolver().openOutputStream(uri, "wt");
        if (outputStream != null) {
          ContentIO.writeTo(contentx, outputStream, true);
          rkUtils.toast(ctx, "saved!");
        } else {
          rkUtils.toast(ctx, "InputStream is null");
        }
      } catch (IOException e) {
        e.printStackTrace();
        rkUtils.toast(ctx, "Unknown Error \n" + e.toString());
      }
    }
  }
}

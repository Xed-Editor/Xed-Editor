package com.rk.xededitor;

import android.content.Context;
import android.net.Uri;
import androidx.appcompat.widget.PopupMenu;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.HashMap;
import android.util.Log;
import android.view.*;
import android.widget.*;

public class EditorManager {

    private final CodeEditor editor;
    private final Context ctx;
    private TabLayout tablayout;
    private static HashSet<Uri> uris;
    private static HashSet<String> strs;
    private static HashMap<TabLayout.Tab, Content> map;
    private PopupMenu popupMenu;

    public EditorManager(CodeEditor editor, Context ctx) {

        this.editor = editor;
        this.ctx = ctx;
        tablayout = MainActivity.getTabLayout();
        map = new HashMap<>();
        strs = new HashSet<>();
        uris = new HashSet<>();

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
        if (editor.getVisibility() == View.GONE) {
            rkUtils.setVisibility(MainActivity.binding.empty, false);
            rkUtils.setVisibility(editor, true);
        }

        Uri uri = file.getUri();
        String name = file.getName();

        if (uris.contains(uri)) {
            MainActivity.getBinding().drawerLayout.close();
            return;
        } else {
            uris.add(uri);
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
                            popupMenu
                                    .getMenuInflater()
                                    .inflate(R.menu.popup_menu, popupMenu.getMenu());
                            popupMenu.setOnMenuItemClickListener(
                                    new PopupMenu.OnMenuItemClickListener() {
                                        public boolean onMenuItemClick(MenuItem item) {
                                            /*Note : any variable refrenced here in a different scope 
                                            any modifications done to them may not result in any effect 
                                            unless they are static*/
                                            int id = item.getItemId();
                                            if (id == R.id.close_this) {

                                                tablayout.removeTab(tab);
                                                map.remove(tab);
                                                uris.remove(uri);
                                                strs.remove(final_name);
                                                if (tablayout.getChildCount() > 1) {
                                                    tablayout.selectTab(tablayout.getTabAt(0));
                                                }

                                            } else if (id == R.id.close_others) {

                                                for (int i = tablayout.getTabCount() - 1;
                                                        i >= 0;
                                                        i--) {
                                                    TabLayout.Tab t = tablayout.getTabAt(i);
                                                    if (!t.equals(tab)) {
                                                        tablayout.removeTab(t);
                                                    }
                                                }

                                                map.clear();
                                                map.putIfAbsent(tab, contnt);
                                                uris.clear();
                                                uris.add(uri);
                                                strs.clear();
                                                strs.add(final_name);

                                            } else if (id == R.id.close_all) {
                                                tablayout.removeAllTabs();
                                                map.clear();
                                                uris.clear();
                                                strs.clear();
                                            }
                                            return true;
                                        }
                                    });
                            popupMenu.show();
                        }
                    }
                });
    }
}

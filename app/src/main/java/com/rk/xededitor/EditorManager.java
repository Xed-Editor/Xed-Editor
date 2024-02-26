package com.rk.xededitor;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.tabs.TabLayout;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.InputStream;
import java.util.*;
import java.util.HashMap;
import android.util.Log;

public class EditorManager {

    private final CodeEditor editor;
    private final Context ctx;
    private TabLayout tablayout;
    private final HashSet<Integer> uris;
    private final HashSet<Integer> strs;
    private final HashMap<TabLayout.Tab, TabData> map;

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

                        TabData tabData = map.get(tab);
                        if (tabData != null) {
                            tabData.show();
                        } else {
                            Log.e("tab click", "tab is null");
                        }
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });
    }

    public void newEditor(DocumentFile file) {
        Uri uri = file.getUri();
        String name = file.getName();

        if (uris.contains(uri.hashCode())) {
            return;
            // dublicate
        } else {
            uris.add(uri.hashCode());
        }

        if (strs.contains(name.hashCode())) {
            name = file.getParentFile().getName() + "/" + name;
            strs.add(name.hashCode());
        } else {
            strs.add(name.hashCode());
        }

        TabLayout.Tab tab = tablayout.newTab();
        tab.setText(name);
        tablayout.addTab(tab);
        TabData tabData = new TabData(ctx, editor, uri);
        map.put(tab, tabData);

        rkUtils.setVisibility(tablayout, true);

        if (tablayout.getTabCount() == 1) {
            tabData.show();
        }
        tab.select();
        
    }

    public class TabData {
        private InputStream inputStream;
        private final Context ctx;
        private final CodeEditor editor;
        private final Uri uri;
        private Content contnt;

        public TabData(Context ctx, CodeEditor editor, Uri uri) {
            this.uri = uri;
            this.editor = editor;
            this.ctx = ctx;
            try {
                inputStream = ctx.getContentResolver().openInputStream(uri);
                this.contnt = ContentIO.createFrom(inputStream);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        public void show() {
            editor.setText(contnt);
        }
        
        
        //make sure to de_init_when tab is closed
        public void de_init() {
            try {
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}

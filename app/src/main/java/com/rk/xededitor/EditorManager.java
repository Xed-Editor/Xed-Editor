package com.rk.xededitor;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.tabs.TabLayout;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.InputStream;
import java.util.HashMap;

public class EditorManager {
    private static HashMap<String, HashMap<TabLayout.Tab, Uri>> map;
    private final CodeEditor editor;
    private final Context ctx;
    private TabLayout tablayout;

    public EditorManager(CodeEditor editor, Context ctx) {
        if (map == null) {
            map = new HashMap<>();
        }
        this.editor = editor;
        this.ctx = ctx;
        tablayout = MainActivity.getTabLayout();

        tablayout.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    @Override
                    public void onTabSelected(TabLayout.Tab tab) {
                        setText(tab);
                    }

                    @Override
                    public void onTabUnselected(TabLayout.Tab tab) {}

                    @Override
                    public void onTabReselected(TabLayout.Tab tab) {}
                });
    }

    private void setText(TabLayout.Tab tab) {
        try {
            InputStream inputStream =
                    ctx.getContentResolver()
                            .openInputStream(
                                    EditorManager.getMap().get(tab.getText().toString()).get(tab));
            editor.setText(ContentIO.createFrom(inputStream));
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void newEditor(DocumentFile file) {
        Uri uri = file.getUri();
        String name = file.getName();

        TabLayout.Tab tab = tablayout.newTab();

        if (!map.isEmpty() && map.get(name) != null && map.get(name).containsValue(uri)) {
            return;
        }

        if (!map.isEmpty() && map.containsKey(name)) {
            name = file.getParentFile().getName() + "/" + name;
        }

        tab.setText(name);
        tablayout.addTab(tab);

        HashMap<TabLayout.Tab, Uri> xHashmap = new HashMap<>();
        xHashmap.put(tab, uri);
        map.put(name, xHashmap);

        tab.select();

        if (tablayout.getTabCount() == 1) {
            setText(tab);
        }
    }

    public static HashMap<String, HashMap<TabLayout.Tab, Uri>> getMap() {
        return map;
    }
}

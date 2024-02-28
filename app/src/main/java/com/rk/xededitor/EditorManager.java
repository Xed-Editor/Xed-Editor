package com.rk.xededitor;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.InputStream;
import java.util.*;
import java.util.HashMap;
import android.util.Log;
import android.view.*;

public class EditorManager {

    private final CodeEditor editor;
    private final Context ctx;
    private TabLayout tablayout;
    private final HashSet<Integer> uris;
    private final HashSet<Integer> strs;
    private final HashMap<TabLayout.Tab, Content> map;

    // private TabLayout.Tab last_tab;

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
        if(editor.getVisibility() == View.GONE){
            rkUtils.setVisibility(MainActivity.binding.empty,false);
            rkUtils.setVisibility(editor,true);
            
        }
        
        
        
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

        
        Content contnt = null;
        try {
            InputStream inputStream;
            inputStream = ctx.getContentResolver().openInputStream(uri);
            contnt = ContentIO.createFrom(inputStream);
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        TabLayout.Tab tab = tablayout.newTab();
        tab.setText(name);
        map.put(tab, contnt);
        tablayout.addTab(tab);

        if(tablayout.getVisibility() == View.GONE){
            rkUtils.setVisibility(tablayout,true);
        }

        if (tablayout.getTabCount() == 1) {
            editor.setText(contnt);
        }
        tab.select();
    }
}

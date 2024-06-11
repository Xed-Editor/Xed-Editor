package com.rk.xededitor.MainActivity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.BatchReplacement.BatchReplacement;
import com.rk.xededitor.Decompress;
import com.rk.xededitor.MainActivity.TreeViewX.AndroidTreeView;
import com.rk.xededitor.MainActivity.TreeViewX.TreeNode;
import com.rk.xededitor.R;
import com.rk.xededitor.Settings.SettingsActivity;
import com.rk.xededitor.rkUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_DIRECTORY_SELECTION = 1002;
    public static TabLayout mTabLayout;
    public static List<DocumentFile> fileList;
    public static Menu menu;

    private static Activity activity;
    final int REQUEST_FILE_SELECTION = 123;
    NavigationView navigationView;

    private ViewPager viewPager;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private TreeNode root;
    private AndroidTreeView tView;
    private mAdapter adapter;
    private boolean isReselecting = false;
    private String SearchText = "";


    public static Activity getActivity() {
        return activity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dynamic);

        // apply all prefrances that are quied

activity = this;
        String jsonString = rkUtils.getSetting(this, "applyOnBoot", "{}");
rkUtils.toast(jsonString);
        rkUtils.applyPrefs(this);
        
        fileList = new ArrayList<>();
        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);


        if (!rkUtils.isDarkMode(this)) {
            //light mode
            getWindow().setNavigationBarColor(Color.parseColor("#FEF7FF"));
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        if (rkUtils.isDarkMode(this) && rkUtils.isOled(this)) {
            findViewById(R.id.drawer_layout).setBackgroundColor(Color.BLACK);
            findViewById(R.id.nav_view).setBackgroundColor(Color.BLACK);
            findViewById(R.id.main).setBackgroundColor(Color.BLACK);
            findViewById(R.id.appbar).setBackgroundColor(Color.BLACK);
            findViewById(R.id.toolbar).setBackgroundColor(Color.BLACK);
            findViewById(R.id.tabs).setBackgroundColor(Color.BLACK);
            findViewById(R.id.mainView).setBackgroundColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        }


        initViews();

        if (!new File(getExternalFilesDir(null) + "/unzip").exists()) {
            try {
                Decompress.unzipFromAssets(this, "files.zip", getExternalFilesDir(null) + "/unzip");
                new File(getExternalFilesDir(null) + "files").delete();
                new File(getExternalFilesDir(null) + "files.zip").delete();
                new File(getExternalFilesDir(null) + "textmate").delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }



        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if(mAdapter.fragments != null){
                    for(Fragment fragment : mAdapter.fragments){
                        DynamicFragment fragment1 = (DynamicFragment)fragment;
                        if(fragment1.isModified){
                            new MaterialAlertDialogBuilder(MainActivity.this)
                                    .setTitle("Unsaved Files")
                                    .setMessage("You have unsaved files!")
                                    .setNegativeButton("Cancel", null)
                                    .setNeutralButton("Save & Exit", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            onOptionsItemSelected(menu.findItem(R.id.action_all));
                                            finish();
                                        }
                                    })
                                    .setPositiveButton(
                                            "Exit",
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    finish();
                                                }
                                            })
                                    .show();
                        }
                        break;
                    }
                }
            }
        });

    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle the theme change here
        rkUtils.toast("Restart Required");
    }

    private void initViews() {

        // initialise the layout
        viewPager = findViewById(R.id.viewpager);
        mTabLayout = findViewById(R.id.tabs);
        viewPager.setOffscreenPageLimit(15);

        adapter = new mAdapter(getSupportFragmentManager());
        viewPager.setAdapter(adapter);
        mTabLayout.setupWithViewPager(viewPager);

        // viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));

        mTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).updateUndoRedo();

            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                PopupMenu popupMenu = new PopupMenu(activity, tab.view);
                MenuInflater inflater = popupMenu.getMenuInflater();
                inflater.inflate(R.menu.tab_menu, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        final int id = item.getItemId();
                        if (id == R.id.close_this) {
                            adapter.removeFragment(mTabLayout.getSelectedTabPosition());
                        } else if (id == R.id.close_others) {
                            adapter.closeOthers(viewPager.getCurrentItem());
                        } else if (id == R.id.close_all) {
                            adapter.clear();
                        }
                        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
                            TabLayout.Tab tab = mTabLayout.getTabAt(i);
                            if (tab != null) {
                                String name = mAdapter.titles.get(i);
                                if (name != null) {
                                    tab.setText(name);
                                }
                            }
                        }
                        if (mTabLayout.getTabCount() < 1) {
                            findViewById(R.id.tabs).setVisibility(View.GONE);
                            findViewById(R.id.mainView).setVisibility(View.GONE);
                            findViewById(R.id.openBtn).setVisibility(View.VISIBLE);

                        }
                        boolean visible = (!(mAdapter.fragments == null || mAdapter.fragments.isEmpty()));
                        menu.findItem(R.id.search).setVisible(visible);
                        menu.findItem(R.id.action_save).setVisible(visible);
                        menu.findItem(R.id.action_all).setVisible(visible);
                        menu.findItem(R.id.batchrep).setVisible(visible);


                        return true;
                    }
                });
                popupMenu.show();

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_SELECTION && resultCode == RESULT_OK && data != null) {
            Uri selectedFileUri = data.getData();
            findViewById(R.id.tabs).setVisibility(View.VISIBLE);
            findViewById(R.id.mainView).setVisibility(View.VISIBLE);
            findViewById(R.id.openBtn).setVisibility(View.GONE);
            newEditor(DocumentFile.fromSingleUri(this, selectedFileUri));
        } else if (requestCode == REQUEST_DIRECTORY_SELECTION && resultCode == RESULT_OK && data != null) {
            //findViewById(R.id.tabs).setVisibility(View.VISIBLE);
            findViewById(R.id.mainView).setVisibility(View.VISIBLE);
            //findViewById(R.id.openBtn).setVisibility(View.GONE);
            findViewById(R.id.safbuttons).setVisibility(View.GONE);
            findViewById(R.id.hscroll).setVisibility(View.VISIBLE);

            Uri treeUri = data.getData();
            DocumentFile rootFolder = DocumentFile.fromTreeUri(this, treeUri);
            if(Boolean.parseBoolean(rkUtils.getSetting(this,"legacyFileBrowser","true"))){
                if (isReselecting) {
                    List<TreeNode> nodes = new ArrayList<>(root.getChildren());
                    for (TreeNode node : nodes) {
                        tView.removeNode(node);
                    }
                    root.children.clear();
                    mTabLayout.removeAllTabs();
                    ((LinearLayout) findViewById(R.id.maindrawer)).removeAllViews();
                    isReselecting = false;
                }
                root = TreeNode.root();
                rkUtils.looper(rootFolder, root, 0);
                tView = new AndroidTreeView(this, root);
                View tv = tView.getView();
                LinearLayout.LayoutParams layout_params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                layout_params.setMargins(30, 20, 0, 0);
                tv.setLayoutParams(layout_params);
                ((LinearLayout) findViewById(R.id.maindrawer)).addView(tv);


            }else{
                new MA(this,rootFolder);
            }

        String name = rootFolder.getName();
            if (name.length() > 18) {
                name = rootFolder.getName().substring(0, 15) + "...";
            }

            ((TextView) findViewById(R.id.rootDirLabel)).setText(name);
            findViewById(R.id.drawerToolbar).setVisibility(View.VISIBLE);

        }
    }

    /*this method is called when user opens a file
    unlike newEditor which is called when opening a directory via file manager*/
    public void onNewEditor() {
        findViewById(R.id.openBtn).setVisibility(View.GONE);
        findViewById(R.id.tabs).setVisibility(View.VISIBLE);
        findViewById(R.id.mainView).setVisibility(View.VISIBLE);
        final boolean visible = !(mAdapter.fragments == null || mAdapter.fragments.isEmpty());
        menu.findItem(R.id.search).setVisible(visible);
        menu.findItem(R.id.action_save).setVisible(visible);
        menu.findItem(R.id.action_all).setVisible(visible);
        menu.findItem(R.id.batchrep).setVisible(visible);
        MenuItem undo = menu.findItem(R.id.undo);
        MenuItem redo = menu.findItem(R.id.redo);
        undo.setVisible(true);
        redo.setVisible(true);


    }

    public void onEditorRemove(DynamicFragment fragment) {
        fragment.releaseEditor();
        if (mAdapter.fragments.size() <= 1) {
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            undo.setVisible(false);
            redo.setVisible(false);

        }

    }

    public void newEditor(DocumentFile file) {
        final String file_name = file.getName();
        if (fileList.contains(file) || adapter.addFragment(new DynamicFragment(file, activity), file_name, file)) {
            rkUtils.toast("File already opened!");
            return;
        }

        fileList.add(file);

        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            if (tab != null) {
                String name = mAdapter.titles.get(tab.getPosition());
                if (name != null) {
                    tab.setText(name);
                }
            }
        }
        final boolean visible = !(mAdapter.fragments == null || mAdapter.fragments.isEmpty());

        menu.findItem(R.id.batchrep).setVisible(visible);
        menu.findItem(R.id.search).setVisible(visible);
        menu.findItem(R.id.action_save).setVisible(visible);
        menu.findItem(R.id.action_all).setVisible(visible);
        menu.findItem(R.id.batchrep).setVisible(visible);
        menu.findItem(R.id.search).setVisible(visible);

    }

    public void openFile(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // you can specify mime types here to filter certain types of files
        startActivityForResult(intent, REQUEST_FILE_SELECTION);
    }

    public void openDir(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_DIRECTORY_SELECTION);
    }

    public void reselctDir(View v) {
        isReselecting = true;
        openDir(null);
    }

    public void openDrawer(View v) {
        drawerLayout.open();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MainActivity.menu = menu;
        menu.findItem(R.id.search).setVisible(!(mAdapter.fragments == null || mAdapter.fragments.isEmpty()));
        menu.findItem(R.id.batchrep).setVisible(!(mAdapter.fragments == null || mAdapter.fragments.isEmpty()));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
            return true;
        }
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        if (id == R.id.action_save) {
            if (fileList.isEmpty()) {
                return true;
            }

            try {
                final int index = MainActivity.mTabLayout.getSelectedTabPosition();
                DynamicFragment fg = (DynamicFragment) mAdapter.fragments.get(index);

                TabLayout.Tab tab = mTabLayout.getTabAt(mTabLayout.getSelectedTabPosition());
                assert tab != null;
                String name = tab.getText().toString();
                if(name.charAt(name.length()-1 )== '*'){
                    fg.isModified = false;
                    tab.setText(name.substring(0,name.length()-1));
                }

                //Content content = fg.editor.getText();
                Content content = DynamicFragment.contents.get(mTabLayout.getSelectedTabPosition());
                OutputStream outputStream = getContentResolver().openOutputStream(fileList.get(index).getUri(), "wt");
                ContentIO.writeTo(content, outputStream, true);
                outputStream.close();
                outputStream = null;
                rkUtils.toast("saved!");

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_all) {
            for (int i = 0; i < mTabLayout.getTabCount(); i++) {
                TabLayout.Tab mtab = mTabLayout.getTabAt(i);

                OutputStream outputStream = null;
                try {
                    final int index = mtab.getPosition();
                    DynamicFragment fg = (DynamicFragment) mAdapter.fragments.get(index);

                    String name = mtab.getText().toString();
                    if(name.charAt(name.length()-1) == '*'){
                        fg.isModified = false;
                        mtab.setText(name.substring(0,name.length()-1));
                    }

                    //Content content = fg.editor.getText();
                    Content content = DynamicFragment.contents.get(index);
                    outputStream = getContentResolver().openOutputStream(fileList.get(index).getUri(), "wt");
                    ContentIO.writeTo(content, outputStream, true);

                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    outputStream = null;
                }
                rkUtils.toast("saved all");
            }
            return true;
        } else if (id == R.id.search) {

            View popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_search, null);
            TextView searchBox = popuop_view.findViewById(R.id.searchbox);
            if (!SearchText.equals("")) {
                searchBox.setText(SearchText);
            }

            AlertDialog dialog =
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Search")
                            .setView(popuop_view)
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton(
                                    "Search",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialogInterface, int i) {
                                            MenuItem undo = menu.findItem(R.id.undo);
                                            MenuItem redo = menu.findItem(R.id.redo);
                                            undo.setVisible(false);
                                            redo.setVisible(false);
                                            CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
                                            CheckBox checkBox = popuop_view.findViewById(R.id.case_senstive);
                                            SearchText = searchBox.getText().toString();
                                            editor.getSearcher().search(SearchText, new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, !checkBox.isChecked()));
                                            menu.findItem(R.id.search_next).setVisible(true);
                                            menu.findItem(R.id.search_previous).setVisible(true);
                                            menu.findItem(R.id.search_close).setVisible(true);
                                            menu.findItem(R.id.replace).setVisible(true);

                                        }
                                    })
                            .show();

            return true;
        } else if (id == R.id.search_next) {
            CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
            editor.getSearcher().gotoNext();
            return true;
        } else if (id == R.id.search_previous) {
            CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
            editor.getSearcher().gotoPrevious();
            return true;
        } else if (id == R.id.search_close) {
            CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
            editor.getSearcher().stopSearch();
            menu.findItem(R.id.search_next).setVisible(false);
            menu.findItem(R.id.search_previous).setVisible(false);
            menu.findItem(R.id.search_close).setVisible(false);
            menu.findItem(R.id.replace).setVisible(false);
            SearchText = "";
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            undo.setVisible(true);
            redo.setVisible(true);

            return true;
        } else if (id == R.id.replace) {
            View popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_replace, null);
            AlertDialog dialog =
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Replace")
                            .setView(popuop_view)
                            .setNegativeButton("Cancel", null).setPositiveButton("Replace All", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //fatass one liner
                                    ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor().getSearcher().replaceAll(((TextView) popuop_view.findViewById(R.id.replace_replacement)).getText().toString());
                                }
                            }).show();

        } else if (id == R.id.batchrep) {
            Intent intent = new Intent(this, BatchReplacement.class);
            startActivity(intent);
        } else if (id == R.id.undo) {
            ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).Undo();
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
            redo.setEnabled(editor.canRedo());
            undo.setEnabled(editor.canUndo());
        } else if (id == R.id.redo) {
            ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).Redo();
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
            redo.setEnabled(editor.canRedo());
            undo.setEnabled(editor.canUndo());
        }
        return super.onOptionsItemSelected(item);
    }
}


package com.rk.xededitor.MainActivity;

import static com.rk.xededitor.MainActivity.Data.REQUEST_DIRECTORY_SELECTION;
import static com.rk.xededitor.MainActivity.Data.activity;
import static com.rk.xededitor.MainActivity.Data.contents;
import static com.rk.xededitor.MainActivity.Data.fileList;
import static com.rk.xededitor.MainActivity.Data.fragments;
import static com.rk.xededitor.MainActivity.Data.mTabLayout;
import static com.rk.xededitor.MainActivity.Data.menu;
import static com.rk.xededitor.MainActivity.Data.rootFolder;
import static com.rk.xededitor.MainActivity.Data.titles;
import static com.rk.xededitor.MainActivity.Data.uris;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
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
import com.rk.xededitor.BaseActivity;
import com.rk.xededitor.BatchReplacement.BatchReplacement;
import com.rk.xededitor.Decompress;
import com.rk.xededitor.MainActivity.treeview2.HandleFileActions;
import com.rk.xededitor.MainActivity.treeview2.MA;
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter;
import com.rk.xededitor.R;
import com.rk.xededitor.Settings.SettingsActivity;
import com.rk.xededitor.Settings.SettingsData;
import com.rk.xededitor.databinding.ActivityDynamicBinding;
import com.rk.xededitor.plugin.PluginInstance;
import com.rk.xededitor.plugin.PluginServer;
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

public class MainActivity extends BaseActivity {

    final int REQUEST_FILE_SELECTION = 123;
    public ActivityDynamicBinding binding;
    public mAdapter adapter;
    NavigationView navigationView;
    private ViewPager viewPager;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private boolean isReselecting = false;
    private String SearchText = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDynamicBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        activity = this;

        //use thread
        SettingsData.applyPrefs(this);

        fileList = new ArrayList<>();
        Toolbar toolbar = binding.toolbar;

        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);


        if (!SettingsData.isDarkMode(this)) {
            //light mode
            getWindow().setNavigationBarColor(Color.parseColor("#FEF7FF"));
            View decorView = getWindow().getDecorView();
            int flags = decorView.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decorView.setSystemUiVisibility(flags);
        }
        if (SettingsData.isDarkMode(this) && SettingsData.isOled(this)) {
            binding.drawerLayout.setBackgroundColor(Color.BLACK);
            binding.navView.setBackgroundColor(Color.BLACK);
            binding.main.setBackgroundColor(Color.BLACK);
            binding.appbar.setBackgroundColor(Color.BLACK);
            binding.toolbar.setBackgroundColor(Color.BLACK);
            binding.tabs.setBackgroundColor(Color.BLACK);
            binding.mainView.setBackgroundColor(Color.BLACK);
            Window window = getWindow();
            window.setNavigationBarColor(Color.BLACK);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        }


        initViews();


        //todo use shared prefs instead of files
        if (!new File(getExternalFilesDir(null) + "/unzip").exists()) {
            new Thread(() -> {
                try {
                    Decompress.unzipFromAssets(this, "files.zip", getExternalFilesDir(null) + "/unzip");
                    new File(getExternalFilesDir(null) + "files").delete();
                    new File(getExternalFilesDir(null) + "files.zip").delete();
                    new File(getExternalFilesDir(null) + "textmate").delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }


        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                boolean shouldExit = true;
                if (fragments != null) {
                    for (Fragment fragment : fragments) {
                        DynamicFragment fragment1 = (DynamicFragment) fragment;
                        if (fragment1.isModified) {
                            shouldExit = false;
                            new MaterialAlertDialogBuilder(MainActivity.this).setTitle("Unsaved Files").setMessage("You have unsaved files!").setNegativeButton("Cancel", null).setNeutralButton("Save & Exit", (dialog, which) -> {
                                onOptionsItemSelected(menu.findItem(R.id.action_all));
                                finish();
                            }).setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    finish();
                                }
                            }).show();
                        }
                        break;
                    }
                }
                if (shouldExit) {
                    finish();
                }
            }
        });


        String UriString = SettingsData.getSetting(this, "lastOpenedUri", "null");
        if (!UriString.equals("null")) {
            Uri uri = Uri.parse(UriString);
            if (hasUriPermission(uri)) {
                rootFolder = DocumentFile.fromTreeUri(this, uri);
                //binding.tabs.setVisibility(View.VISIBLE);
                binding.mainView.setVisibility(View.VISIBLE);
                binding.safbuttons.setVisibility(View.GONE);
                binding.maindrawer.setVisibility(View.VISIBLE);
                binding.drawerToolbar.setVisibility(View.VISIBLE);
                //use new file browser
                new MA(this, rootFolder);

                String name = rootFolder.getName();
                assert name != null;
                if (name.length() > 18) {
                    name = rootFolder.getName().substring(0, 15) + "...";
                }

                binding.rootDirLabel.setText(name);
            }

        }






    }

    public boolean hasUriPermission(Uri uri) {
        if (uri == null) return false;

        // Check if we have persisted permissions for this URI
        List<UriPermission> persistedPermissions = getContentResolver().getPersistedUriPermissions();
        boolean hasPersistedPermission = persistedPermissions.stream().anyMatch(p -> p.getUri().equals(uri));

        return hasPersistedPermission;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Handle the theme change here
        rkUtils.toast("Restart Required");
    }

    private void initViews() {

        // initialise the layout
        viewPager = binding.viewpager;
        mTabLayout = binding.tabs;
        viewPager.setOffscreenPageLimit(15);

        mTabLayout.setupWithViewPager(viewPager);

        // viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mTabLayout));

        mTabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
                fragments.get(mTabLayout.getSelectedTabPosition()).updateUndoRedo();
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

                        //wtf this loop do ?
                        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
                            TabLayout.Tab tab = mTabLayout.getTabAt(i);
                            if (tab != null) {
                                String name = titles.get(i);
                                if (name != null) {
                                    tab.setText(name);
                                }
                            }
                        }
                        if (mTabLayout.getTabCount() < 1) {
                            binding.tabs.setVisibility(View.GONE);
                            binding.mainView.setVisibility(View.GONE);
                            binding.openBtn.setVisibility(View.VISIBLE);

                        }
                        boolean visible = (!(fragments == null || fragments.isEmpty()));
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
            binding.tabs.setVisibility(View.VISIBLE);
            binding.mainView.setVisibility(View.VISIBLE);
            binding.openBtn.setVisibility(View.GONE);
            newEditor(DocumentFile.fromSingleUri(this, selectedFileUri));
        } else if (requestCode == REQUEST_DIRECTORY_SELECTION && resultCode == RESULT_OK && data != null) {
            //binding.tabs.setVisibility(View.VISIBLE);
            binding.mainView.setVisibility(View.VISIBLE);
            binding.safbuttons.setVisibility(View.GONE);
            binding.maindrawer.setVisibility(View.VISIBLE);
            binding.drawerToolbar.setVisibility(View.VISIBLE);

            Uri treeUri = data.getData();
            persistUriPermission(treeUri);
            rootFolder = DocumentFile.fromTreeUri(this, treeUri);

            //use new file browser
            new MA(this, rootFolder);

            String name = rootFolder.getName();
            if (name.length() > 18) {
                name = rootFolder.getName().substring(0, 15) + "...";
            }

            binding.rootDirLabel.setText(name);

        } else if (requestCode == HandleFileActions.getREQUEST_CODE_OPEN_DIRECTORY() && resultCode == RESULT_OK) {
            Uri directoryUri = data.getData();

            if (directoryUri != null) {
                // Save a file in the selected directory
                HandleFileActions.saveFile(this, directoryUri);
            } else {
                Toast.makeText(this, "No directory selected", Toast.LENGTH_SHORT).show();
            }

        }
    }

    /*this method is called when user opens a file
    unlike newEditor which is called when opening a directory via file manager*/
    public void onNewEditor() {
        binding.openBtn.setVisibility(View.GONE);
        binding.tabs.setVisibility(View.VISIBLE);
        binding.mainView.setVisibility(View.VISIBLE);
        final boolean visible = !(fragments == null || fragments.isEmpty());
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
        if (fragments.size() <= 1) {
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            undo.setVisible(false);
            redo.setVisible(false);

        }

    }

    public void newEditor(DocumentFile file) {

        if (adapter == null) {
            fragments = new ArrayList<>();
            titles = new ArrayList<>();
            uris = new ArrayList<>();
            adapter = new mAdapter(getSupportFragmentManager());
            viewPager.setAdapter(adapter);
        }


        final String file_name = file.getName();
        if (fileList.contains(file) || adapter.addFragment(new DynamicFragment(file, activity), file_name, file)) {
            rkUtils.toast("File already opened!");
            return;
        }

        fileList.add(file);

        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            if (tab != null) {
                String name = titles.get(tab.getPosition());
                if (name != null) {
                    tab.setText(name);
                }
            }
        }
        final boolean visible = !(fragments == null || fragments.isEmpty());

        menu.findItem(R.id.batchrep).setVisible(visible);
        menu.findItem(R.id.search).setVisible(visible);
        menu.findItem(R.id.action_save).setVisible(visible);
        menu.findItem(R.id.action_all).setVisible(visible);
        menu.findItem(R.id.batchrep).setVisible(visible);
        menu.findItem(R.id.search).setVisible(visible);

    }


    @Override
    protected void onDestroy() {
        Data.clear();
        super.onDestroy();
    }

    public void openFile(View v) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // you can specify mime types here to filter certain types of files
        startActivityForResult(intent, REQUEST_FILE_SELECTION);
    }

    private void persistUriPermission(Uri uri) {
        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        // Check if URI permission is already granted
        if ((getContentResolver().getPersistedUriPermissions().stream().noneMatch(p -> p.getUri().equals(uri)))) {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }
        SettingsData.setSetting(this, "lastOpenedUri", uri.toString());

    }

    public void revokeUriPermission(Uri uri) {
        final int releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        getContentResolver().releasePersistableUriPermission(uri, releaseFlags);


    }

    public void openDir(View v) {
        TreeViewAdapter.stopThread();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_DIRECTORY_SELECTION);
    }

    public void reselctDir(View v) {
        isReselecting = true;
        String uriStr = SettingsData.getSetting(this, "lastOpenedUri", "null");
        if (!uriStr.isEmpty() && !uriStr.equals("null")) {
            revokeUriPermission(Uri.parse(uriStr));
            SettingsData.setSetting(this, "lastOpenedUri", "null");
        }

        openDir(null);
    }

    public void fileOptions(View v) {

        new HandleFileActions(this, rootFolder, rootFolder, v);
    }

    public void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public void openDrawer(View v) {
        drawerLayout.open();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        Data.menu = menu;
        menu.findItem(R.id.search).setVisible(!(fragments == null || fragments.isEmpty()));
        menu.findItem(R.id.batchrep).setVisible(!(fragments == null || fragments.isEmpty()));

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


            final int index = mTabLayout.getSelectedTabPosition();
            DynamicFragment fg = fragments.get(index);

            TabLayout.Tab tab = mTabLayout.getTabAt(mTabLayout.getSelectedTabPosition());
            assert tab != null;
            String name = tab.getText().toString();
            if (name.charAt(name.length() - 1) == '*') {
                fg.isModified = false;
                tab.setText(name.substring(0, name.length() - 1));
            }

            new Thread(() -> {
                Content content = contents.get(mTabLayout.getSelectedTabPosition());
                OutputStream outputStream = null;
                try {
                    outputStream = getContentResolver().openOutputStream(fileList.get(index).getUri(), "wt");
                    ContentIO.writeTo(content, outputStream, true);
                    outputStream.close();
                    activity.runOnUiThread(() -> {
                        rkUtils.toast("saved!");
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    activity.runOnUiThread(() -> {
                        rkUtils.toast("error \n " + e.getMessage());
                    });
                }

            }).start();

            //Content content = fg.editor.getText();


            return true;
        } else if (id == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_all) {

            for (int i = 0; i < mTabLayout.getTabCount(); i++) {
                TabLayout.Tab mtab = mTabLayout.getTabAt(i);


                final int index = mtab.getPosition();
                DynamicFragment fg = fragments.get(index);

                String name = mtab.getText().toString();
                if (name.charAt(name.length() - 1) == '*') {
                    fg.isModified = false;
                    mtab.setText(name.substring(0, name.length() - 1));
                }

                new Thread(() -> {
                    OutputStream outputStream = null;
                    Content content = contents.get(index);
                    try {
                        outputStream = getContentResolver().openOutputStream(fileList.get(index).getUri(), "wt");
                        ContentIO.writeTo(content, outputStream, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();


            }
            rkUtils.toast("saved all");
            return true;
        } else if (id == R.id.search) {

            View popuop_view = LayoutInflater.from(this).inflate(R.layout.popup_search, null);
            TextView searchBox = popuop_view.findViewById(R.id.searchbox);
            if (!SearchText.isEmpty()) {
                searchBox.setText(SearchText);
            }

            new MaterialAlertDialogBuilder(this).setTitle("Search").setView(popuop_view).setNegativeButton("Cancel", null).setPositiveButton("Search", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    MenuItem undo = menu.findItem(R.id.undo);
                    MenuItem redo = menu.findItem(R.id.redo);
                    undo.setVisible(false);
                    redo.setVisible(false);
                    CodeEditor editor = fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
                    CheckBox checkBox = popuop_view.findViewById(R.id.case_senstive);
                    SearchText = searchBox.getText().toString();
                    editor.getSearcher().search(SearchText, new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, !checkBox.isChecked()));
                    menu.findItem(R.id.search_next).setVisible(true);
                    menu.findItem(R.id.search_previous).setVisible(true);
                    menu.findItem(R.id.search_close).setVisible(true);
                    menu.findItem(R.id.replace).setVisible(true);

                }
            }).show();

            return true;
        } else if (id == R.id.search_next) {
            CodeEditor editor = fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
            editor.getSearcher().gotoPrevious();

            return true;
        } else if (id == R.id.search_previous) {
            CodeEditor editor = fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
            editor.getSearcher().gotoNext();

            return true;
        } else if (id == R.id.search_close) {
            CodeEditor editor = fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
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
            new MaterialAlertDialogBuilder(this).setTitle("Replace").setView(popuop_view).setNegativeButton("Cancel", null).setPositiveButton("Replace All", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    fragments.get(mTabLayout.getSelectedTabPosition()).getEditor().getSearcher().replaceAll(((TextView) popuop_view.findViewById(R.id.replace_replacement)).getText().toString());
                }
            }).show();

        } else if (id == R.id.batchrep) {
            Intent intent = new Intent(this, BatchReplacement.class);
            startActivity(intent);
        } else if (id == R.id.undo) {
            fragments.get(mTabLayout.getSelectedTabPosition()).Undo();
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            CodeEditor editor = fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
            redo.setEnabled(editor.canRedo());
            undo.setEnabled(editor.canUndo());
        } else if (id == R.id.redo) {
            fragments.get(mTabLayout.getSelectedTabPosition()).Redo();
            MenuItem undo = menu.findItem(R.id.undo);
            MenuItem redo = menu.findItem(R.id.redo);
            CodeEditor editor = fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
            redo.setEnabled(editor.canRedo());
            undo.setEnabled(editor.canUndo());
        }
        return super.onOptionsItemSelected(item);
    }
}


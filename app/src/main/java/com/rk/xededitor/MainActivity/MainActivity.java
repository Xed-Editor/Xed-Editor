package com.rk.xededitor.MainActivity;

import static com.rk.xededitor.MainActivity.Data.REQUEST_DIRECTORY_SELECTION;
import static com.rk.xededitor.MainActivity.Data.fileList;
import static com.rk.xededitor.MainActivity.Data.fragments;
import static com.rk.xededitor.MainActivity.Data.mTabLayout;
import static com.rk.xededitor.MainActivity.Data.menu;
import static com.rk.xededitor.MainActivity.Data.rootFolder;
import static com.rk.xededitor.MainActivity.Data.titles;
import static com.rk.xededitor.MainActivity.Data.uris;

import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.GravityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.After;
import com.rk.xededitor.BaseActivity;
import com.rk.xededitor.MainActivity.treeview2.HandleFileActions;
import com.rk.xededitor.MainActivity.treeview2.MA;
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter;
import com.rk.xededitor.R;
import com.rk.xededitor.Settings.SettingsData;
import com.rk.xededitor.databinding.ActivityMainBinding;
import com.rk.xededitor.rkUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.widget.CodeEditor;

public class MainActivity extends BaseActivity {
  
  final int REQUEST_FILE_SELECTION = 123;
  public ActivityMainBinding binding;
  public mAdapter adapter;
  NavigationView navigationView;
  public ViewPager viewPager;
  private DrawerLayout drawerLayout;
  private ActionBarDrawerToggle drawerToggle;
  private boolean isReselecting = false;
  
 
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    
    
    
    setSupportActionBar(binding.toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
    
    drawerLayout = binding.drawerLayout;
    navigationView = binding.navView;
    drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
    drawerLayout.addDrawerListener(drawerToggle);
    drawerToggle.syncState();
    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    
    
    new After(4000, () -> MainActivity.this.runOnUiThread(() -> {
      getOnBackPressedDispatcher().addCallback(MainActivity.this, new OnBackPressedCallback(true) {
        
        @Override
        public void handleOnBackPressed() {
          boolean shouldExit = true;
          if (fragments != null) {
            for (DynamicFragment fragment : fragments) {
              if (fragment.isModified) {
                shouldExit = false;
                new MaterialAlertDialogBuilder(MainActivity.this).setTitle("Unsaved Files").setMessage("You have unsaved files!").setNegativeButton("Cancel", null).setNeutralButton("Save & Exit", (dialog, which) -> {
                  onOptionsItemSelected(menu.findItem(R.id.action_all));
                  finish();
                }).setPositiveButton("Exit", (dialogInterface, i) -> finish()).show();
              }
              break;
            }
          }
          if (shouldExit) {
            finish();
          }
        }
      });
    }));
    
    viewPager = binding.viewpager;
    mTabLayout = binding.tabs;
    viewPager.setOffscreenPageLimit(15);
    mTabLayout.setupWithViewPager(viewPager);
    
    
   
    
    //run async init
    new Init(this);
  }
  
  public CodeEditor getCurrentEditor(){
    return fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
  }
  
  public boolean hasUriPermission(Uri uri) {
    if (uri == null) return false;
    List<UriPermission> persistedPermissions = getContentResolver().getPersistedUriPermissions();
    boolean hasPersistedPermission = persistedPermissions.stream().anyMatch(p -> p.getUri().equals(uri));
    return hasPersistedPermission;
  }
  
  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    // Handle the theme change here
    rkUtils.toast(this,"Restart Required");
  }
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_FILE_SELECTION && resultCode == RESULT_OK && data != null) {
      Uri selectedFileUri = data.getData();
      binding.tabs.setVisibility(View.VISIBLE);
      binding.mainView.setVisibility(View.VISIBLE);
      binding.openBtn.setVisibility(View.GONE);
      newEditor(DocumentFile.fromSingleUri(this, selectedFileUri), false);
    } else if (requestCode == REQUEST_DIRECTORY_SELECTION && resultCode == RESULT_OK && data != null) {
      //binding.tabs.setVisibility(View.VISIBLE);
      binding.mainView.setVisibility(View.VISIBLE);
      binding.safbuttons.setVisibility(View.GONE);
      binding.maindrawer.setVisibility(View.VISIBLE);
      binding.drawerToolbar.setVisibility(View.VISIBLE);
      
      Uri treeUri = data.getData();
      persistUriPermission(treeUri);
      rootFolder = DocumentFile.fromTreeUri(this, treeUri);
      
      new MA(MainActivity.this, rootFolder);
      
      //use new file browser
      
      
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
      
    } else if (requestCode == Data.REQUEST_CODE_CREATE_FILE && resultCode == RESULT_OK) {
      if (data != null) {
        Uri uri = data.getData();
        if (uri != null) {
          new Thread(() -> {
            File cacheFile = new File(getExternalCacheDir(), "newfile.txt");
            try (InputStream inputStream = new FileInputStream(cacheFile); OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt")) {
              byte[] buffer = new byte[1024];
              int length;
              while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
              }
              cacheFile.delete();
            } catch (Exception e) {
              e.printStackTrace();
            }
            
          }).start();
        }
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
    menu.findItem(R.id.action_print).setVisible(visible);
    menu.findItem(R.id.action_all).setVisible(visible);
    menu.findItem(R.id.batchrep).setVisible(visible);
    menu.findItem(R.id.share).setVisible(visible);
    MenuItem undo = menu.findItem(R.id.undo);
    MenuItem redo = menu.findItem(R.id.redo);
    undo.setVisible(visible);
    redo.setVisible(visible);
    
  }
  
  
  
  public void newEditor(DocumentFile file, boolean isNewFile) {
    
    if (adapter == null) {
      fragments = new ArrayList<>();
      titles = new ArrayList<>();
      uris = new ArrayList<>();
      adapter = new mAdapter(getSupportFragmentManager());
      viewPager.setAdapter(adapter);
    }
    
    
    final String file_name = file.getName();
    
    if (fileList.contains(file) || adapter.addFragment(new DynamicFragment(file, this, isNewFile), file_name, file)) {
      rkUtils.toast(this,"File already opened!");
      
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
    
    //viewPager.setCurrentItem(mTabLayout.getTabCount(),false);
    final boolean visible = !(fragments == null || fragments.isEmpty());
    
    menu.findItem(R.id.batchrep).setVisible(visible);
    menu.findItem(R.id.search).setVisible(visible);
    menu.findItem(R.id.action_save).setVisible(visible);
    menu.findItem(R.id.action_print).setVisible(visible);
    menu.findItem(R.id.action_all).setVisible(visible);
    menu.findItem(R.id.batchrep).setVisible(visible);
    menu.findItem(R.id.search).setVisible(visible);
    menu.findItem(R.id.share).setVisible(visible);
    
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
   new HandleFileActions(MainActivity.this, rootFolder, rootFolder, v);
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
  
  public void newFile(View v) {
    newEditor(DocumentFile.fromFile(new File(getExternalCacheDir(), "newfile.txt")), true);
    onNewEditor();
    new After(500, () -> drawerLayout.close());
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
    
    
    //this is used to open and close the drawer
    if (id == android.R.id.home) {
      if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
        drawerLayout.closeDrawer(GravityCompat.START);
      } else {
        drawerLayout.openDrawer(GravityCompat.START);
      }
      return true;
    } else {
      if (drawerToggle.onOptionsItemSelected(item)) {
        return true;
      }
      return HandleMenuClick.handle(this,item);
    }
  }
}


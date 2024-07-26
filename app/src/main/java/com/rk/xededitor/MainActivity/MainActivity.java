package com.rk.xededitor.MainActivity;

import static android.content.res.Resources.getSystem;
import static com.rk.xededitor.MainActivity.StaticData.REQUEST_DIRECTORY_SELECTION;
import static com.rk.xededitor.MainActivity.StaticData.fragments;
import static com.rk.xededitor.MainActivity.StaticData.mTabLayout;
import static com.rk.xededitor.MainActivity.StaticData.menu;
import static com.rk.xededitor.MainActivity.StaticData.rootFolder;
import static com.rk.xededitor.rkUtils.dpToPx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.BaseActivity;
import com.rk.xededitor.FileClipboard;
import com.rk.xededitor.MainActivity.treeview2.HandleFileActions;
import com.rk.xededitor.MainActivity.treeview2.TreeView;
import com.rk.xededitor.MainActivity.treeview2.TreeViewAdapter;
import com.rk.xededitor.R;
import com.rk.xededitor.Settings.SettingsData;
import com.rk.xededitor.databinding.ActivityMainBinding;
import com.rk.xededitor.rkUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.rosemoe.sora.widget.CodeEditor;

public class MainActivity extends BaseActivity {
  
  private static final int REQUEST_CODE_STORAGE_PERMISSIONS = 38386;
  public static MainActivity activity;
  final int REQUEST_FILE_SELECTION = 123;
  private final int REQUEST_CODE_MANAGE_EXTERNAL_STORAGE = 36169;
  public ActivityMainBinding binding;
  public mAdapter adapter;
  public ViewPager viewPager;
  public DrawerLayout drawerLayout;
  NavigationView navigationView;
  private ActionBarDrawerToggle drawerToggle;
  private boolean isReselecting = false;
  
  public static void updateMenuItems() {
    final boolean visible = !(fragments == null || fragments.isEmpty());
    menu.findItem(R.id.batchrep).setVisible(visible);
    menu.findItem(R.id.search).setVisible(visible);
    menu.findItem(R.id.action_save).setVisible(visible);
    menu.findItem(R.id.action_print).setVisible(visible);
    menu.findItem(R.id.action_all).setVisible(visible);
    menu.findItem(R.id.batchrep).setVisible(visible);
    menu.findItem(R.id.search).setVisible(visible);
    menu.findItem(R.id.share).setVisible(visible);
    menu.findItem(R.id.undo).setVisible(visible);
    menu.findItem(R.id.redo).setVisible(visible);
    menu.findItem(R.id.insertdate).setVisible(visible);
    if (visible && SettingsData.getBoolean(MainActivity.activity, "show_arrows", false)) {
      MainActivity.activity.binding.divider.setVisibility(View.VISIBLE);
      MainActivity.activity.binding.mainBottomBar.setVisibility(View.VISIBLE);
      var vp =MainActivity.activity.binding.viewpager;
      RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) vp.getLayoutParams();
      layoutParams.bottomMargin = dpToPx(40,MainActivity.activity);  // Convert dp to pixels as needed
      vp.setLayoutParams(layoutParams);
    } else {
      MainActivity.activity.binding.divider.setVisibility(View.GONE);
      MainActivity.activity.binding.mainBottomBar.setVisibility(View.GONE);
      var vp =MainActivity.activity.binding.viewpager;
      RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) vp.getLayoutParams();
      layoutParams.bottomMargin = dpToPx(0,MainActivity.activity);  // Convert dp to pixels as needed
      vp.setLayoutParams(layoutParams);
    }
  }
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    StaticData.clear();
    super.onCreate(savedInstanceState);
    activity = this;
    
    
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    
    //new After(1000, () -> new SFTPClient());
    
    setSupportActionBar(binding.toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowTitleEnabled(false);
    
    drawerLayout = binding.drawerLayout;
    navigationView = binding.navView;
    navigationView.getLayoutParams().width = (int) (getSystem().getDisplayMetrics().widthPixels * 0.87);
    
    
    drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.open_drawer, R.string.close_drawer);
    drawerLayout.addDrawerListener(drawerToggle);
    drawerToggle.syncState();
    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    
    new PrepareRecyclerView(this);
    
    verifyStoragePermission();
    
    //run async init
    new Init(this);
    
    
    viewPager = binding.viewpager;
    mTabLayout = binding.tabs;
    viewPager.setOffscreenPageLimit(15);
    mTabLayout.setupWithViewPager(viewPager);
    
    
  }
  
  public void verifyStoragePermission() {
    var shouldAsk = false;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (!Environment.isExternalStorageManager()) {
        shouldAsk = true;
      }
    } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      shouldAsk = true;
    }
    
    if (shouldAsk) {
      new MaterialAlertDialogBuilder(this).setTitle("Manage Storage").setMessage("App needs access to edit files in your storage. Please allow the access in the upcoming system setting.").setNegativeButton("Exit App", (dialog, which) -> {
        finishAffinity();
      }).setPositiveButton("OK", (dialog, which) -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
          intent.setData(Uri.parse("package:" + getPackageName()));
          startActivityForResult(intent, REQUEST_CODE_MANAGE_EXTERNAL_STORAGE);
        } else {
          //below 11
          // Request permissions
          String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
          ActivityCompat.requestPermissions(this, perms, REQUEST_CODE_STORAGE_PERMISSIONS);
          
        }
      }).setCancelable(false).show();
    }
    
  }
  
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    //check permission for old devices
    if (requestCode == REQUEST_CODE_STORAGE_PERMISSIONS) {
      if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
        // permssion denied
        verifyStoragePermission();
      }
    }
  }
  
  public CodeEditor getCurrentEditor() {
    return fragments.get(mTabLayout.getSelectedTabPosition()).getEditor();
  }
  
  public boolean hasUriPermission(Uri uri) {
    if (uri == null) return false;
    List<UriPermission> persistedPermissions = getContentResolver().getPersistedUriPermissions();
    boolean hasPersistedPermission = persistedPermissions.stream().anyMatch(p -> p.getUri().equals(uri));
    return hasPersistedPermission;
  }
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_MANAGE_EXTERNAL_STORAGE) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (!Environment.isExternalStorageManager()) {
          // Permission still not granted
          verifyStoragePermission();
        }
      }
    } else if (requestCode == REQUEST_FILE_SELECTION && resultCode == RESULT_OK && data != null) {
      Uri selectedFileUri = data.getData();
      assert selectedFileUri != null;
      String path = Objects.requireNonNull(selectedFileUri.getPath()).replace("/document/primary:", "/storage/emulated/0/");
      File file = new File(path);
      
      binding.tabs.setVisibility(View.VISIBLE);
      binding.mainView.setVisibility(View.VISIBLE);
      binding.openBtn.setVisibility(View.GONE);
      newEditor(file, false);
    } else if (requestCode == REQUEST_DIRECTORY_SELECTION && resultCode == RESULT_OK && data != null) {
      binding.mainView.setVisibility(View.VISIBLE);
      binding.safbuttons.setVisibility(View.GONE);
      binding.maindrawer.setVisibility(View.VISIBLE);
      binding.drawerToolbar.setVisibility(View.VISIBLE);
      
      Uri treeUri = data.getData();
      persistUriPermission(treeUri);
      
      String path = treeUri.getPath().replace("/tree/primary:", "/storage/emulated/0/");
      File file = new File(path);
      
      rootFolder = file;
      
      new TreeView(MainActivity.this, file);
      
      //use new file browser
      String name = rootFolder.getName();
      if (name.length() > 18) {
        name = rootFolder.getName().substring(0, 15) + "...";
      }
      
      binding.rootDirLabel.setText(name);
    } else if (requestCode == HandleFileActions.REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
      Uri directoryUri = data.getData();
      
      if (directoryUri != null) {
        // Save a file in the selected directory
        String path = directoryUri.getPath().replace("/tree/primary:", "/storage/emulated/0/");
        File directory = new File(path);
        
        if (directory.isDirectory()) {
          // Ensure the directory exists
          if (!directory.exists()) {
            directory.mkdirs();
          }
          
          // Create a new file within the directory
          File newFile = new File(directory, HandleFileActions.Companion.getTo_save_file().getName());
          
          try {
            // Copy the file to the new file path within the directory
            Files.copy(HandleFileActions.Companion.getTo_save_file().toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            // Optionally, clear the clipboard after copying
            FileClipboard.clear();
            
          } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to save file: " + e.getMessage());
          }
        } else {
          throw new RuntimeException("Selected path is not a directory");
        }
        
        
      } else {
        Toast.makeText(this, "No directory selected", Toast.LENGTH_SHORT).show();
      }
      
    } else if (requestCode == StaticData.REQUEST_CODE_CREATE_FILE && resultCode == RESULT_OK) {
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
  
  public void onNewEditor() {
    binding.openBtn.setVisibility(View.GONE);
    binding.tabs.setVisibility(View.VISIBLE);
    binding.mainView.setVisibility(View.VISIBLE);
    updateMenuItems();
  }
  
  public void newEditor(File file, boolean isNewFile) {
    newEditor(file, isNewFile, null);
  }
  
  public void newEditor(File file, boolean isNewFile, String text) {
    if (adapter == null) {
      fragments = new ArrayList<>();
      adapter = new mAdapter(getSupportFragmentManager());
      viewPager.setAdapter(adapter);
    }
    
    for (DynamicFragment f : fragments) {
      if (f.getFile().equals(file)) {
        rkUtils.toast(this, "File already opened!");
        return;
      }
    }
    
    
    var dynamicfragment = new DynamicFragment(file, this);
    if (text != null) {
      dynamicfragment.editor.setText(text);
    }
    adapter.addFragment(dynamicfragment, file);
    
    for (int i = 0; i < mTabLayout.getTabCount(); i++) {
      TabLayout.Tab tab = mTabLayout.getTabAt(i);
      if (tab != null) {
        String name = fragments.get(tab.getPosition()).getFileName();
        if (name != null) {
          tab.setText(name);
        }
      }
    }
    
    updateMenuItems();
  }
  
  @Override
  protected void onDestroy() {
    StaticData.clear();
    activity = null;
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
  
  
  public void openDrawer(View v) {
    drawerLayout.open();
  }
  
  public void open_from_path(View v) {
    var popupView = LayoutInflater.from(this).inflate(R.layout.popup_new, null);
    var editText = (EditText) popupView.findViewById(R.id.name);
    
    editText.setText("/storage/emulated/0/");
    editText.setHint("file or folder path");
    
    new MaterialAlertDialogBuilder(this).setView(popupView).setTitle("Path").setNegativeButton(getString(R.string.cancel), null).setPositiveButton("Open", new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String path = editText.getText().toString();
        if (path.isEmpty()) {
          rkUtils.toast(MainActivity.this, "Please enter a path");
          return;
        }
        File file = new File(path);
        if (!file.exists()) {
          rkUtils.toast(MainActivity.this, "Path does not exist");
          return;
        }
        
        if (!file.canRead() && file.canWrite()) {
          rkUtils.toast(MainActivity.this, "Permission Denied");
        }
        
        if (file.isDirectory()) {
          binding.mainView.setVisibility(View.VISIBLE);
          binding.safbuttons.setVisibility(View.GONE);
          binding.maindrawer.setVisibility(View.VISIBLE);
          binding.drawerToolbar.setVisibility(View.VISIBLE);
          
          rootFolder = file;
          
          new TreeView(MainActivity.this, file);
          
          //use new file browser
          String name = rootFolder.getName();
          if (name.length() > 18) {
            name = rootFolder.getName().substring(0, 15) + "...";
          }
          
          binding.rootDirLabel.setText(name);
        } else {
          newEditor(file, false);
        }
      }
    }).show();
    
  }
  
  public void privateDir(View v) {
    binding.mainView.setVisibility(View.VISIBLE);
    binding.safbuttons.setVisibility(View.GONE);
    binding.maindrawer.setVisibility(View.VISIBLE);
    binding.drawerToolbar.setVisibility(View.VISIBLE);
    
    File file = getFilesDir().getParentFile();
    
    rootFolder = file;
    
    new TreeView(MainActivity.this, file);
    
    //use new file browser
    String name = rootFolder.getName();
    if (name.length() > 18) {
      name = rootFolder.getName().substring(0, 15) + "...";
    }
    
    binding.rootDirLabel.setText(name);
  }
  
  @SuppressLint("RestrictedApi")
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    StaticData.menu = menu;
    
    if (menu instanceof MenuBuilder m) {
      //noinspection RestrictedApi
      m.setOptionalIconsVisible(true);
    }
    
    menu.findItem(R.id.search).setVisible(!(fragments == null || fragments.isEmpty()));
    menu.findItem(R.id.batchrep).setVisible(!(fragments == null || fragments.isEmpty()));
    /*
    data.setLooping(true);
    var files = data.getOpenedFiles();
    var map = data.getNewFileMap();
    for(int i=0;i<files.size();i++){
      DocumentFile file = files.get(i);
      Boolean isNewFile = map.get(file);
      if (isNewFile == null){
        isNewFile = false;
      }
      newEditor(file,isNewFile);
      onNewEditor();
    }
    
    
    data.setLooping(false);
    updateMenuItems();
    
    */
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
    } else {
      if (drawerToggle.onOptionsItemSelected(item)) {
        return true;
      }
      return MenuClickHandler.Companion.handle(this, item);
    }
  }
}

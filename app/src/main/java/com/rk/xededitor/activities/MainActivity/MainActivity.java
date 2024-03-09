package com.rk.xededitor.activities.MainActivity;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.*;
import android.os.*;
import androidx.annotation.NonNull;
import android.provider.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.*;
import androidx.navigation.ui.AppBarConfiguration;
import com.google.android.material.tabs.TabLayout;

import com.rk.xededitor.*;
import com.rk.xededitor.R;
import com.rk.xededitor.activities.settings.Settings;
import com.rk.xededitor.databinding.ActivityMainBinding;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.lang.*;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

  private AppBarConfiguration mAppBarConfiguration;
  public static ActivityMainBinding binding;
  private final int REQUEST_CODE_PICK_FOLDER = 123;
  // public static CodeEditor editor;
  private TabLayout tablayout;
  private TreeNode root;
  private AndroidTreeView tView;
  private static FragmentManager manager;
  private Context ctx;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    setSupportActionBar(binding.appBarMain.toolbar);
    tablayout = binding.editorTabLayout;
    mAppBarConfiguration =
        new AppBarConfiguration.Builder(R.id.nav_home)
            .setOpenableLayout(binding.drawerLayout)
            .build();
    getSupportActionBar().setDisplayShowTitleEnabled(false);
    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    manager = getSupportFragmentManager();
    if (!rkUtils.isDarkMode(this)) {
      light_statusbar();
    }
    ctx = this;

    // EasyWindow.with(this).setHeight(750).setWidth(450).setTitle("yo").show();

    if (!new File(rkUtils.getPublicDirectory()+"/files").exists()) {
      try {
        rkUtils.copyFileFromAssetsToInternalStorage(this, "files.zip", "files.zip");
        rkUtils.unzip(rkUtils.getPublicDirectory()+"/files.zip",rkUtils.getPublicDirectory()+"/files");
        new File(rkUtils.getPublicDirectory()+"/files.zip").delete();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  @SuppressWarnings("deprecation")
  public void light_statusbar() {
    // status bar color
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsController insetsController = getWindow().getInsetsController();
      if (insetsController != null) {
        insetsController.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      View decorView = getWindow().getDecorView();
      decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (!(requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK)) {
      // set visibility to visible again
      if (binding.openFolder.getVisibility() == View.GONE) {
        rkUtils.setVisibility(binding.openFolder, true);
        rkUtils.setVisibility(binding.fmToolbar, false);
      }
      return;
    }
    Uri treeUri = data != null ? data.getData() : null;
    if (treeUri == null) {
      Log.e(getClass().getSimpleName(), "Uri is null");
      return;
    }
    setup_fm(treeUri);
  }

  private void setup_fm(Uri uri) {
    rkUtils.setVisibility(binding.openFolder, false);
    root = TreeNode.root();
    DocumentFile rootFolder = DocumentFile.fromTreeUri(this, uri);
    String name = rootFolder.getName();

    if (name.length() > 13) {
      name = name.substring(0, 10) + "...";
    }
    binding.rootName.setText(name);
    name = null;
    rkUtils.looper(rootFolder, root, 0);
    tView = new AndroidTreeView(this, root);
    binding.drawbar.addView(tView.getView());
    rkUtils.setVisibility(binding.fmToolbar, true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final int id = item.getItemId();
    if (id == R.id.action_save) {
      try {
        rkUtils.toast(this, EditorManager.save_files(getApplicationContext()));
      } catch (Exception e) {
        e.printStackTrace();
      }

      return true;
    } else if (id == R.id.action_settings) {
      Intent intent = new Intent(this, Settings.class);
      startActivity(intent);
      return true;
    } else if (id == R.id.action_terminal) {
      rkUtils.ni(this);
      return true;
    } else if (id == R.id.action_plugin) {
      rkUtils.ni(this);
      return true;
    } else {
      return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.main, menu);
    return true;
  }

  public static ActivityMainBinding getBinding() {
    return binding;
  }

  public static FragmentManager getManager() {
    return manager;
  }

  // click listners
  public void run(View view) {
    rkUtils.ni(this);
  }

  public void menu(View view) {
    binding.drawerLayout.open();
  }

  public void reselect(View v) {
    List<TreeNode> nodes = new ArrayList<>();
    nodes.addAll(root.getChildren());
    for (TreeNode node : nodes) {
      tView.removeNode(node);
    }
    root.children.clear();
    tablayout.removeAllTabs();
    // rkUtils.setVisibility(editor,false);
    rkUtils.setVisibility(binding.empty, true);
    open_folder(v);
  }

  public void open_folder(View view) {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    ActivityCompat.startActivityForResult(this, intent, REQUEST_CODE_PICK_FOLDER, null);
  }

  @Override
  protected void onDestroy() {
    binding = null;
    tablayout = null;
    System.gc();
    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    rkUtils.toast(this, "Restart the app to take effect!");
  }
}

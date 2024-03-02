package com.rk.xededitor.activities.MainActivity;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.*;
import androidx.navigation.ui.AppBarConfiguration;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.Config;
import com.rk.xededitor.R;
import com.rk.xededitor.activities.settings.Settings;
import com.rk.xededitor.databinding.ActivityMainBinding;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.*;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

  private AppBarConfiguration mAppBarConfiguration;
  public static ActivityMainBinding binding;
  private static final int REQUEST_CODE_PICK_FOLDER = 123;
  private static CodeEditor editor;
  private static TabLayout tablayout;
  private TreeNode root;
  private AndroidTreeView tView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    setSupportActionBar(binding.appBarMain.toolbar);
    tablayout = binding.editorTabLayout;
    editor = binding.editor;
    

    if (rkUtils.isDarkMode(this)) {
      Xed_dark.applyTheme(this, editor);
    } else {
      Xed.applyTheme(this, editor);
    }

    mAppBarConfiguration =
        new AppBarConfiguration.Builder(R.id.nav_home)
            .setOpenableLayout(binding.drawerLayout)
            .build();
    ViewTreeObserver viewTreeObserver = binding.drawerLayout.getViewTreeObserver();
    viewTreeObserver.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            // Remove the listener to avoid multiple calls
            binding.drawerLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) binding.openFolder.getLayoutParams();
            params.setMargins(
                binding.drawerLayout.getWidth() / 50,
                rkUtils.Percentage(binding.drawerLayout.getHeight(), 69) / 2,
                0,
                0);
            binding.openFolder.setLayoutParams(params);
          }
        });
    binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
    getSupportActionBar().setDisplayShowTitleEnabled(false);

    
    //load configurations from data
    new Config(this);
    editor.setPinLineNumber(Config.Editor.pinLineNumbers);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (!(requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK)) {
      // set visibility to visible again
      if(binding.openFolder.getVisibility() == View.GONE){
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

    rkUtils.setVisibility(binding.openFolder, false);
    root = TreeNode.root();
    DocumentFile rootFolder = DocumentFile.fromTreeUri(this, treeUri);
    String name = rootFolder.getName();
    if(name.length() > 13){
      name = name.substring(0,10)+"...";
    }
    binding.rootName.setText(name);
    rkUtils.looper(rootFolder, root, 0);
    tView = new AndroidTreeView(this, root);
    binding.drawbar.addView(tView.getView());
    rkUtils.setVisibility(binding.fmToolbar, true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    final int id = item.getItemId();

    if (id == R.id.action_save) {
      EditorManager.save_files(this);
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

  @Override
  public boolean onSupportNavigateUp() {
    return super.onSupportNavigateUp();
  }

  // static getters
  public static TabLayout getTabLayout() {
    return tablayout;
  }

  public static CodeEditor getEditor() {
    return editor;
  }

  public static ActivityMainBinding getBinding() {
    return binding;
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
    open_folder(v);
  }

  public void open_folder(View view) {
    // Launch SAF intent to pick a folder
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    ActivityCompat.startActivityForResult(this, intent, REQUEST_CODE_PICK_FOLDER, null);
  }
}

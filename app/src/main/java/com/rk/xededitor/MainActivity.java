package com.rk.xededitor;

import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.rkUtils;
import com.rk.xededitor.ui.themes.*;
import android.os.*;
import android.net.*;
import android.content.*;
import android.provider.*;
import android.widget.*;
import android.app.*;
import android.view.*;
import android.widget.ExpandableListView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.*;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.navigation.NavigationView;
import com.rk.xededitor.databinding.ActivityMainBinding;
import io.github.rosemoe.sora.widget.CodeEditor;
// import com.rk.xededitor.ui.home.HomeFragment;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.widget.schemes.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.content.Context;



public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    public ActivityMainBinding binding;
    private static final int REQUEST_CODE_PICK_FOLDER = 123;
    private static CodeEditor editor;
    private static TabLayout tablayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        

        setSupportActionBar(binding.appBarMain.toolbar);

        // editor = HomeFragment.binding.editor;
        editor = findViewById(R.id.editor);
        if (rkUtils.isDarkMode(this)) {
            Xed_dark.applyTheme(this, editor);
        } else {
            Xed.applyTheme(this, editor);
        }

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        mAppBarConfiguration =
                new AppBarConfiguration.Builder(R.id.nav_home).setOpenableLayout(drawer).build();

        ViewTreeObserver viewTreeObserver = binding.drawerLayout.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Remove the listener to avoid multiple calls
                        binding.drawerLayout
                                .getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);

                        int width = binding.drawerLayout.getWidth();
                        int height = binding.drawerLayout.getHeight();

                        LinearLayout.LayoutParams params =
                                (LinearLayout.LayoutParams) binding.openFolder.getLayoutParams();
                        params.setMargins(width / 10, rkUtils.Percentage(height, 87) / 2, 0, 0);
                        binding.openFolder.setLayoutParams(params);
                    }
                });

        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        
        
        
        tablayout = binding.editorTabLayout;
        rkUtils.setVisibility(tablayout,false);
        
    }

    public void menu(View view) {

        binding.drawerLayout.open();
    }
    public static CodeEditor getEditor() {
    	return editor;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            Uri treeUri = data != null ? data.getData() : null;
            if (treeUri != null) {
                rkUtils.setVisibility(binding.openFolder, false);
                // rkUtils.setVisibility(binding.fileTreeLabel, true);
                TreeNode root = TreeNode.root();
                DocumentFile rootFolder = DocumentFile.fromTreeUri(this, treeUri);
                // Loop over files and folders
                looper(rootFolder, root, 0);
                AndroidTreeView tView = new AndroidTreeView(this, root);
                // binding.drawbar.addView(tView.getView());
                binding.drawbar.addView(tView.getView());
            } else {
                System.out.println("Error : Uri is null ------------");
            }
        }
    }

    public void run(View view) {
        rkUtils.ni(this);
    }
    public static TabLayout getTabLayout() {
    	return tablayout;
    }

    public void open_folder(View view) {
        // Launch SAF intent to pick a folder
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final int id = item.getItemId();

        if (id == R.id.action_save) {
            rkUtils.ni(this);
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

    public void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static void looper(DocumentFile rootFolder, TreeNode root, int indent) {
        if (rootFolder != null && rootFolder.isDirectory()) {
            for (DocumentFile file : rootFolder.listFiles()) {
                if (file.isDirectory()) {
                    String folderName = file.getName();
                    TreeNode thisFolder = new TreeNode(file, folderName, false, indent);
                    root.addChild(thisFolder);
                } else {
                    String fileName = file.getName();
                    root.addChild(new TreeNode(file, fileName, true, indent));
                }
            }
        }
    }
}

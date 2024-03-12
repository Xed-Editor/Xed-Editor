package com.rk.xededitor.activities.simpleeditor;

import android.app.*;
import android.content.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.*;
import android.net.Uri;
import android.os.*;
import android.os.Bundle;
import android.provider.*;
import android.provider.OpenableColumns;
import android.util.*;
import android.view.*;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.navigation.*;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.rk.xededitor.*;
import com.rk.xededitor.R;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import com.rk.xededitor.R;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.InputStream;
import org.eclipse.tm4e.core.registry.IThemeSource;
import com.rk.xededitor.activities.settings.Settings;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.lang.*;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.*;
import java.io.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import android.content.SharedPreferences.Editor;
import org.eclipse.tm4e.core.registry.IThemeSource;

@SuppressWarnings("unused")
public class SimpleEditor extends AppCompatActivity {

  private PopupMenu popupMenu;
  private CodeEditor editor;
  private Content content;
  private Uri uri;
  private Context ctx;

  @SuppressWarnings("deprecation")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    boolean isDarkMode = rkUtils.isDarkMode(this);
    SharedPreferences pref = getApplicationContext().getSharedPreferences("MyPref", 0);
    boolean isOled = pref.getBoolean("isOled", false);
      if (isDarkMode && isOled) {
      setTheme(R.style.oled);
    }
    
    
    setContentView(R.layout.layout_simple_editor);
    editor = findViewById(R.id.seditor);
    editor.setTextSize(14);

    ctx = this;

    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    AppBarLayout appbar = findViewById(R.id.appbar);

    setSupportActionBar(toolbar);
    toolbar.setNavigationOnClickListener(v -> onBackPressed());
    getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Enable back button
    getSupportActionBar().setDisplayShowTitleEnabled(true); // Hide default title

    
    if (!isDarkMode) {
      int f5 = ContextCompat.getColor(this, R.color.f5);
      appbar.setBackgroundColor(f5);
      toolbar.setBackgroundColor(f5);
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

    } else {

      if (isOled) {

        int black = ContextCompat.getColor(this, R.color.black);
        appbar.setBackgroundColor(black);
        toolbar.setBackgroundColor(black);
      } else {

        int dark = ContextCompat.getColor(this, R.color.dark);
        appbar.setBackgroundColor(dark);
        toolbar.setBackgroundColor(dark);
      }
    }

    if (!pref.getBoolean("isUnpacked", false)) {
      rkUtils.toast(this, "Extracting assets");
      File directory = getExternalFilesDir(null);
      if (!directory.exists()) {
        directory.mkdirs();
      }
      try {
        rkUtils.copyFileFromAssetsToInternalStorage(
            this, "files.zip", getExternalFilesDir(null).getAbsolutePath() + "/files.zip");
        rkUtils.unzip(
            getExternalFilesDir(null).getAbsolutePath() + "/files.zip",
            getExternalFilesDir(null).getAbsolutePath());
        new File(getExternalFilesDir(null).getAbsolutePath() + "/files.zip").delete();

        Editor editor = pref.edit();

        editor.putBoolean("isUnpacked", true);
        editor.commit();

      } catch (Exception e) {
        rkUtils.toast(this, e.toString());
        e.printStackTrace();
      }
    }

    popupMenu = new PopupMenu(this, findViewById(R.id.null_t));
    popupMenu.getMenuInflater().inflate(R.menu.simple_mode_menu, popupMenu.getMenu());

    popupMenu.setOnMenuItemClickListener(
        new PopupMenu.OnMenuItemClickListener() {

          @Override
          public boolean onMenuItemClick(MenuItem item) {
            int id = item.getItemId();

            if (id == R.id.simple_settings_item) {
              Intent intent = new Intent(ctx, Settings.class);
              startActivity(intent);
            } else if (id == R.id.plugins) {
              rkUtils.ni(ctx);
            } else if (id == R.id.search) {
              rkUtils.ni(ctx);
            }
            return true;
          }
        });

    editor.setTypefaceText(Typeface.createFromAsset(getAssets(), "JetBrainsMono-Regular.ttf"));
    ensureTextmateTheme();
    handleIntent(getIntent());
  }

  private void handleIntent(Intent intent) {
    if (intent != null
        && (Intent.ACTION_VIEW.equals(intent.getAction())
            || Intent.ACTION_EDIT.equals(intent.getAction()))) {
      uri = intent.getData();
      if (uri != null) {
        // Try to retrieve the file's display name
        String displayName = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null, null)) {
          if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0) {
              displayName = cursor.getString(nameIndex);
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

        // Proceed to use the display name as needed
        if (displayName != null) {
          /*  if (displayName.endsWith(".java")) {
            JavaLanguage java = new JavaLanguage();
            java.useTab();
            seditor.setEditorLanguage(java);
          }*/
          if (displayName.length() > 13) {
            displayName = displayName.substring(0, 10) + "...";
          }
          getSupportActionBar().setTitle(displayName);
        }

        try {
          InputStream inputStream = getContentResolver().openInputStream(uri);
          if (inputStream != null) {
            content = ContentIO.createFrom(inputStream);
            if (content != null) {
              editor.setText(content); // Ensure content.toString() is what you intend to set

            } else {
              rkUtils.toast(this, "Error: Content is null");
            }
            inputStream.close();
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void show_overflow_menu(View v) {
    if (popupMenu != null) {
      popupMenu.show();
    }
  }

  public void save(View v) {
    try {
      OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt");
      if (outputStream != null) {
        ContentIO.writeTo(content, outputStream, true);
        rkUtils.toast(this, "saved!");
      } else {
        rkUtils.toast(this, "InputStream is null");
      }
    } catch (IOException e) {
      e.printStackTrace();
      rkUtils.toast(this, "Unknown Error \n" + e.toString());
    }
  }

  private void ensureTextmateTheme() {

    var editorColorScheme = editor.getColorScheme();
    var themeRegistry = ThemeRegistry.getInstance();

    boolean darkMode = rkUtils.isDarkMode(ctx);
    try {

      if (darkMode) {
        SharedPreferences pref = ctx.getApplicationContext().getSharedPreferences("MyPref", 0);
        String path;
        if (pref.getBoolean("isOled", false)) {
          path = rkUtils.getPublicDirectory() + "/files/textmate/black/darcula.json";
          themeRegistry.loadTheme(
              new ThemeModel(
                  IThemeSource.fromInputStream(
                      FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                  "darculaX"));
        } else {
          path = rkUtils.getPublicDirectory() + "/files/textmate/darcula.json";
          themeRegistry.loadTheme(
              new ThemeModel(
                  IThemeSource.fromInputStream(
                      FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                  "darcula"));
        }

        editorColorScheme = TextMateColorScheme.create(themeRegistry);

      } else {
        String path = rkUtils.getPublicDirectory() + "/files/textmate/quietlight.json";
        themeRegistry.loadTheme(
            new ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                "quitelight"));
        editorColorScheme = TextMateColorScheme.create(themeRegistry);
      }

    } catch (Exception e) {
      e.printStackTrace();
    }

    if (darkMode) {

      SharedPreferences pref = ctx.getApplicationContext().getSharedPreferences("MyPref", 0);
      if (pref.getBoolean("isOled", false)) {
        themeRegistry.setTheme("darculaX");
      } else {
        themeRegistry.setTheme("darcula");
      }
    } else {
      themeRegistry.setTheme("quietlight");
    }

    editor.setColorScheme(editorColorScheme);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    rkUtils.toast(this, "Restart the app to take effect!");
  }

  @Override
  protected void onDestroy() {
    editor.release();
    super.onDestroy();
  }
}

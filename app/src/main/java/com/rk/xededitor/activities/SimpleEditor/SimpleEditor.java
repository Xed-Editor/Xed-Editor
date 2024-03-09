package com.rk.xededitor.activities.simpleeditor;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.provider.OpenableColumns;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.appbar.MaterialToolbar;
import com.rk.xededitor.activities.MainActivity.Xed;
import com.rk.xededitor.activities.MainActivity.Xed_dark;
import com.rk.xededitor.rkUtils;
import android.view.WindowInsetsController;
import android.view.View;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.IOException;
import java.io.InputStream;
import com.rk.xededitor.R;
import android.os.*;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import java.io.OutputStream;

@SuppressWarnings("unused")
public class SimpleEditor extends AppCompatActivity {

  private PopupMenu popupMenu;
  private CodeEditor seditor;
  private Content content;
  private Uri uri;
  private Context context;

  @SuppressWarnings("deprecation")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.layout_simple_editor);
    seditor = findViewById(R.id.seditor);
    seditor.setTextSize(14);

    context = this;
    if (!rkUtils.isDarkMode(this)) {
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
      Xed.applyTheme(this, seditor);

    } else {
      Xed_dark.applyTheme(this, seditor);
    }

    popupMenu = new PopupMenu(this, findViewById(R.id.null_t));
    popupMenu.getMenuInflater().inflate(R.menu.simple_mode_menu, popupMenu.getMenu());

    popupMenu.setOnMenuItemClickListener(
        new PopupMenu.OnMenuItemClickListener() {

          @Override
          public boolean onMenuItemClick(MenuItem item) {
            int id = item.getItemId();

            if (id == R.id.simple_settings_item) {
              rkUtils.ni(context);
            } else if (id == R.id.plugins) {
              rkUtils.ni(context);
            } else if (id == R.id.search) {
              rkUtils.ni(context);
            }
            return true;
          }
        });

    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    toolbar.setNavigationOnClickListener(v -> onBackPressed());
    getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Enable back button
    getSupportActionBar().setDisplayShowTitleEnabled(true); // Hide default title
    seditor.setTypefaceText(Typeface.createFromAsset(getAssets(), "JetBrainsMono-Regular.ttf"));
    handleIntent(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    handleIntent(intent);
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
              seditor.setText(content); // Ensure content.toString() is what you intend to set

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
}

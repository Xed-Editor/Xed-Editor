package com.rk.xededitor.MainActivity;

import static com.rk.xededitor.MainActivity.StaticData.contents;
import static com.rk.xededitor.MainActivity.StaticData.fileList;
import static com.rk.xededitor.MainActivity.StaticData.fragments;
import static com.rk.xededitor.MainActivity.StaticData.mTabLayout;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.BaseActivityKt;
import com.rk.xededitor.R;
import com.rk.xededitor.Settings.SettingsData;
import com.rk.xededitor.rkUtils;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;


public class DynamicFragment extends Fragment {
  
  public final String fileName;
  private final DocumentFile file;
  private final Context ctx;
  public CodeEditor editor;
  public boolean isModified = false;
  MenuItem undo;
  MenuItem redo;
  public boolean isNewFile;
  
  public DynamicFragment(DocumentFile file, Context ctx, boolean isNewFile) {
    this.isNewFile = isNewFile;
    fileName = file.getName();
    this.ctx = ctx;
    this.file = file;
    editor = new CodeEditor(ctx);
    if (SettingsData.isDarkMode(ctx)) {
      ensureTextmateTheme();
    } else {
      new Thread(this::ensureTextmateTheme).start();
    }
    
    
    if (contents == null) {
      contents = new ArrayList<>();
    }
    
    final boolean wordwrap = SettingsData.getBoolean(ctx, "wordwrap", false);
    
    
    if (isNewFile) {
      editor.setText("");
      setListner();
    } else {
      new Thread(() -> {
        try {
          InputStream inputStream;
          inputStream = ctx.getContentResolver().openInputStream(file.getUri());
          assert inputStream != null;
          Content content = ContentIO.createFrom(inputStream);
          contents.add(content);
          inputStream.close();
          BaseActivityKt.runOnUi(() -> editor.setText(content));
          if (wordwrap) {
            int length = content.toString().length();
            if (length > 700 && content.toString().split("\\R").length < 100) {
              BaseActivityKt.runOnUi(() -> rkUtils.toast(ctx, getResources().getString(R.string.ww_wait)));
            }
            if (length > 1500) {
              BaseActivityKt.runOnUi(() -> Toast.makeText(ctx, getResources().getString(R.string.ww_wait), Toast.LENGTH_LONG).show());
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
        setListner();
        
      }).start();
      
      
    }
    
    editor.setTypefaceText(Typeface.createFromAsset(ctx.getAssets(), "JetBrainsMono-Regular.ttf"));
    editor.setTextSize(14);
    editor.setWordwrap(wordwrap);
    undo = StaticData.menu.findItem(R.id.undo);
    redo = StaticData.menu.findItem(R.id.redo);
    
    
  }
  
  private void setListner() {
    editor.subscribeAlways(ContentChangeEvent.class, (event) -> {
      updateUndoRedo();
      TabLayout.Tab tab = mTabLayout.getTabAt(fragments.indexOf(this));
      if (isModified) {
        tab.setText(fileName + "*");
      }
      isModified = true;
      
    });
    
  }
  
  public DocumentFile getFile() {
    return file;
  }
  
  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return editor;
  }
  
  public void updateUndoRedo() {
    redo.setEnabled(editor.canRedo());
    undo.setEnabled(editor.canUndo());
  }
  
  public void releaseEditor() {
    releaseEditor(false);
  }
  
  public void releaseEditor(boolean removeCoontent) {
    editor.release();
    editor = null;
    if (removeCoontent) {
      contents.remove(fileList.indexOf(file));
    }
    
  }
  
  public void Undo() {
    if (editor.canUndo()) {
      editor.undo();
    }
  }
  
  public void Redo() {
    if (editor.canRedo()) {
      editor.redo();
    }
  }
  
  public CodeEditor getEditor() {
    return editor;
  }
  
  private void ensureTextmateTheme() {
    
    var editorColorScheme = editor.getColorScheme();
    var themeRegistry = ThemeRegistry.getInstance();
    
    boolean darkMode = SettingsData.isDarkMode(ctx);
    try {
      
      if (darkMode) {
        String path;
        if (SettingsData.isOled(ctx)) {
          path = ctx.getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/black/darcula.json";
        } else {
          path = ctx.getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/darcula.json";
        }
        if (!new File(path).exists()) {
          BaseActivityKt.runOnUi(() -> rkUtils.toast(ctx, getResources().getString(R.string.theme_not_found_err)));
        }
        
        themeRegistry.loadTheme(new ThemeModel(IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null), "darcula"));
        editorColorScheme = TextMateColorScheme.create(themeRegistry);
        if (SettingsData.isOled(ctx)) {
          editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK);
        }
      } else {
        String path = ctx.getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/quietlight.json";
        if (!new File(path).exists()) {
          BaseActivityKt.runOnUi(() -> rkUtils.toast(ctx, getResources().getString(R.string.theme_not_found_err)));
          
        }
        themeRegistry.loadTheme(new ThemeModel(IThemeSource.fromInputStream(FileProviderRegistry.getInstance().tryGetInputStream(path), path, null), "quitelight"));
        editorColorScheme = TextMateColorScheme.create(themeRegistry);
      }
      
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    if (darkMode) {
      SharedPreferences pref = ctx.getApplicationContext().getSharedPreferences("MyPref", 0);
      themeRegistry.setTheme("darcula");
    } else {
      themeRegistry.setTheme("quietlight");
    }
    
    editor.setColorScheme(editorColorScheme);
  }
  
}


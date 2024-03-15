package com.rk.xededitor.activities.MainActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.rk.xededitor.R;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import java.io.InputStream;
import org.eclipse.tm4e.core.registry.IThemeSource;

public class mFragment extends Fragment {

  private CodeEditor editor;
  private Content content;
  private Context ctx;

  public mFragment(Context ctx, Uri uri, String name) {
    this.ctx = ctx;
    try {
      InputStream inputStream;
      inputStream = ctx.getContentResolver().openInputStream(uri);
      content = ContentIO.createFrom(inputStream);
      inputStream.close();
      inputStream = null;

    } catch (Exception e) {
      e.printStackTrace();
      rkUtils.toast(ctx, e.toString());
      System.exit(1);
    }
    editor = new CodeEditor(ctx, null);
    if (rkUtils.isDarkMode(ctx)) {
      editor.setColorScheme(new SchemeDarcula());
    }

    editor.setText(content);
    editor.setTypefaceText(Typeface.createFromAsset(ctx.getAssets(), "JetBrainsMono-Regular.ttf"));
    editor.setTextSize(14);

    ensureTextmateTheme();
    MainActivity.getBinding().appBarMain.undo.setEnabled(editor.canUndo());
    MainActivity.getBinding().appBarMain.redo.setEnabled(editor.canRedo());
 
    editor.subscribeEvent(
        ContentChangeEvent.class,
        (event, unsubscribe) -> {
          MainActivity.getBinding().appBarMain.undo.setEnabled(editor.canUndo());
          MainActivity.getBinding().appBarMain.redo.setEnabled(editor.canRedo());
        });
  }

  public Content getContent() {
    return content;
  }

  public CodeEditor getEditor() {
    return editor;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Inflate the layout for this fragment

    //   ensureTextmateTheme();
    MainActivity.getBinding().appBarMain.undo.setEnabled(editor.canUndo());
    MainActivity.getBinding().appBarMain.redo.setEnabled(editor.canRedo());
    return editor;
  }

  public void releaseEditor() {
    editor.release();
    editor = null;
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
        } else {
          path = rkUtils.getPublicDirectory() + "/files/textmate/darcula.json";
        }

        themeRegistry.loadTheme(
            new ThemeModel(
                IThemeSource.fromInputStream(
                    FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                "darcula"));
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
      themeRegistry.setTheme("darcula");
    } else {
      themeRegistry.setTheme("quietlight");
    }

    editor.setColorScheme(editorColorScheme);
  }
}

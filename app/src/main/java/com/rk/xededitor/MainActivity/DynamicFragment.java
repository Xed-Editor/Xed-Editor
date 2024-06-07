package com.rk.xededitor.MainActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

import com.rk.xededitor.rkUtils;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;


public class DynamicFragment extends Fragment {
    public static List<Content> contents;
    public CodeEditor editor;
    private final DocumentFile file;
    private final Context ctx;

    public DynamicFragment(DocumentFile file, Context ctx) {
        this.ctx = ctx;
        this.file = file;
        editor = new CodeEditor(ctx);
        if (contents == null) {
            contents = new ArrayList<>();
        }
        Content content = null;
        try {
            InputStream inputStream;
            inputStream = ctx.getContentResolver().openInputStream(file.getUri());
            content = ContentIO.createFrom(inputStream);
            contents.add(content);
            inputStream.close();
            inputStream = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
        editor.setText(content);
        editor.setTypefaceText(Typeface.createFromAsset(ctx.getAssets(), "JetBrainsMono-Regular.ttf"));
        editor.setTextSize(14);
        editor.setWordwrap(Boolean.parseBoolean(rkUtils.getSetting(ctx,"wordwrap","false")));
        ensureTextmateTheme();

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return editor;
    }

    public void releaseEditor() {
        editor.release();
        editor = null;
        contents.remove(MainActivity.fileList.indexOf(file));
    }

    public CodeEditor getEditor() {
        return editor;
    }

    private void ensureTextmateTheme() {

        var editorColorScheme = editor.getColorScheme();
        var themeRegistry = ThemeRegistry.getInstance();

        boolean darkMode = rkUtils.isDarkMode(ctx);
        try {

            if (darkMode) {
                String path;
                if (rkUtils.isOled(ctx)) {
                    path = ctx.getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/black/darcula.json";
                } else {
                    path = ctx.getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/darcula.json";
                }
                if (!new File(path).exists()) {
                    rkUtils.toast("theme file not found please reinstall the Xed Editor");
                }

                themeRegistry.loadTheme(
                        new ThemeModel(
                                IThemeSource.fromInputStream(
                                        FileProviderRegistry.getInstance().tryGetInputStream(path), path, null),
                                "darcula"));
                editorColorScheme = TextMateColorScheme.create(themeRegistry);
                if(rkUtils.isOled(ctx)){
                    editorColorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.BLACK);
                }

            } else {

                String path = ctx.getExternalFilesDir(null).getAbsolutePath() + "/unzip/textmate/quietlight.json";
                if (!new File(path).exists()) {
                    rkUtils.toast("theme file not found");
                }
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


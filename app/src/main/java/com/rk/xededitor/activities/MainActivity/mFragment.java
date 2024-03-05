package com.rk.xededitor.activities.MainActivity;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.rk.xededitor.activities.MainActivity.Xed;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentIO;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import java.io.InputStream;
import com.rk.xededitor.R;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;

public class mFragment extends Fragment {

  private CodeEditor editor;
  private Content content;

  public mFragment(Context ctx,Uri uri, Language lang) {

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
    if(rkUtils.isDarkMode(ctx)){
      editor.setColorScheme(new SchemeDarcula());
    }else{
      final String color =
        String.format(
            "#%06X", (0xFFFFFF & ctx.getResources().getColor(R.color.c0, ctx.getTheme())));
    final EditorColorScheme default_theme = editor.getColorScheme();
    default_theme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor(color));
    default_theme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color.parseColor(color));
    editor.setColorScheme(default_theme);
    }
    
    editor.setText(content);
    editor.setEditorLanguage(lang);
    editor.setTypefaceText(Typeface.createFromAsset(ctx.getAssets(), "JetBrainsMono-Regular.ttf"));
    editor.setTextSize(14);
  }

  public Content getContent() {
    return content;
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Inflate the layout for this fragment

    return editor;
  }

}

package com.rk.xededitor.ui.themes;

import android.app.Activity;
import android.view.View;
import android.view.WindowInsetsController;
import android.widget.ImageButton;
import com.rk.xededitor.*;
import android.content.Context;
import android.graphics.Color;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.CodeEditor;
import android.content.Context;
import android.graphics.Color;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.CodeEditor;
import android.os.Build;

public class Xed {

  @SuppressWarnings("deprecation")
  public static void applyTheme(Activity ctx, CodeEditor editor) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowInsetsController insetsController = ctx.getWindow().getInsetsController();
      if (insetsController != null) {
        insetsController.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      View decorView = ctx.getWindow().getDecorView();
      decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }
    
    final String color =
        String.format(
            "#%06X", (0xFFFFFF & ctx.getResources().getColor(R.color.c0, ctx.getTheme())));
    final EditorColorScheme default_theme = editor.getColorScheme();
    default_theme.setColor(EditorColorScheme.WHOLE_BACKGROUND, Color.parseColor(color));
    default_theme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, Color.parseColor(color));
    editor.setColorScheme(default_theme);
  }
}

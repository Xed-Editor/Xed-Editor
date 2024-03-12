package com.rk.xededitor.activities.settings;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.*;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.*;
import io.github.rosemoe.sora.widget.schemes.*;
import com.rk.xededitor.R;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class Settings extends AppCompatActivity {

 // private static final int REQUEST_CODE_PICK_FOLDER = 123;
  private boolean isOled = false;
  private Context ctx;
  @SuppressWarnings("deprecation")
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    SharedPreferences pref =
        getApplicationContext().getSharedPreferences("MyPref", 0); // 0 - for private mode
    isOled = pref.getBoolean("isOled",false);
    if (isOled) {
      setTheme(R.style.oled);
    }
    setContentView(R.layout.layout_settings);
    ctx = this;
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Enable back button
    getSupportActionBar().setDisplayShowTitleEnabled(false); // Hide default title

    toolbar.setNavigationOnClickListener(v -> onBackPressed());

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
    }
    
    MaterialSwitch xswitch = findViewById(R.id.oled);
    xswitch.setChecked(isOled);
    xswitch.setOnCheckedChangeListener(
        new CompoundButton.OnCheckedChangeListener() {
          @Override
          public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            // Do something when the switch is toggled on/off
            SharedPreferences pref =
                getApplicationContext().getSharedPreferences("MyPref", 0); // 0 - for private mode
            Editor editor = pref.edit();

            editor.putBoolean("isOled", isChecked);
            editor.commit();
            rkUtils.toast(ctx,"Restart Required!");
          }
        });
  }
}

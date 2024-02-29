package com.rk.xededitor.activities.SimpleEditor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SimpleEditor extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    handleIntent(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    handleIntent(intent);
  }

  private void handleIntent(Intent intent) {
    if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
      Uri uri = intent.getData();
      if (uri != null) {
        // Handle the URI, read the content of the text file, etc.
      }
    }
  }
}

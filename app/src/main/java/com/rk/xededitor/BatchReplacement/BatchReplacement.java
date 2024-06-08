package com.rk.xededitor.BatchReplacement;

import static com.rk.xededitor.MainActivity.MainActivity.mTabLayout;
import static com.rk.xededitor.rkUtils.dpToPx;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.rk.xededitor.MainActivity.DynamicFragment;
import com.rk.xededitor.MainActivity.mAdapter;
import com.rk.xededitor.R;
import com.rk.xededitor.databinding.ActivityBatchReplacementBinding;
import com.rk.xededitor.rkUtils;

import java.util.Objects;
import java.util.Random;

import io.github.rosemoe.sora.widget.CodeEditor;

public class BatchReplacement extends AppCompatActivity {

    private ActivityBatchReplacementBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityBatchReplacementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Replace");

        if (rkUtils.isDarkMode(this) && rkUtils.isOled(this)) {
            findViewById(R.id.drawer_layout).setBackgroundColor(Color.BLACK);
            findViewById(R.id.appbar).setBackgroundColor(Color.BLACK);
            findViewById(R.id.mainBody).setBackgroundColor(Color.BLACK);
            findViewById(R.id.toolbar).setBackgroundColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);
        }


    }

    private View newEditBox(String hint) {
        LinearLayout rootLinearLayout = new LinearLayout(this);
        rootLinearLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rootLinearLayout.setTag("keyRep");
        rootLinearLayout.setOrientation(LinearLayout.VERTICAL);

        // Create the inner LinearLayout
        LinearLayout innerLinearLayout = new LinearLayout(this);
        LinearLayout.LayoutParams innerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(50, this));  // height is 50dp
        innerParams.setMargins(dpToPx(22, this), dpToPx(10, this), dpToPx(22, this), 0);
        innerLinearLayout.setLayoutParams(innerParams);
        innerLinearLayout.setTag("keyword");

        // Set the background for innerLinearLayout
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.edittext);
        innerLinearLayout.setBackground(drawable);

        // Create the EditText
        EditText editText = new EditText(this);
        LinearLayout.LayoutParams editTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        editText.setLayoutParams(editTextParams);
        editText.setPadding(dpToPx(8, this), 0, dpToPx(5, this), 0);  // paddingStart 8dp, paddingEnd 5dp
        editText.setId(View.generateViewId());
        editText.setSingleLine(true);
        editText.setHint(hint);
        editText.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // Add EditText to inner LinearLayout
        innerLinearLayout.addView(editText);

        rootLinearLayout.addView(innerLinearLayout);
        return rootLinearLayout;
    }

    public void addBatch(View v) {
        int random_int = new Random().nextInt();
        ((LinearLayout) findViewById(R.id.mainBody)).addView(newEditBox("keyword"));
        ((LinearLayout) findViewById(R.id.mainBody)).addView(newEditBox("replacement"));
        View view = new View(this);

        // Set the width and height of the View
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dpToPx(0, this), // Width in dp
                dpToPx(20, this)   // Height in dp
        );
        view.setLayoutParams(params);
        ((LinearLayout) findViewById(R.id.mainBody)).addView(view);

        findViewById(R.id.removeBatch).setVisibility(View.VISIBLE);
    }

    public void removeBatch(View v) {
        LinearLayout linearLayout = findViewById(R.id.mainBody);
        int childCount = linearLayout.getChildCount();
        if (childCount > 3) {
            // Remove the last child first
            linearLayout.removeViewAt(childCount - 1);
            // Remove the second-to-last child
            linearLayout.removeViewAt(childCount - 2);
            // Remove the third-to-last child
            linearLayout.removeViewAt(childCount - 3);
            if (linearLayout.getChildCount() <= 3) {
                findViewById(R.id.removeBatch).setVisibility(View.GONE);
            }
        } else {
            findViewById(R.id.removeBatch).setVisibility(View.GONE);
        }

    }

    public void replace_all(View v) {
        ProgressDialog dialog = ProgressDialog.show(this, "",
                "Please wait...", true);
        LinearLayout linearLayout = findViewById(R.id.mainBody);
        for (int i = 0; i < linearLayout.getChildCount(); i++) {
            View view = linearLayout.getChildAt(i);
            if (view instanceof LinearLayout) {
                LinearLayout l = (LinearLayout) ((LinearLayout) view).getChildAt(0);
                EditText editText = (EditText) l.getChildAt(0);
                if (editText.getHint().equals("keyword")) {
                    View viewx = linearLayout.getChildAt(i + 1);
                    LinearLayout lx = (LinearLayout) ((LinearLayout) viewx).getChildAt(0);
                    EditText editTextx = (EditText) lx.getChildAt(0);
                    String keyword = editText.getText().toString();
                    String replacement = editTextx.getText().toString();
                    if (mAdapter.fragments == null || mTabLayout == null) {
                        return;
                    }
                    CodeEditor editor = ((DynamicFragment) mAdapter.fragments.get(mTabLayout.getSelectedTabPosition())).getEditor();
                    editor.setText(editor.getText().toString().replaceAll(keyword, replacement));
                }
            }
        }
        dialog.hide();
        rkUtils.toast(this, "Action Completed");
    }
}
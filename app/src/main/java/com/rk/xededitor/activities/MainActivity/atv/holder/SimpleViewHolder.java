package com.rk.xededitor.activities.MainActivity;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.RotateAnimation;
import android.view.animation.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.rk.xededitor.rkUtils;
import io.github.rosemoe.sora.text.ContentIO;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import io.github.rosemoe.sora.widget.CodeEditor;
import java.io.IOException;
import java.io.*;
import com.google.android.material.tabs.TabLayout;
import java.net.URLConnection;
import android.os.Vibrator;
import android.os.*;
import com.rk.xededitor.rkUtils;
import com.rk.xededitor.R;
import static com.rk.xededitor.Config.FileViewer.indentation_level;

public class SimpleViewHolder extends TreeNode.BaseNodeViewHolder<Object> {
    private boolean isFile;
    private ImageView arrow;
    private TreeNode node;
    private final Context context;
    private final int berryColor;
    private final int closedDrawable;
    private final int openedDrawable;
    private final int folderDrawable;
    private final int fileDrawable;
    

    private EditorManager manager;
    private CodeEditor editor;
    boolean isRotated = false;

    final LinearLayout layout;
    final LinearLayout.LayoutParams layout_params;
    final LinearLayout.LayoutParams imgParams;
    final ImageView img;
    final TextView tv;
    final LinearLayout.LayoutParams params;
    final LinearLayout.LayoutParams arr_params;
     
    

    public SimpleViewHolder(Context context) {
        super(context);
        this.context = context;
        berryColor = ContextCompat.getColor(context, R.color.berry);
        closedDrawable = R.drawable.closed;
        openedDrawable = R.drawable.opened;
        folderDrawable = R.drawable.folder;
        fileDrawable = R.drawable.file;
        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);

        layout_params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout_params.setMargins(0, 10, 0, 10);
        layout.setLayoutParams(layout_params);

        arrow = new ImageView(context);

        img = new ImageView(context);
        img.setImageDrawable(ContextCompat.getDrawable(context, fileDrawable));

        tv = new TextView(context);
        tv.setTextColor(berryColor);
        
        

        imgParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        imgParams.setMargins(0, 0, 4, 0);

        arr_params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        editor = MainActivity.getEditor();
        manager = new EditorManager(editor, context);

        
        
        
    }

    @Override
    public View createNodeView(TreeNode node, Object value) {
        this.node = node;
        isFile = node.isFile;

        tv.setText(String.valueOf(value));

        if (!isFile) {

            arr_params.setMargins(indentation_level * node.indentation, 7, 0, 0);
            arrow.setLayoutParams(arr_params);
            arrow.setImageDrawable(ContextCompat.getDrawable(context, closedDrawable));
            layout.addView(arrow);
            img.setImageDrawable(ContextCompat.getDrawable(context, folderDrawable));
            img.setLayoutParams(imgParams);
        } else {

            params.setMargins(indentation_level * node.indentation, 0, 0, 0);

            img.setLayoutParams(params);
        }

        layout.addView(img);
        layout.addView(tv);

        return layout;
    }

    @Override
    public void toggle(boolean active) {
        if (node == null || arrow == null || node.file == null) {
            return;
        }
        // implement loading
        if (!isFile) {
            

            if (isRotated) {
                arrow.startAnimation(animate(90,0));
                isRotated = false;
            } else {
                arrow.startAnimation(animate(0,90));
                isRotated = true;
            }

            if (!node.isLoaded()) {
                rkUtils.looper(node.file, node, node.indentation + 1);
                node.setLoaded();
            }

        } else if (isFile) {
            //  Uri uri = node.file.getUri();

            String type = node.file.getType();
            if (type == null) {
                rkUtils.toast(context, "Error: Mime Type is null");
            }
            if (!(type.contains("text") || type.contains("plain"))) {
                // Todo: show window warning user (it's not a file )
                manager.newEditor(node.file);
            } else {
                manager.newEditor(node.file);
            }
        }
    }

    private RotateAnimation animate(int from,int to) {
    	RotateAnimation rotateAnimation =
                new RotateAnimation(
                        from, to, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(300);
        rotateAnimation.setFillAfter(true);
        return rotateAnimation;
    }
}

package com.rk.xededitor.MainActivity.TreeView;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.rk.xededitor.MainActivity.MainActivity;
import com.rk.xededitor.R;
import com.rk.xededitor.rkUtils;

public class SimpleViewHolder extends TreeNode.BaseNodeViewHolder<Object> {
    final LinearLayout layout;
    final LinearLayout.LayoutParams layout_params;
    final LinearLayout.LayoutParams imgParams;
    final ImageView img;
    final TextView tv;
    final LinearLayout.LayoutParams params;
    final LinearLayout.LayoutParams arr_params;
    private final Context context;
    private final int berryColor;
    private final int closedDrawable;
    private final int folderDrawable;
    private final int fileDrawable;
    private final int indentation_level = 68;
    //   private CodeEditor editor;
    boolean isRotated = false;
    private boolean isFile;
    private final ImageView arrow;
    private TreeNode node;


    public SimpleViewHolder(Context context) {
        super(context);
        this.context = context;
        berryColor = ContextCompat.getColor(context, R.color.berry);
        closedDrawable = R.drawable.closed;
        //closedDrawable = R.drawable.ic_launcher_foreground;

        folderDrawable = R.drawable.folder;
        //folderDrawable = R.drawable.ic_launcher_foreground;
        fileDrawable = R.drawable.file;
        //fileDrawable = R.drawable.ic_launcher_foreground;
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

        //editor = MainActivity.getEditor();


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
                arrow.startAnimation(animate(90, 0));
                isRotated = false;
            } else {
                arrow.startAnimation(animate(0, 90));
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
                ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
            } else {
                ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
            }
            ((MainActivity) MainActivity.getActivity()).onNewEditor();
        }
    }

    private RotateAnimation animate(int from, int to) {
        RotateAnimation rotateAnimation =
                new RotateAnimation(
                        from, to, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setDuration(300);
        rotateAnimation.setFillAfter(true);
        return rotateAnimation;
    }
}

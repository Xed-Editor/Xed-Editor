package com.rk.xededitor.MainActivity.TreeViewX;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rk.xededitor.MainActivity.MainActivity;
import com.rk.xededitor.R;
import com.rk.xededitor.rkUtils;

import org.apache.tika.Tika;

import java.io.InputStream;

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
    private final int indentation_level = 48;
    private final ImageView arrow;
    //   private CodeEditor editor;
    boolean isRotated = false;
    private boolean isFile;
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

    @SuppressLint("SetTextI18n")
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
                rkUtils.toast(context, "Error: File Mime Type is null");
            }


            ((MainActivity) MainActivity.getActivity()).onNewEditor();
            assert type != null;
            if (!(type.contains("text") || type.contains("plain"))) {
                if (Boolean.parseBoolean(rkUtils.getSetting(context, node.file.getName(), "false"))) {
                    ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
                    return;
                }
                Tika tika = new Tika();
                ContentResolver contentResolver = context.getContentResolver();
                try (InputStream inputStream = contentResolver.openInputStream(node.file.getUri())) {
                    String s = tika.detect(inputStream);
                    if (Boolean.parseBoolean(rkUtils.getSetting(context, s, "false"))) {
                        ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
                        return;
                    }
                    if (s.contains("text") || s.contains("plain")) {
                        ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
                        rkUtils.setSetting(context, node.file.getName(), "true");
                    } else {

                        View popuop_view = LayoutInflater.from(context).inflate(R.layout.popup_nontext, null);
                        ((TextView) popuop_view.findViewById(R.id.msg)).setText("Selected file is a " + s + " file. opening non text file can permanently corrupt the file. \n\nare you sure you want to open this?");
                        ((CheckBox) popuop_view.findViewById(R.id.ignore)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                rkUtils.setSetting(context, s, Boolean.toString(isChecked));
                                assert node.file != null;
                                rkUtils.setSetting(context, node.file.getName(), Boolean.toString(isChecked));
                            }
                        });
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("Non Text File")
                                .setView(popuop_view)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Open",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialogInterface, int i) {
                                                assert node.file != null;
                                                ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
                                            }
                                        })
                                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                    @Override
                                    public void onCancel(DialogInterface dialog) {

                                    }
                                })
                                .show();


                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }


            } else {
                ((MainActivity) MainActivity.getActivity()).newEditor(node.file);
            }

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

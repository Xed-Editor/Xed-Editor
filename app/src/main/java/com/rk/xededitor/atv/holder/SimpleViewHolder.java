package com.rk.xededitor;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.rk.xededitor.rkUtils;

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
    public final int indentation_level = 50;
    public static boolean showIndentation = false;

    public SimpleViewHolder(Context context) {
        super(context);
        this.context = context;
        berryColor = ContextCompat.getColor(context, R.color.berry);
        closedDrawable = R.drawable.closed;
        openedDrawable = R.drawable.opened;
        folderDrawable = R.drawable.folder;
        fileDrawable = R.drawable.file;
    }

    @Override
    public View createNodeView(TreeNode node, Object value) {
        this.node = node;
        isFile = node.isFile;
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout.LayoutParams layout_params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout_params.setMargins(0, 10, 0, 10);
        layout.setLayoutParams(layout_params);
        
        arrow = new ImageView(context);
        final ImageView img = new ImageView(context);
        final TextView tv = new TextView(context);
        if(showIndentation){
            tv.setText(String.valueOf(value)+" "+node.indentation);
        }else{
            tv.setText(String.valueOf(value));
        }
        
        tv.setTextColor(berryColor);
        

        
        
      //  final int space = 10;
        

        LinearLayout.LayoutParams imgParams;
        if (!isFile) {
            imgParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            imgParams.setMargins(0, 0, 10, 0);
            final LinearLayout.LayoutParams arr_params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            arr_params.setMargins(indentation_level * node.indentation,7,0,0);
            arrow.setLayoutParams(arr_params);
            arrow.setImageDrawable(ContextCompat.getDrawable(context, closedDrawable));
            layout.addView(arrow);
            img.setImageDrawable(ContextCompat.getDrawable(context, folderDrawable));
            img.setLayoutParams(imgParams);
        } else {
            final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(indentation_level * node.indentation, 0, 0, 0);
            img.setImageDrawable(ContextCompat.getDrawable(context, fileDrawable));
            img.setLayoutParams(params);
        }
        
        layout.addView(img);
        layout.addView(tv);

        return layout;
    }

    @Override
    public void toggle(boolean active) {
        if (isFile || node == null || arrow == null) {
            return;
        }
        //implement loading
        if(!node.isLoaded()){
            MainActivity.looper(node.folder,node,node.indentation+1);
            node.setLoaded();
        }
            
        
        
        
        
        arrow.setImageDrawable(ContextCompat.getDrawable(context, active ? openedDrawable : closedDrawable));
    }
}
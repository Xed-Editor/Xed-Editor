package com.rk.xededitor;

import android.content.Context;
import android.graphics.Color;
import android.media.Image;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Created by Bogdan Melnychuk on 2/11/15. */
public class SimpleViewHolder extends TreeNode.BaseNodeViewHolder<Object> {

    public SimpleViewHolder(Context context) {
        super(context);
    }

    @Override
    public View createNodeView(TreeNode node, Object value) {
        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        final ImageView img = new ImageView(context);
        final TextView tv = new TextView(context);
        tv.setText(String.valueOf(value) + node.indentation);
        if (!node.isFile) {
            tv.setTextColor(Color.GREEN);
            img.setImageDrawable(context.getResources().getDrawable(R.drawable.folder));
        }else{
            img.setImageDrawable(context.getResources().getDrawable(R.drawable.file));
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(100 * node.indentation, 0, 0, 0); // left, top, right, bottom
       // tv.setLayoutParams(params);
        img.setLayoutParams(params)  ;  
        
        
        
        
        layout.addView(img);
        layout.addView(tv);

        return layout;
    }

    @Override
    public void toggle(boolean active) {}
}

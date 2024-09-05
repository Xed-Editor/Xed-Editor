package com.rk.xededitor.Pluginclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.rk.libPlugin.R;
import com.rk.libPlugin.server.Plugin;
import com.rk.libPlugin.server.Loader;
import com.rk.libPlugin.server.PluginUtils;

import java.io.IOException;
import java.util.List;
import java.io.File;


public class CustomListAdapter extends ArrayAdapter<Plugin> {

    Context context;
    public CustomListAdapter(Context context, List<Plugin> items) {
        super(context, 0, items);
        this.context = context;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        Plugin plugin = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
        }


        TextView title = convertView.findViewById(R.id.title);
        assert plugin != null;
        title.setText(plugin.getInfo().getName());

        TextView pkg = convertView.findViewById(R.id.pkg);
        pkg.setText(plugin.getInfo().getPackageName());

        String image_path = plugin.getInfo().getIcon();
        var file = new File(plugin.getPluginHome(),image_path);
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(file.getCanonicalPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (bitmap != null){
            ImageView imageView = convertView.findViewById(R.id.icon);
            imageView.setImageBitmap(bitmap);
        }

        MaterialSwitch materialSwitch = convertView.findViewById(R.id.toggle);

        materialSwitch.setChecked(PluginUtils.isPluginActive(context,plugin.getInfo().getPackageName(),false));

        materialSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> PluginUtils.setPluginActive(context,plugin.getInfo().getPackageName(),isChecked));
        return convertView;
    }


}

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

import java.util.List;


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
        Bitmap bitmap = BitmapFactory.decodeFile(image_path);
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

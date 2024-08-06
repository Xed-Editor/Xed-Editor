package com.rk.xededitor.MainActivity;

import android.util.Log;
import android.view.Menu;

import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.MainActivity.fragment.DynamicFragment;
import com.rk.xededitor.MainActivity.treeview2.Node;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class StaticData {

    public static final int REQUEST_DIRECTORY_SELECTION = 2937;
    public static ArrayList<DynamicFragment> fragments;
    public static List<Node<File>> nodes;
    public static TabLayout mTabLayout;
    public static Menu menu;
    public static File rootFolder;


    public static void clear() {
        nodes = null;
        menu = null;
        fragments = null;
        mTabLayout = null;
        rootFolder = null;
        Log.d("Data", "Cleaning Static Data...");
        //run the garbage collector
        System.gc();
    }

}

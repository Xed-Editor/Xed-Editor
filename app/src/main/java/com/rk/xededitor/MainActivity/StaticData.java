package com.rk.xededitor.MainActivity;

import android.view.Menu;

import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.MainActivity.fragment.DynamicFragment;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;


public class StaticData {

    public static final int REQUEST_DIRECTORY_SELECTION = 2937;
    public static final int REQUEST_FILE_SELECTION = 64653;
    public static final int MANAGE_EXTERNAL_STORAGE = 6738973;
    public static final int REQUEST_CODE_STORAGE_PERMISSIONS = 3595397;

    public static ArrayList<DynamicFragment> fragments;
    public static HashSet<File> fileSet;
    public static TabLayout mTabLayout;
    public static Menu menu;
    public static File rootFolder;

    public static void clear() {
        fragments = null;
        mTabLayout = null;
        menu = null;
        rootFolder = null;
        fileSet = null;

        //run the garbage collector
        System.gc();
    }

}

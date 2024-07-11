package com.rk.xededitor.MainActivity;

import android.net.Uri;
import android.util.Log;
import android.view.Menu;

import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.tabs.TabLayout;
import com.rk.xededitor.MainActivity.treeview2.Node;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.text.Content;


public class StaticData {
  
  
  
  public static ArrayList<DynamicFragment> fragments;
  public static ArrayList<String> titles;
  
 public static List<Node<DocumentFile>> nodes;
  
  public static ArrayList<Uri> uris;
  public static List<Content> contents;
  public static TabLayout mTabLayout;
  public static List<DocumentFile> fileList;
  public static Menu menu;
  public static DocumentFile rootFolder;
  
  
  public static void clear() {
    
    nodes = null;
    menu = null;
    fragments = null;
    titles = null;
    uris = null;
    contents = null;
    mTabLayout = null;
    fileList = null;
    rootFolder = null;
    
    
    //run the garbage collector
    Log.d("Data", "Cleaning State Data...");
    System.gc();
  }
  
}

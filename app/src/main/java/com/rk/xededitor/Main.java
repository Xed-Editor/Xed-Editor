package com.rk.xededitor;

import android.app.Activity;
import android.content.Context;
import com.rk.xededitor.databinding.FragmentHomeBinding;

public class Main {
  private final Activity context;
  //its hbinding in MainActivity.kt
  private final FragmentHomeBinding binding;
  
  public Main(Activity context,FragmentHomeBinding binding) {
    this.context = context;
    this.binding = binding;
    //to do match editor colors to ui
  }
}

package com.rk.xededitor.MainActivity.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.io.File

class MainViewModel : ViewModel() {
    val files = MutableLiveData<List<File>>()
    val selectedFile = MutableLiveData<File>()

}
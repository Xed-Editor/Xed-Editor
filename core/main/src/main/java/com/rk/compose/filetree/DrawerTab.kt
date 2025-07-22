package com.rk.compose.filetree

import androidx.compose.runtime.Composable
import com.rk.file_wrapper.FileObject

open class DrawerTab(val id: String,val label: String, val icon:@Composable ()-> Unit, val content:@Composable ()-> Unit) {}

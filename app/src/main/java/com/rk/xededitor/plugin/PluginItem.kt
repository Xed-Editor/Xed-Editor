package com.rk.xededitor.plugin

import java.io.Serializable

class PluginItem(
  val icon: String?,
  val title: String,
  val packageName: String,
  val description: String,
  val repo: String
) : Serializable

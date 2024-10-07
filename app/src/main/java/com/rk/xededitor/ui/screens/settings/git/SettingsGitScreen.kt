package com.rk.xededitor.ui.screens.settings.git

import android.view.LayoutInflater
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.xededitor.Keys
import com.rk.xededitor.R
import com.rk.xededitor.SettingsData
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory
import java.io.File

@Composable
fun SettingsGitScreen() {
  PreferenceLayout(
    label = stringResource(id = R.string.git),
    backArrowVisible = true,
  ) {

    val context = LocalContext.current

    PreferenceCategory(label = stringResource(id = R.string.cred),
      description = stringResource(id = R.string.gitcred),
      iconResource = R.drawable.key,
      onNavigate = {
        val view = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val edittext = view.findViewById<EditText>(R.id.name).apply {
          hint = getString(R.string.gitKeyExample)
          setText(SettingsData.getString(Keys.GIT_CRED, ""))
        }
        MaterialAlertDialogBuilder(context).setTitle(getString(R.string.cred)).setView(view)
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.apply)) { _, _ ->
            val credentials = edittext.text.toString()
            if (credentials.isEmpty()) {
              return@setPositiveButton
            }
            SettingsData.setString(Keys.GIT_CRED, credentials)
          }.show()

      })

    PreferenceCategory(label = stringResource(id = R.string.userdata),
      description = stringResource(id = R.string.userdatagit),
      iconResource = R.drawable.person,
      onNavigate = {


        val view = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val edittext = view.findViewById<EditText>(R.id.name).apply {
          hint = getString(R.string.gituserexample)
          setText(SettingsData.getString(Keys.GIT_USER_DATA, ""))
        }
        MaterialAlertDialogBuilder(context).setTitle(getString(R.string.userdata)).setView(view)
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.apply)) { _, _ ->
            val userdata = edittext.text.toString()
            if (userdata.isEmpty()) {
              return@setPositiveButton
            }
            SettingsData.setString(Keys.GIT_USER_DATA, userdata)
          }.show()
      })


    PreferenceCategory(label = stringResource(id = R.string.repo_dir),
      description = stringResource(id = R.string.clone_dir),
      iconResource = R.drawable.outline_folder_24,
      onNavigate = {
        val view = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val edittext = view.findViewById<EditText>(R.id.name).apply {
          hint = "/storage/emulated/0"
          setText(SettingsData.getString(Keys.GIT_REPO_DIR, "/storage/emulated/0"))
        }
        MaterialAlertDialogBuilder(context).setTitle(getString(R.string.repo_dir)).setView(view)
          .setNegativeButton(getString(R.string.cancel), null)
          .setPositiveButton(getString(R.string.apply)) { _, _ ->
            val repodir = edittext.text.toString()
            if (repodir.isEmpty()) {
              return@setPositiveButton
            }
            if (!File(repodir).exists()) {
              rkUtils.toast(getString(R.string.dir_exist_not))
              return@setPositiveButton
            }
            SettingsData.setString(Keys.GIT_REPO_DIR, repodir)
          }.show()
      })
  }
}
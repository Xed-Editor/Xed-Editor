package com.rk.xededitor.ui.screens.settings.terminal

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jaredrummler.ktsh.Shell
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.settings.Keys
import com.rk.xededitor.settings.SettingsData
import com.rk.xededitor.settings.SettingsData.getBoolean
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory
import java.io.File
import java.nio.file.Files

@OptIn(DelicateCoroutinesApi::class)
fun updateProotArgs(context: Context):Boolean{
  GlobalScope.launch(Dispatchers.IO){
    val link2sym = getBoolean(Keys.LINK2SYMLINK,false)
    val ashmemfd = getBoolean(Keys.ASHMEM_MEMFD,true)
    val sysvipc = getBoolean(Keys.SYSVIPC,true)
    val killOnExit = getBoolean(Keys.KILL_ON_EXIT,true)

    val sb = StringBuilder()
    if (link2sym){
      sb.append(" --link2symlink")
    }
    if (ashmemfd){
      sb.append(" --ashmem-memfd")
    }

    if (sysvipc){
      sb.append(" --sysvipc")
    }

    if (killOnExit){
      sb.append(" --kill-on-exit ")
    }


    Shell.SH.apply {
      run("echo $sb > ${File(context.filesDir.parentFile,"proot_args").absolutePath}")
      shutdown()
    }

  }

  return true
}

@Composable
fun SettingsTerminalScreen() {
  PreferenceLayout(
    label = stringResource(id = R.string.terminal),
    backArrowVisible = true,
  ) {

    val context = LocalContext.current

    var failSafe by remember {
      mutableStateOf(
        getBoolean(
          Keys.FAIL_SAFE, false
        )
      )
    }

    var ctrlWorkAround by remember {
      mutableStateOf(
        getBoolean(
          Keys.CTRL_WORKAROUND, false
        )
      )
    }

    var forceChar by remember {
      mutableStateOf(
        getBoolean(
          Keys.FORCE_CHAR, false
        )
      )
    }
    var link2sym by remember {
      mutableStateOf(
        getBoolean(
          Keys.LINK2SYMLINK, true
        )
      )
    }

    var ashememFd by remember {
      mutableStateOf(
        getBoolean(
          Keys.ASHMEM_MEMFD, true
        )
      )
    }

    var sysvipc by remember {
      mutableStateOf(
        getBoolean(
          Keys.SYSVIPC, true
        )
      )
    }

    var killOnExit by remember {
      mutableStateOf(
        getBoolean(
          Keys.KILL_ON_EXIT, true
        )
      )
    }



    PreferenceCategory(label = stringResource(id = R.string.fail_safe),
      description = stringResource(id = R.string.failsafe_desc),
      iconResource = R.drawable.android,
      onNavigate = {
        failSafe = !failSafe
        SettingsData.setBoolean(Keys.FAIL_SAFE, failSafe)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = failSafe,
          onCheckedChange = null
        )
      })


    PreferenceCategory(label = stringResource(id = R.string.Lshell),
      description = stringResource(id = R.string.Lshell_desc),
      iconResource = R.drawable.terminal,
      onNavigate = {
        val view = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val edittext = view.findViewById<EditText>(R.id.name).apply {
          hint = "eg. /bin/sh"
          setText(
            SettingsData.getString(Keys.SHELL, "/bin/sh")
              .removePrefix(File(context.filesDir.parentFile, "rootfs/").absolutePath)
          )
        }
        MaterialAlertDialogBuilder(context).setTitle(rkUtils.getString(R.string.Lshell))
          .setView(view).setNegativeButton(rkUtils.getString(R.string.cancel), null)
          .setPositiveButton(rkUtils.getString(R.string.apply)) { _, _ ->
            val shell = edittext.text.toString()

            if (shell.isEmpty()) {
              return@setPositiveButton
            }

            //verify shell exists
            val absoluteShell = File(context.filesDir.parentFile, "rootfs/$shell")
            if (absoluteShell.exists().not() && !Files.isSymbolicLink(absoluteShell.toPath())) {
              rkUtils.toast(rkUtils.getString(R.string.file_exist_not))
              return@setPositiveButton
            }
            SettingsData.setString(Keys.SHELL, absoluteShell.absolutePath)
            Shell.SH.run("echo \"$shell\" > ${context.filesDir.parentFile!!.absolutePath}/shell")
          }.show()
      })


    PreferenceCategory(label = stringResource(id = R.string.terminal_text_size),
      description = stringResource(id = R.string.terminal_text_size_desc),
      iconResource = R.drawable.terminal,
      onNavigate = {

        val view = LayoutInflater.from(context).inflate(R.layout.popup_new, null)
        val edittext = view.findViewById<EditText>(R.id.name).apply {
          hint = rkUtils.getString(R.string.terminal_text_size)
          setText(SettingsData.getString(Keys.TERMINAL_TEXT_SIZE, "14"))
          inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        MaterialAlertDialogBuilder(context).setTitle(rkUtils.getString(R.string.text_size))
          .setView(view).setNegativeButton(rkUtils.getString(R.string.cancel), null)
          .setPositiveButton(rkUtils.getString(R.string.apply)) { _, _ ->
            val text = edittext.text.toString()
            for (c in text) {
              if (!c.isDigit()) {
                rkUtils.toast(rkUtils.getString(R.string.inavalid_v))
                return@setPositiveButton
              }
            }
            if (text.toInt() > 32) {
              rkUtils.toast(rkUtils.getString(R.string.v_large))
              return@setPositiveButton
            }

            if (text.toInt() < 8) {
              rkUtils.toast(rkUtils.getString(R.string.v_small))
              return@setPositiveButton
            }
            SettingsData.setString(Keys.TERMINAL_TEXT_SIZE, text)

          }.show()


      })


    PreferenceCategory(label = stringResource(id = R.string.useCtrlWorkaround),
      description = stringResource(id = R.string.useCtrlWorkaround_desc),
      iconResource = R.drawable.terminal,
      onNavigate = {
        ctrlWorkAround = !ctrlWorkAround
        SettingsData.setBoolean(Keys.CTRL_WORKAROUND, ctrlWorkAround)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = ctrlWorkAround,
          onCheckedChange = null
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.force_char),
      description = stringResource(id = R.string.force_char_desc),
      iconResource = R.drawable.edit,
      onNavigate = {
        forceChar = !forceChar
        SettingsData.setBoolean(Keys.FORCE_CHAR, forceChar)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = forceChar,
          onCheckedChange = null
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.sim_hard_links),
      description = stringResource(id = R.string.sim_hard_links_desc),
      iconResource = R.drawable.terminal,
      onNavigate = {
        link2sym = !link2sym
        SettingsData.setBoolean(Keys.LINK2SYMLINK, link2sym)
        updateProotArgs(context)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = link2sym,
          onCheckedChange = null
        )
      })


    PreferenceCategory(label = "Ashmem_memfd",
      description = stringResource(id = R.string.sim_ashmem),
      iconResource = R.drawable.terminal,
      onNavigate = {
        ashememFd = !ashememFd
        SettingsData.setBoolean(Keys.ASHMEM_MEMFD, ashememFd)
        updateProotArgs(context)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = ashememFd,
          onCheckedChange = null
        )
      })

    PreferenceCategory(label = "Sysvipc",
      description = stringResource(id = R.string.sysvipc),
      iconResource = R.drawable.terminal,
      onNavigate = {
        sysvipc = !sysvipc
        SettingsData.setBoolean(Keys.SYSVIPC, sysvipc)
        updateProotArgs(context)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = sysvipc,
          onCheckedChange = null
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.kill_on_exit),
      description = stringResource(id = R.string.kill_on_exit_desc),
      iconResource = R.drawable.terminal,
      onNavigate = {
        killOnExit = !killOnExit
        SettingsData.setBoolean(Keys.KILL_ON_EXIT, killOnExit)
        updateProotArgs(context)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = killOnExit,
          onCheckedChange = null
        )
      })


  }
}
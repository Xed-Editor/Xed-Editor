package com.rk.xededitor.ui.screens.settings.editor

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.xededitor.BaseActivity.Companion.getActivity
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.getString
import com.rk.xededitor.settings.Keys
import com.rk.xededitor.settings.SettingsData
import de.Maxr1998.modernpreferences.helpers.onCheckedChange
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.category.PreferenceCategory

@Composable
fun SettingsEditorScreen() {
  PreferenceLayout(
    label = stringResource(id = R.string.editor),
    backArrowVisible = true,
  ) {

    var smoothTabs by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.VIEWPAGER_SMOOTH_SCROLL, true
        )
      )
    }
    var wordwrap by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.WORD_WRAP_ENABLED, false
        )
      )
    }
    var drawerLock by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.KEEP_DRAWER_LOCKED, false
        )
      )
    }
    var diagonalScroll by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.DIAGONAL_SCROLL, false
        )
      )
    }
    var cursorAnimation by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.CURSOR_ANIMATION_ENABLED, true
        )
      )
    }
    var showLineNumber by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.SHOW_LINE_NUMBERS, true
        )
      )
    }
    var pinLineNumber by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.PIN_LINE_NUMBER, false
        )
      )
    }
    var showArrowKeys by remember {
      mutableStateOf(
        SettingsData.getBoolean(
          Keys.SHOW_ARROW_KEYS, false
        )
      )
    }

    PreferenceCategory(label = stringResource(id = R.string.smooth_tabs),
      description = stringResource(id = R.string.smooth_tab_desc),
      iconResource = R.drawable.animation,
      onNavigate = {
        smoothTabs = !smoothTabs
        SettingsData.setBoolean(Keys.VIEWPAGER_SMOOTH_SCROLL, smoothTabs)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = smoothTabs,
          onCheckedChange = {
            MainActivity.activityRef.get()?.smoothTabs = it
          }
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.ww),
      description = stringResource(id = R.string.ww_desc),
      iconResource = R.drawable.reorder,
      onNavigate = {
        wordwrap = !wordwrap
        SettingsData.setBoolean(Keys.VIEWPAGER_SMOOTH_SCROLL, wordwrap)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = wordwrap,
          onCheckedChange = { isChecked ->
            getActivity(MainActivity::class.java)?.let {
              (it as MainActivity).adapter.tabFragments.forEach { f ->
                f.value.get()?.editor?.isWordwrap = isChecked
              }
            }
          }
        )
      })



    PreferenceCategory(label = stringResource(id = R.string.keepdl),
      description = stringResource(id = R.string.drawer_lock_desc),
      iconResource = R.drawable.lock,
      onNavigate = {
        drawerLock = !drawerLock
        SettingsData.setBoolean(Keys.KEEP_DRAWER_LOCKED, drawerLock)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = drawerLock,
          onCheckedChange = null
        )
      })


    PreferenceCategory(label = stringResource(id = R.string.diagonal_scroll),
      description = stringResource(id = R.string.diagonal_scroll_desc),
      iconResource = R.drawable.diagonal_scroll,
      onNavigate = {
        diagonalScroll = !diagonalScroll
        SettingsData.setBoolean(Keys.DIAGONAL_SCROLL, diagonalScroll)
        rkUtils.toast(getString(R.string.rr))
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = diagonalScroll,
          onCheckedChange = null
        )
      })



    PreferenceCategory(label = stringResource(id = R.string.cursor_anim),
      description = stringResource(id = R.string.cursor_anim_desc),
      iconResource = R.drawable.animation,
      onNavigate = {
        cursorAnimation = !cursorAnimation
        SettingsData.setBoolean(Keys.CURSOR_ANIMATION_ENABLED, cursorAnimation)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = cursorAnimation,
          onCheckedChange = { isChecked ->
            getActivity(MainActivity::class.java)?.let {
              (it as MainActivity).adapter.tabFragments.forEach { f ->
                f.value.get()?.editor?.isCursorAnimationEnabled = isChecked
              }
            }
          }
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.show_line_number),
      description = stringResource(id = R.string.show_line_number),
      iconResource = R.drawable.linenumbers,
      onNavigate = {
        showLineNumber = !showLineNumber
        SettingsData.setBoolean(Keys.CURSOR_ANIMATION_ENABLED, showLineNumber)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = showLineNumber,
          onCheckedChange = { isChecked ->
            getActivity(MainActivity::class.java)?.let {
              (it as MainActivity).adapter.tabFragments.forEach { f ->
                f.value.get()?.editor?.isLineNumberEnabled = isChecked
              }
            }
          }
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.pin_line_number),
      description = stringResource(id = R.string.pin_line_number),
      iconResource = R.drawable.linenumbers,
      onNavigate = {
        pinLineNumber = !pinLineNumber
        SettingsData.setBoolean(Keys.PIN_LINE_NUMBER, pinLineNumber)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = pinLineNumber,
          onCheckedChange = { isChecked ->
            getActivity(MainActivity::class.java)?.let {
              (it as MainActivity).adapter.tabFragments.forEach { f ->
                f.value.get()?.editor?.setPinLineNumber(isChecked)
              }
            }
          }
        )
      })

    PreferenceCategory(label = stringResource(id = R.string.extra_keys),
      description = stringResource(id = R.string.extra_keys_desc),
      iconResource = R.drawable.double_arrows,
      onNavigate = {
        showArrowKeys = !showArrowKeys
        SettingsData.setBoolean(Keys.SHOW_ARROW_KEYS, showArrowKeys)
      },
      endWidget = {
        Switch(
          modifier = Modifier
            .padding(12.dp)
            .height(24.dp),
          checked = showArrowKeys,
          onCheckedChange = null
        )
      })


  }
}
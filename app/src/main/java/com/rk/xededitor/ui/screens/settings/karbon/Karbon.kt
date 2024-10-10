package com.rk.xededitor.ui.screens.settings.karbon

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import com.rk.xededitor.R
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate

@Composable
fun AboutKarbon() {
  
  val packageInfo = LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0)
  val versionName = packageInfo.versionName
  val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
  val context = LocalContext.current
  
  PreferenceLayout(
    label = stringResource(id = R.string.app_name),
    backArrowVisible = true,
  ) {
    
    PreferenceGroup(heading = stringResource(R.string.app_name)) {
      PreferenceTemplate(
        title = {
          Text(
            text = "Version",
            style = MaterialTheme.typography.titleMedium
          )
        },
        description = {
          Text(
            text = versionName,
            style = MaterialTheme.typography.titleSmall
          )
        },
      )
      
      PreferenceTemplate(
        title = {
          Text(
            text = "Version Code",
            style = MaterialTheme.typography.titleMedium
          )
        },
        description = {
          Text(
            text = versionCode.toString(),
            style = MaterialTheme.typography.titleSmall
          )
        },
      )
      
      PreferenceTemplate(
        title = {
          Text(
            text = "Github",
            style = MaterialTheme.typography.titleMedium
          )
        },
        description = {
          Text(
            text = "Karbon is open source",
            style = MaterialTheme.typography.titleSmall
          )
        }, endWidget = {
          Button(onClick = {
            val url = "https://github.com/Xed-Editor/Xed-Editor"
            val intent = Intent(Intent.ACTION_VIEW).apply {
              data = Uri.parse(url)
            }
            context.startActivity(intent)
            
          }) { Text("Github") }
        }
      )
      
      PreferenceTemplate(
        title = {
          Text(
            text = "Telegram Group",
            style = MaterialTheme.typography.titleMedium
          )
        },
        description = {
          Text(
            text = "Join Telegram community",
            style = MaterialTheme.typography.titleSmall
          )
        }, endWidget = {
          Button(onClick = {
            val url = "https://t.me/Xed_Editor"
            val intent = Intent(Intent.ACTION_VIEW).apply {
              data = Uri.parse(url)
            }
            context.startActivity(intent)
            
          }) { Text("Join") }
        }
      )
      
      
    }
  }
}

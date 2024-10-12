package com.rk.xededitor.ui.screens.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.PackageInfoCompat
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.R
import com.rk.xededitor.update.UpdateManager
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout
import org.robok.engine.core.components.compose.preferences.base.PreferenceTemplate

@Composable
fun AboutScreen() {

    val packageInfo =
        LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val context = LocalContext.current

    PreferenceLayout(label = stringResource(id = R.string.about), backArrowVisible = true) {
        PreferenceGroup(heading = stringResource(R.string.app_name)) {
            PreferenceTemplate(
                title = { Text(text = stringResource(id = R.string.version), style = MaterialTheme.typography.titleMedium) },
                description = {
                    Text(text = versionName, style = MaterialTheme.typography.titleSmall)
                },
            )

            PreferenceTemplate(
                title = {
                    Text(text = stringResource(id = R.string.version_code), style = MaterialTheme.typography.titleMedium)
                },
                description = {
                    Text(text = versionCode.toString(), style = MaterialTheme.typography.titleSmall)
                },
            )
            
            
            PreferenceTemplate(
                title = { Text(text = stringResource(id = R.string.GitCommit), style = MaterialTheme.typography.titleMedium) },
                description = {
                    Text(
                        text = BuildConfig.GIT_SHORT_COMMIT_HASH,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )

            PreferenceTemplate(
                title = { Text(text = stringResource(id = R.string.github), style = MaterialTheme.typography.titleMedium) },
                description = {
                    Text(
                        text = stringResource(id = R.string.github_desc),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                endWidget = {
                    Button(
                        onClick = {
                            val url = "https://github.com/Xed-Editor/Xed-Editor"
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(id = R.string.github))
                    }
                },
            )
            
            

            PreferenceTemplate(
                title = {
                    Text(text = stringResource(id = R.string.telegram), style = MaterialTheme.typography.titleMedium)
                },
                description = {
                    Text(
                        text = stringResource(id = R.string.telegram_desc),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                endWidget = {
                    Button(
                        onClick = {
                            val url = "https://t.me/Xed_Editor"
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(id = R.string.join))
                    }
                },
            )
        }
    }
}

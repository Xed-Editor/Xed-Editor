package com.rk.xededitor.ui.screens.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.libcommons.isFdroid
import com.rk.resources.strings
import com.rk.xededitor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun AboutScreen() {
    val packageInfo =
        LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val context = LocalContext.current

    PreferenceLayout(label = stringResource(id = strings.about), backArrowVisible = true) {
        PreferenceGroup(heading = "BuildInfo") {
            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = strings.version),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(text = versionName.toString(), style = MaterialTheme.typography.titleSmall)
                },
            )

            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = strings.version_code),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(text = versionCode.toString(), style = MaterialTheme.typography.titleSmall)
                },
            )


            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = strings.git_commit),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = BuildConfig.GIT_SHORT_COMMIT_HASH,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )

            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(strings.flavor),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = if (isFdroid) "FDroid" else "PlayStore",
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }


        PreferenceGroup(heading = "Support") {

            val stars = remember { mutableStateOf("Unknown") }

            LaunchedEffect(Unit) {
                val client = OkHttpClient()
                val url = "https://api.github.com/repos/Xed-Editor/Xed-Editor"
                val request = Request.Builder().url(url).build()

                withContext(Dispatchers.IO) {
                    try {
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val jsonBody = response.body?.string()
                                    ?: throw RuntimeException("Empty response body")
                                val json = JSONObject(jsonBody)
                                val count = json.getInt("stargazers_count")

                                withContext(Dispatchers.Main) {
                                    stars.value = count.toString()
                                }
                            } else {
                                stars.value = "Error"
                            }
                        }
                    } catch (e: Exception) {
                        stars.value = e.message ?: "API Error"
                    }
                }
            }


            PreferenceTemplate(
                title = {
                    Text(
                        text = "Github StarGazers",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = stars.value,
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )

            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = strings.github),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = stringResource(id = strings.github_desc),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                endWidget = {
                    Button(
                        onClick = {
                            val url = "https://github.com/Xed-Editor/Xed-Editor"
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(id = strings.github))
                    }
                },
            )

            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = strings.telegram),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = stringResource(id = strings.telegram_desc),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                endWidget = {
                    Button(
                        onClick = {
                            val url = "https://t.me/XedEditor"
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(id = strings.join))
                    }
                },
            )


            PreferenceTemplate(
                title = {
                    Text(
                        text = stringResource(id = strings.sponsor),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = stringResource(id = strings.sponsor_desc),
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
                endWidget = {
                    Button(
                        onClick = {
                            val url = "https://github.com/sponsors/RohitKushvaha01"
                            val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(id = strings.sponsor))
                    }
                },
            )

        }
    }
}

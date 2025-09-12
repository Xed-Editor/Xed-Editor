package com.rk.xededitor.ui.screens.settings.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.rk.App
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.libcommons.copyToClipboard
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.core.net.toUri

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen() {
    val packageInfo =
        LocalContext.current.packageManager.getPackageInfo(LocalContext.current.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    val context = LocalContext.current

    PreferenceLayout(label = stringResource(id = strings.about), backArrowVisible = true) {

        PreferenceGroup(heading = "Developer") {
            SettingsToggle(
                label = "RohitKushvaha01",
                description = stringResource(strings.view_github_profile),
                default = false,
                sideEffect = {
                    val url = "https://github.com/RohitKushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                },
                showSwitch = false,
                startWidget = {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://github.com/RohitKushvaha01.png")
                            .crossfade(true)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = "GitHub Avatar",
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                    )

                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            )

        }

        PreferenceGroup(heading = "BuildInfo") {
            PreferenceTemplate(
                modifier = Modifier.combinedClickable(enabled = true, onClick = {}, onLongClick = {
                    copyToClipboard(versionName.toString())
                }),
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
                modifier = Modifier.combinedClickable(enabled = true, onClick = {}, onLongClick = {
                    copyToClipboard(versionCode.toString())
                }),
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
                modifier = Modifier.combinedClickable(enabled = true, onClick = {}, onLongClick = {
                    copyToClipboard(BuildConfig.GIT_SHORT_COMMIT_HASH)
                }),
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
                modifier = Modifier.combinedClickable(enabled = true, onClick = {}, onLongClick = {
                    copyToClipboard(if (App.isFDroid) "FDroid" else "PlayStore")
                }),
                title = {
                    Text(
                        text = stringResource(strings.flavor),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                description = {
                    Text(
                        text = if (App.isFDroid) "FDroid" else "PlayStore",
                        style = MaterialTheme.typography.titleSmall,
                    )
                },
            )
        }


        PreferenceGroup(heading = "Community") {

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
                        text = "Github Stars",
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

            SettingsToggle(
                label = stringResource(id = strings.github),
                description = stringResource(id = strings.github_desc),
                isEnabled = true,
                showSwitch = false,
                default = false,
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    val url = "https://github.com/Xed-Editor/Xed-Editor"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = url.toUri() }
                    context.startActivity(intent)
                }
            )

            SettingsToggle(
                label = stringResource(id = strings.telegram),
                description = stringResource(id = strings.telegram_desc),
                isEnabled = true,
                showSwitch = false,
                default = false,
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    val url = "https://t.me/XedEditor"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = url.toUri() }
                    context.startActivity(intent)
                }
            )

            SettingsToggle(
                label = stringResource(id = strings.discord),
                description = stringResource(id = strings.telegram_desc),
                isEnabled = true,
                showSwitch = false,
                default = false,
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    val url = "https://discord.gg/6bKzcQRuef"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = url.toUri() }
                    context.startActivity(intent)
                }
            )

        }
    }
}

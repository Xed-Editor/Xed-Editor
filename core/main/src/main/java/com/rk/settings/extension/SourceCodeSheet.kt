package com.rk.settings.extension

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.extension.Extension
import com.rk.resources.drawables
import com.rk.resources.fillPlaceholders
import com.rk.resources.strings
import com.rk.theme.Typography
import java.net.URL

enum class SourceCodeProvider(val drawableRes: Int, val viewStringRes: Int) {
    GitHub(drawables.github, strings.view_github),
    GitLab(drawables.gitlab, strings.view_gitlab),
    BitBucket(drawables.bitbucket, strings.view_bitbucket),
    Other(drawables.xml, strings.view_repo);

    companion object {
        fun fromUrl(url: String): SourceCodeProvider {
            val hostName = URL(url).host
            return when (hostName) {
                "github.com" -> GitHub
                "gitlab.com" -> GitLab
                "bitbucket.org" -> BitBucket
                else -> Other
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceCodeSheet(extension: Extension, onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val sourceCodeProvider = SourceCodeProvider.fromUrl(extension.repository)

    ModalBottomSheet(onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    painter = painterResource(drawables.xml),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )

                Text(
                    text = stringResource(strings.source_code),
                    style = Typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val sheetDescription =
                extension.license?.let { stringResource(strings.ext_source_desc_license).fillPlaceholders(it) }
                    ?: stringResource(strings.ext_source_desc)
            Text(sheetDescription, modifier = Modifier.padding(horizontal = 16.dp))

            PreferenceGroup {
                SettingsToggle(
                    label = stringResource(sourceCodeProvider.viewStringRes),
                    description = extension.repository,
                    isEnabled = true,
                    showSwitch = false,
                    default = false,
                    startWidget = {
                        Icon(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            painter = painterResource(sourceCodeProvider.drawableRes),
                            contentDescription = null,
                        )
                    },
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            painter = painterResource(drawables.open_in_new),
                            contentDescription = null,
                        )
                    },
                    sideEffect = {
                        val intent = Intent(Intent.ACTION_VIEW, extension.repository.toUri())
                        context.startActivity(intent)
                    },
                )
            }
        }
    }
}

package com.rk.settings.account

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rk.account.AccountConfig
import com.rk.account.AccountManager
import com.rk.account.AccountSession
import com.rk.account.AccountUser
import com.rk.components.SettingsItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.utils.copyToClipboard
import com.rk.utils.toast
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun AccountScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by AccountManager.session.collectAsState()
    val signInState by AccountManager.signInState.collectAsState()
    var confirmSignOut by remember { mutableStateOf(false) }

    PreferenceLayout(label = stringResource(strings.account)) {
        val current = session
        if (current == null) {
            SignedOutSection(
                state = signInState,
                onSignIn = {
                    scope.launch {
                        AccountManager.startSignIn()
                            .onSuccess { url -> openInBrowser(context, url) }
                            .onFailure { toast(it.message ?: strings.account_sign_in_failed.getString()) }
                    }
                },
                onCancel = { AccountManager.cancelSignIn() },
            )
        } else {
            SignedInSection(session = current, onSignOut = { confirmSignOut = true })
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(strings.account_sign_out_confirm_title)) },
            text = { Text(stringResource(strings.account_sign_out_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        scope.launch {
                            AccountManager.signOut()
                            toast(strings.account_sign_out_success)
                        }
                    }
                ) {
                    Text(stringResource(strings.account_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(strings.cancel)) }
            },
        )
    }
}

@Composable
private fun SignedOutSection(
    state: AccountManager.SignInState,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(drawables.person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(strings.account_sign_in_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(strings.account_sign_in_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            is AccountManager.SignInState.AwaitingBrowser -> {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(strings.account_waiting_browser),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(strings.account_waiting_browser_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onCancel) { Text(stringResource(strings.cancel)) }
            }

            is AccountManager.SignInState.Failed -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(strings.retry))
                }
            }

            else -> {
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(strings.account_sign_in_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(strings.account_sign_in_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SignedInSection(session: AccountSession, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val user = session.user

    ProfileHeader(user = user)

    PreferenceGroup(heading = stringResource(strings.account_details)) {
        CopyableRow(
            label = stringResource(strings.account_id),
            value = user.id,
        )
        SettingsItem(
            label = stringResource(strings.email),
            description = user.email.ifEmpty { stringResource(strings.account_no_email) },
            showSwitch = false,
            default = false,
            endWidget = {
                Text(
                    text =
                        stringResource(
                            if (user.emailVerified) {
                                strings.account_email_verified
                            } else {
                                strings.account_email_unverified
                            }
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp),
                )
            },
        )
        SettingsItem(
            label = stringResource(strings.account_signed_in_since),
            description = remember(session.signedInAt) { formatTimestamp(session.signedInAt) },
            showSwitch = false,
            default = false,
        )
    }

    PreferenceGroup {
        SettingsItem(
            label = stringResource(strings.account_manage),
            description = stringResource(strings.account_manage_desc),
            showSwitch = false,
            default = false,
            sideEffect = { openInBrowser(context, AccountConfig.DASHBOARD_URL) },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(strings.account_sign_out))
        }
    }
}

@Composable
private fun ProfileHeader(user: AccountUser) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val avatarModifier = Modifier.size(80.dp).clip(CircleShape)
        if (user.image.isNullOrBlank()) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                    Icon(
                        painter = painterResource(drawables.person),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        } else {
            AsyncImage(
                model = user.image,
                contentDescription = null,
                modifier = avatarModifier,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = user.name?.takeIf { it.isNotBlank() } ?: user.email.ifEmpty { stringResource(strings.account) },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!user.email.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (user.isAdmin) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    text = stringResource(strings.account_administrator),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableRow(label: String, value: String) {
    PreferenceTemplate(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { copyToClipboard(label, value) }),
        title = { Text(text = label, style = MaterialTheme.typography.titleMedium) },
        description = { Text(text = value, style = MaterialTheme.typography.titleSmall) },
    )
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
                .launchUrl(context, url.toUri())
        }
        .onFailure { toast(it.message ?: strings.account_browser_error.getString()) }
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
}

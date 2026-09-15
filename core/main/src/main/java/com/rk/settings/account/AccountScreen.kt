package com.rk.settings.account

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
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
    var showMenu by remember { mutableStateOf(false) }

    val signedIn = session != null

    PreferenceLayout(
        label = stringResource(strings.account),
        verticalArrangement = if (signedIn) Arrangement.spacedBy(8.dp) else Arrangement.Center,
        horizontalAlignment = if (signedIn) Alignment.Start else Alignment.CenterHorizontally,
        actions = {
            if (signedIn){
                IconButton(onClick = {
                    showMenu = true
                }) {
                    Icon(imageVector = Icons.Outlined.MoreVert,contentDescription = null)
                    DropdownMenu(expanded = showMenu && !confirmSignOut, onDismissRequest = {
                        showMenu = false
                    }) {
                        DropdownMenuItem(text = {
                            Text(stringResource(strings.account_sign_out))
                        }, onClick = {
                            confirmSignOut = true
                        }, leadingIcon = {
                            Icon(imageVector = Icons.Outlined.Delete,null)
                        })
                    }
                }
            }

        }
    ) {
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
            SignedInSection(session = current)
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
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
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
fun ProfileIcon(modifier: Modifier = Modifier,user: AccountUser?,avatarSize: Dp) {
    if (user == null || user.image.isNullOrBlank()){
        Surface(shape = CircleShape) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(avatarSize)) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(avatarSize),
                )
            }
        }
    }else{
        SubcomposeAsyncImage(
            model = user.image,
            contentDescription = null,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape),
            loading = {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(avatarSize),
                        )
                    }
                }
            },
        )
    }
}

private fun openInBrowser(context: Context, url: String) {
    runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF).build()
                .launchUrl(context, url.toUri())
        }
        .onFailure { toast(it.message ?: strings.account_browser_error.getString()) }
}

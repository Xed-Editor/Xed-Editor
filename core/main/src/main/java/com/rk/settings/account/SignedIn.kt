package com.rk.settings.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.account.AccountSession
import com.rk.account.AccountUser
import com.rk.components.SettingsItem
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.resources.strings

@Composable
fun SignedInSection(session: AccountSession) {
    val context = LocalContext.current
    val user = session.user

    PreferenceGroup() {
        ProfileCard(user)
    }



    //todo


}

@Composable
fun ProfileCard(user: AccountUser?){
    val avatarSize = 36.dp

    if (user != null){
        SettingsItem(startWidget = {
            Box(modifier = Modifier.padding(start = 16.dp)) {
                ProfileIcon(user = user, avatarSize = avatarSize)
            }
        }, label = user.name?.takeIf { it.isNotBlank() } ?: stringResource(strings.unknown),
            description = user.id,
            showSwitch = false)

    }
}
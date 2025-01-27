package com.rk.xededitor.ui.screens.settings.git

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Patterns
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.libcommons.alpineHomeDir
import com.rk.libcommons.child
import com.rk.libcommons.createFileIfNot
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.PreferencesData
import com.rk.settings.PreferencesKeys
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.toastIt
import com.rk.xededitor.ui.components.InputDialog
import com.rk.xededitor.ui.components.SettingsToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.robok.engine.core.components.compose.preferences.base.PreferenceGroup
import org.robok.engine.core.components.compose.preferences.base.PreferenceLayout

@SuppressLint("AuthLeak")
@Composable
fun SettingsGitScreen() {
    val context = LocalContext.current
    val activity = LocalActivity.current

    var isNotGithub by remember { mutableStateOf(true) }

    var username by remember { mutableStateOf("root") }
    var email by remember { mutableStateOf("example@mail.com") }
    var token by remember { mutableStateOf("") }
    var gitUrl by remember { mutableStateOf("github.com") }
    var isLoading by remember { mutableStateOf(true) }


    var showEmailDialog by remember { mutableStateOf(false) }
    var showUserNameDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showGithubUrlDialog by remember { mutableStateOf(false) }

    var inputEmail by remember { mutableStateOf("") }
    var inputUserName by remember { mutableStateOf("") }
    var inputToken by remember { mutableStateOf("") }
    var inputGitUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val gitConfig = loadGitConfig(context)
        username = gitConfig.first
        email = gitConfig.second
        token = getToken(context)

        inputEmail = gitConfig.second
        inputUserName = gitConfig.first
        inputToken = token

        isLoading = false
    }

    PreferenceLayout(label = stringResource(id = strings.git), backArrowVisible = true) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {

            PreferenceGroup {
                SettingsToggle(
                    label = stringResource(strings.github),
                    description = stringResource(strings.use_github_url),
                    default = true,
                    key = PreferencesKeys.NOTGITHUB,
                    sideEffect = {
                        isNotGithub = it.not()
                    }
                )

                SettingsToggle(label = stringResource(strings.user),
                    description = username,
                    showSwitch = false,
                    sideEffect = {
                        showUserNameDialog = true
                    })

                SettingsToggle(label = stringResource(strings.email),
                    description = email,
                    showSwitch = false,
                    sideEffect = {
                        showEmailDialog = true
                    })

                SettingsToggle(label = stringResource(strings.git_auth),
                    description = stringResource(strings.git_auth),
                    showSwitch = false,
                    sideEffect = {
                        showTokenDialog = true
                    })

                if (isNotGithub){
                    SettingsToggle(
                        label = stringResource(strings.custom_git_url),
                        description = gitUrl,
                        showSwitch = false,
                        sideEffect = {
                            showGithubUrlDialog = true
                        }
                    )
                }
            }





            if (showGithubUrlDialog){
                InputDialog(
                    title = stringResource(strings.custom_git_url),
                    inputLabel = "github.com",
                    inputValue = inputGitUrl,
                    onInputValueChange = { text ->
                        inputGitUrl = text
                    },
                    onConfirm = {
                        runCatching {
                            updateConfig(context, username,inputGitUrl)
                            gitUrl = inputGitUrl
                            PreferencesData.setString(PreferencesKeys.GIT_URL,gitUrl)
                            updateCredentials(context,username,token,inputGitUrl)
                        }.onFailure { rkUtils.toast(it.message) }
                        showGithubUrlDialog = false
                    },
                    onDismiss = {
                        showGithubUrlDialog = false
                        inputGitUrl = gitUrl
                    },
                )
            }

            if (showEmailDialog) {
                InputDialog(
                    title = stringResource(strings.email),
                    inputLabel = "example@email.com",
                    inputValue = inputEmail,
                    onInputValueChange = { text ->
                        inputEmail = text
                    },
                    onConfirm = {
                        runCatching {
                            if (isValidEmail(inputEmail)) {
                                updateConfig(context, username, inputEmail)
                                email = inputEmail
                            } else {
                                inputEmail = email
                                rkUtils.toast(strings.invalid_email.getString())
                            }
                        }.onFailure { rkUtils.toast(it.message) }
                        showEmailDialog = false
                    },
                    onDismiss = {
                        showEmailDialog = false
                        inputEmail = email
                    },
                )
            }

            if (showUserNameDialog) {
                InputDialog(
                    title = stringResource(strings.user),
                    inputLabel = stringResource(strings.user),
                    inputValue = inputUserName,
                    onInputValueChange = { text ->
                        inputUserName = text
                    },
                    onConfirm = {
                        runCatching {
                            if (username.contains(" ").not()) {
                                updateConfig(context, inputUserName, email)
                                username = inputUserName
                            } else {
                                inputUserName = username
                                rkUtils.toast(strings.invalid_user.getString())
                            }

                        }.onFailure { rkUtils.toast(it.message) }

                        showUserNameDialog = false
                    },
                    onDismiss = {
                        showUserNameDialog = false
                        inputUserName = username
                    },
                )
            }

            if (showTokenDialog) {
                activity?.window?.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
                )


                InputDialog(
                    title = stringResource(strings.git_auth),
                    inputLabel = stringResource(strings.git_auth),
                    inputValue = inputToken,
                    onInputValueChange = { text ->
                        inputToken = text
                    },
                    onConfirm = {
                        runCatching {
                            if (inputToken.isBlank().not()){
                                updateCredentials(context, username, inputToken,gitUrl)
                                token = inputToken
                            }
                        }.onFailure { it.message.toastIt() }
                        showTokenDialog = false
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    },
                    onDismiss = {
                        showTokenDialog = false
                        inputToken = token
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    },
                )

            }
        }
    }
}

suspend fun loadGitConfig(context: Context): Pair<String, String> {
    return withContext(Dispatchers.IO) {
        val config = alpineHomeDir().child(".gitconfig")
        if (config.exists()) {
            runCatching {
                val text = config.readText()
                val matchResult =
                    Regex("""\[user]\s*name\s*=\s*(\S+)\s*email\s*=\s*(\S+)""").find(text)
                val name = matchResult?.groupValues?.get(1) ?: "root"
                val email = matchResult?.groupValues?.get(2) ?: "example@mail.com"
                return@withContext Pair(name, email)
            }.getOrElse {
                Pair("root", "example@mail.com")
            }
        } else {
            Pair("root", "example@mail.com")
        }
    }
}

private fun updateConfig(context: Context, username: String, email: String) {
    val config = alpineHomeDir().child(".gitconfig").createFileIfNot()
    config.writeText(
        """[user]
 name = $username
 email = $email
[color]
 ui = true
 status = true
 branch = true
 diff = true
 interactive = true
[credential]
 helper = store
"""
    )
}

private fun updateCredentials(context: Context, username: String, token: String,gitUrl:String) {
    val cred = alpineHomeDir().child(".git-credentials").createFileIfNot()
    cred.writeText("https://$username:$token@$gitUrl")
}

private inline fun isValidEmail(email: String): Boolean {
    return Patterns.EMAIL_ADDRESS.matcher(email).matches()
}

suspend fun getToken(context: Context): String {
    return withContext(Dispatchers.IO) {
        val gitUrl = PreferencesData.getString(PreferencesKeys.GIT_URL,"github.com")
        val cred = alpineHomeDir().child(".git-credentials")
        if (cred.exists()) {
            val regex = """https://([^:]+):([^@]+)@$gitUrl""".toRegex()
            val matchResult = regex.find(cred.readText())
            return@withContext matchResult?.groupValues?.get(2) ?: ""
        }
        ""
    }
}

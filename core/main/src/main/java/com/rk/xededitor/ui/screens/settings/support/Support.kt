package com.rk.xededitor.ui.screens.settings.support

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.crashhandler.CrashActivity
import com.rk.libcommons.application
import com.rk.libcommons.dialog
import com.rk.libcommons.toast
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.xededitor.ui.activities.main.MainActivity
import com.rk.xededitor.ui.activities.settings.SettingsActivity
import com.rk.xededitor.ui.activities.settings.SettingsRoutes
import com.rk.xededitor.ui.activities.settings.settingsNavController
import com.rk.xededitor.ui.components.SettingsToggle
import com.rk.xededitor.ui.screens.settings.HeartbeatIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun Support(modifier: Modifier = Modifier) {
    PreferenceLayout(label = "Support", backArrowVisible = true) {
        val context = LocalContext.current

        PreferenceGroup {
            SettingsToggle(
                label = "GitHub Sponsor",
                description = null,
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.github),
                        contentDescription = null
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    val url = "https://github.com/sponsors/RohitKushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                    Settings.donated = true
                }
            )
            SettingsToggle(
                label = "Buy Me a Coffee",
                description = null,
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.coffee_24px),
                        contentDescription = null
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    val url = "https://buymeacoffee.com/rohitkushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                    Settings.donated = true
                }
            )
            SettingsToggle(
                label = "UPI",
                description = null,
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.upi_pay_24px),
                        contentDescription = null
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                sideEffect = {
                    val uri = Uri.parse("upi://pay").buildUpon()
                        .appendQueryParameter("pa", "rohitkushvaha01@axl")
                        .appendQueryParameter("pn", "Rohit Kushwaha")
                        .appendQueryParameter("tn", "Xed-Editor")
                        .appendQueryParameter("cu", "INR")
                        .build()
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = uri
                    }

                    val chooser = Intent.createChooser(intent, "Use")
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(chooser)
                        Settings.donated = true
                    } else {
                        toast("No UPI app found")
                    }
                }
            )
        }

    }
}

fun MainActivity.handleSupport(){
    lifecycleScope.launch(Dispatchers.Main) {
        if (Settings.visits > 300) {
            dialog(
                context = this@handleSupport,
                title = "Quick Question",
                msg = "Are you enjoying Xed-Editor so far?",
                okString = strings.yes,
                cancelString = strings.no,
                onCancel = {
                    Settings.visits = 0
                    dialog(
                        context = this@handleSupport,
                        title = "We’d Love Your Feedback",
                        msg = "Feel free to share your thoughts in our Telegram group or GitHub repository!",
                        onOk = {}
                    )
                },
                onOk = {
                    Settings.visits = 0
                    dialog(
                        context = this@handleSupport,
                        title = "Support the Project",
                        msg = "Would you like to support development of Xed-Editor and help it grow?",
                        okString = strings.yes,
                        cancelString = strings.no,
                        onCancel = {},
                        onOk = {
                            val intent = Intent(application!!, SettingsActivity::class.java)
                            intent.putExtra("route", SettingsRoutes.Support.route)
                            startActivity(intent)
                        }
                    )
                }
            )
        }

    }

}

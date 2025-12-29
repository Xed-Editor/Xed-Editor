package com.rk.settings.support

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.dialog
import com.rk.utils.isDialogShowing
import com.rk.utils.toast

fun isUPISupported(context: Context): Boolean {
    // 1. Check if the user's region is India (Most reliable indicator for UPI)
    val currentLocale = context.resources.configuration.locales[0]
    val isIndia = currentLocale.country.equals("IN", ignoreCase = true)

    // 2. Check if there is at least one app capable of handling a UPI URI
    val uri = Uri.parse("upi://pay")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val packageManager = context.packageManager

    // Check if any app can resolve this intent
    val canHandleUPI =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
                .isNotEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }

    return isIndia || canHandleUPI
}

@Composable
fun Support(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.support), backArrowVisible = true) {
        val context = LocalContext.current

        PreferenceGroup {
            SettingsToggle(
                label = "GitHub Sponsors",
                description = "Become a sponsor and help shape the future of this project",
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.github),
                        contentDescription = null,
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                sideEffect = {
                    val url = "https://github.com/sponsors/RohitKushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                    Settings.donated = true
                },
            )
            SettingsToggle(
                label = "Buy Me a Coffee",
                description = "Show your love with a coffee - every cup helps! ☕",
                isEnabled = true,
                showSwitch = false,
                default = false,
                startWidget = {
                    Icon(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        painter = painterResource(drawables.coffee),
                        contentDescription = null,
                    )
                },
                endWidget = {
                    Icon(
                        modifier = Modifier.padding(16.dp),
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                    )
                },
                sideEffect = {
                    val url = "https://buymeacoffee.com/rohitkushvaha01"
                    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }
                    context.startActivity(intent)
                    Settings.donated = true
                },
            )
            val upiAvailable = remember { isUPISupported(context) }
            if (upiAvailable) {
                SettingsToggle(
                    label = "UPI",
                    description = "Support directly through your favorite UPI app",
                    isEnabled = true,
                    showSwitch = false,
                    default = false,
                    startWidget = {
                        Icon(
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                            painter = painterResource(drawables.upi_pay),
                            contentDescription = null,
                        )
                    },
                    endWidget = {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    sideEffect = {
                        val uri =
                            "upi://pay"
                                .toUri()
                                .buildUpon()
                                .appendQueryParameter("pa", "rohitkushvaha01@axl")
                                .appendQueryParameter("pn", "Rohit Kushwaha")
                                .appendQueryParameter("tn", "Xed-Editor")
                                .appendQueryParameter("cu", "INR")
                                .build()
                        val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }

                        val chooser = Intent.createChooser(intent, "Use")
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(chooser)
                            Settings.donated = true
                        } else {
                            toast("No UPI app found")
                        }
                    },
                )
            }
        }
    }
}

fun Activity.handleSupport() {
    if (isDialogShowing) return

    val currentTime = System.currentTimeMillis()

    // Don't ask users who explicitly said they don't find value
    if (Settings.user_declined_value) return

    // Don't ask if they already supported
    if (Settings.user_has_supported) return

    // Calculate cooldown based on last response
    val cooldownPeriod =
        when {
            Settings.user_said_maybe_later -> 7L * 24 * 60 * 60 * 1000 // 1 week
            else -> 14L * 24 * 60 * 60 * 1000 // First time or other: 2 weeks
        }

    if (currentTime - Settings.last_donation_dialog_timestamp < cooldownPeriod) {
        return
    }

    // Wait for meaningful engagement
    val totalEngagement = Settings.saves + Settings.runs
    val threshold =
        when (Settings.donation_ask_count) {
            0 -> 80 // First ask: wait for real usage
            1 -> 200 // Second ask: they're a regular user
            else -> 500 // Third+ ask: power user
        }

    if (totalEngagement < threshold) return

    Settings.last_donation_dialog_timestamp = currentTime
    Settings.donation_ask_count++

    showCombinedDonationDialog()
}

private fun Activity.showCombinedDonationDialog() {
    dialog(
        context = this,
        title = strings.enjoying_xed.getString(),
        msg = strings.support_message.getFilledString(Settings.saves.toString(), Settings.runs.toString()),
        okString = strings.yes_support,
        cancelString = strings.not_for_me,
        cancelable = false,
        onCancel = {
            // User doesn't find value - stop asking
            Settings.user_declined_value = true
            Settings.user_said_maybe_later = false
        },
        onOk = {
            // User clicked support
            Settings.user_has_supported = true
            Settings.user_said_maybe_later = false
            val intent =
                Intent(this, SettingsActivity::class.java).apply { putExtra("route", SettingsRoutes.Support.route) }
            startActivity(intent)
        },
    )
}

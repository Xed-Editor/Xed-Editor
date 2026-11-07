package com.rk.account

object AccountConfig {
    const val WEB_BASE = "https://xed-editor.app"

    const val API_BASE = "$WEB_BASE/api"

    const val WEB_CALLBACK_PATH = "/android/auth/callback"

    const val WEB_CALLBACK_URL = "$WEB_BASE$WEB_CALLBACK_PATH"

    const val DEEP_LINK_SCHEME = "xed-editor"

    const val DEEP_LINK_HOST = "auth"

    const val DEEP_LINK_PATH = "/callback"

    const val DEEP_LINK_URL = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST$DEEP_LINK_PATH"

    const val WEB_HOST = "xed-editor.app"

    const val DASHBOARD_URL = "$WEB_BASE/dashboard"
}

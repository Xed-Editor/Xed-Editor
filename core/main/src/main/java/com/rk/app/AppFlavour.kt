package com.rk.app

/**
 * Distribution flavour an APK was built from.
 *
 * The authoritative value is declared once per product flavour in `app/build.gradle.kts` as the
 * `BuildConfig.FLAVOUR` field. Library modules cannot read the application module's `BuildConfig`,
 * so the per-flavour `XedApplication` publishes it here during startup with
 * `AppFlavour.init(BuildConfig.FLAVOUR)`.
 *
 * Until [init] runs (and in previews, unit tests and any variant that forgets to call it) the
 * flavour falls back to [COMMUNITY], which is the default and most fully featured distribution.
 */
enum class AppFlavour(
    /** Stable identifier, must match the `BuildConfig.FLAVOUR` declared in the Gradle build. */
    val id: String,
    /**
     * Whether this distribution bundles the `:features:extensions` module and its store. This is a
     * build-time fact only; store UI must also respect the user's `enable_extension` toggle, i.e.
     * `AppFlavour.current.bundlesExtensions && FeatureRegistry.isEnabled("enable_extension")`.
     */
    val bundlesExtensions: Boolean,
    /** Whether this distribution is published on Google Play. */
    val isPlayStore: Boolean,
) {
    COMMUNITY(id = "community", bundlesExtensions = true, isPlayStore = false),
    PLAYSTORE(id = "playstore", bundlesExtensions = false, isPlayStore = true);

    companion object {
        private val byId = entries.associateBy { it.id.lowercase() }

        /** The flavour this process was built from; [COMMUNITY] until [init] is called. */
        @Volatile
        var current: AppFlavour = COMMUNITY
            private set

        /** Resolves a flavour from its [id]. Unknown ids fall back to [COMMUNITY]. */
        fun fromId(id: String): AppFlavour = byId[id.lowercase()] ?: COMMUNITY

        /**
         * Publishes the flavour declared by the application module. Call this once, before anything
         * that branches on [current], e.g. `AppFlavour.init(BuildConfig.FLAVOUR)`.
         */
        fun init(flavourId: String) {
            current = fromId(flavourId)
        }
    }
}

package de.Maxr1998.modernpreferences.preferences.choice

import androidx.annotation.StringRes
import de.Maxr1998.modernpreferences.helpers.DISABLED_RESOURCE_ID
import de.Maxr1998.modernpreferences.preferences.Badge

/**
 * Represents a selectable item in a selection dialog preference,
 * e.g. the [AbstractSingleChoiceDialogPreference]
 *
 * @param key The key of this item, will be committed to preferences if selected
 */
@Suppress("DataClassPrivateConstructor")
data class SelectionItem<T : Any> private constructor(
    val key: T,
    @StringRes
    val titleRes: Int,
    val title: CharSequence,
    @StringRes
    val summaryRes: Int,
    val summary: CharSequence?,
    val badgeInfo: Badge?,
) {
    /**
     * @see SelectionItem
     */
    constructor(
        key: T,
        @StringRes
        titleRes: Int,
        @StringRes
        summaryRes: Int = DISABLED_RESOURCE_ID,
        badgeInfo: Badge? = null,
    ) : this(
        key = key,
        titleRes = titleRes,
        title = "",
        summaryRes = summaryRes,
        summary = null,
        badgeInfo = badgeInfo,
    )

    /**
     * @see SelectionItem
     */
    constructor(
        key: T,
        title: CharSequence,
        summary: CharSequence? = null,
        badgeInfo: Badge? = null,
    ) : this(
        key = key,
        titleRes = DISABLED_RESOURCE_ID,
        title = title,
        summaryRes = DISABLED_RESOURCE_ID,
        summary = summary,
        badgeInfo = badgeInfo,
    )
}
package com.rk.extension

import com.rk.controlpanel.ControlItem
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.ActionPopup
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment

// A unique ID can be anything as long as it is unique.
typealias ID = String

object Hooks {
    /**
     * This tabs HashMap allows plugins to add custom tabs in Xed Editor.
     * Plugins just need to provide a builder that returns an instance of CoreFragment.
     * If the result is null, the default tab will be opened.
     */

    // ID : Builder
    val tabs: HashMap<ID, (file: FileObject, TabFragment) -> CoreFragment?> = hashMapOf()


    /**
     * This actionPopupHook HashMap allows plugins to add custom action for files in Xed Editor.
     */
    // ID : Builder
    val actionPopupHook = hashMapOf<ID, ActionPopup.(FileAction) -> Unit>()

    val controlItems = hashMapOf<ID, ControlItem>()
}

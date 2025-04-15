package com.rk.extension

import android.content.Context
import com.rk.controlpanel.ControlItem
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.ActionPopup
import com.rk.runner.RunnerImpl
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.ui.screens.settings.feature_toggles.Feature

// A unique ID can be anything as long as it is unique.
typealias ID = String

object Hooks {
    object Editor{
        /**
         * This tabs HashMap allows plugins to add custom tabs in Xed Editor.
         * Plugins just need to provide a builder that returns an instance of CoreFragment.
         * If the result is null, the default tab will be opened.
         */
        val tabs: HashMap<ID, (file: FileObject, TabFragment) -> CoreFragment?> = hashMapOf()

    }

    object FileActions{
        val actionPopupHook = hashMapOf<ID, ActionPopup.(FileAction) -> Unit>()
    }

    object ControlPanel{
        val controlItems = hashMapOf<ID, ControlItem>()
    }

    object Settings{
        val screens = hashMapOf<ID, SettingsScreen>()
        val features = hashMapOf<ID, Feature>()
    }

    object Runner{
        /**
         * provide a builder that returns a runner for a file if the return value is null then the runner wont be shown
         * first lambda in the pair is used to check if the runner is available for the given file the second lambda is used to build the actual runner
         */
        val runners = hashMapOf<ID, Pair<(FileObject)-> Boolean,(FileObject, Context)-> RunnerImpl?>>()
    }

}

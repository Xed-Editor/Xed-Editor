package com.rk.extension

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.rk.controlpanel.ControlItem
import com.rk.file.FileObject
import com.rk.libcommons.ActionPopup
import com.rk.runner.RunnerImpl
import com.rk.xededitor.MainActivity.TabFragment
import com.rk.xededitor.MainActivity.file.FileAction
import com.rk.xededitor.MainActivity.tabs.core.CoreFragment
import com.rk.xededitor.ui.screens.settings.feature_toggles.Feature

// A unique ID can be anything as long as it is unique.
typealias ID = String

object Hooks {
    object Editor {
        /**
         * This tabs SnapshotStateMap allows plugins to add custom tabs in Xed Editor.
         * Plugins just need to provide a builder that returns an instance of CoreFragment.
         * If the result is null, the default tab will be opened.
         */
        val tabs: SnapshotStateMap<ID, (file: FileObject, TabFragment) -> CoreFragment?> = mutableStateMapOf()
        val onTabCreate: SnapshotStateMap<ID, (file: FileObject, TabFragment) -> Unit> = mutableStateMapOf()
        val onTabDestroyed: SnapshotStateMap<ID, (file: FileObject) -> Unit> = mutableStateMapOf()
        val onTabClosed: SnapshotStateMap<ID, (file: FileObject) -> Unit> = mutableStateMapOf()
        val onTabCleared: SnapshotStateMap<ID, () -> Unit> = mutableStateMapOf()
    }

    object FileActions {
        val actionPopupHook: SnapshotStateMap<ID, ActionPopup.(FileAction) -> Unit> = mutableStateMapOf()
    }

    object ControlPanel {
        val controlItems: SnapshotStateMap<ID, ControlItem> = mutableStateMapOf()
    }

    object Settings {
        val screens: SnapshotStateMap<ID, SettingsScreen> = mutableStateMapOf()
        val features: SnapshotStateMap<ID, Feature> = mutableStateMapOf()
    }

    object Runner {
        /**
         * provide a builder that returns a runner for a file if the return value is null then the runner wont be shown
         * first lambda in the pair is used to check if the runner is available for the given file the second lambda is used to build the actual runner
         */
        val runners: SnapshotStateMap<ID, Pair<(FileObject) -> Boolean, (FileObject, Context) -> RunnerImpl?>> = mutableStateMapOf()
    }
}

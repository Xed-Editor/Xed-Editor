package com.rk.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings.Global;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Condition;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.rk.activities.main.MainActivity;
import com.rk.commands.ActionContext;
import com.rk.commands.global.SearchCodeCommand;
import com.rk.components.GlobalDialogs;
import com.rk.file.FileWrapper;
import com.rk.settings.Settings;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProjectSearchKeyboardTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final UiDevice device = UiDevice.getInstance(instrumentation);
    private MainActivity activity;
    private File project;
    private FileWrapper projectRoot;
    private boolean projectAdded;
    private int previousTab = -1;
    private int previousServiceTab = -1;
    private boolean disclaimer;
    private boolean ignoreStoragePermission;
    private boolean smartToolbar;
    private boolean fullscreen;
    private boolean supported;
    private boolean addDialog;
    private boolean fileSearchDialog;
    private boolean codeSearchDialog;

    @Before
    public void setUp() throws Exception {
        instrumentation.runOnMainSync(() -> {
            disclaimer = Settings.INSTANCE.getShown_disclaimer();
            ignoreStoragePermission = Settings.INSTANCE.getIgnore_storage_permission();
            smartToolbar = Settings.INSTANCE.getSmart_toolbar();
            fullscreen = Settings.INSTANCE.getFullscreen();
            supported = Settings.INSTANCE.getUser_has_supported();
            addDialog = GlobalDialogs.INSTANCE.getAddDialog().getValue();
            fileSearchDialog = GlobalDialogs.INSTANCE.getFileSearchDialog().getValue();
            codeSearchDialog = GlobalDialogs.INSTANCE.getCodeSearchDialog().getValue();
            Settings.INSTANCE.setShown_disclaimer(true);
            Settings.INSTANCE.setIgnore_storage_permission(true);
            Settings.INSTANCE.setUser_has_supported(true);
            // Drive visibility explicitly so this regression also runs without a software IME.
            Settings.INSTANCE.setSmart_toolbar(false);
            Settings.INSTANCE.setFullscreen(false);
            GlobalDialogs.INSTANCE.getAddDialog().setValue(false);
            GlobalDialogs.INSTANCE.getFileSearchDialog().setValue(false);
            GlobalDialogs.INSTANCE.getCodeSearchDialog().setValue(false);
        });
        project = Files.createTempDirectory(
                instrumentation.getTargetContext().getCacheDir().toPath(), "project-search-").toFile();
        Files.write(new File(project, "sample.txt").toPath(),
                "keyboard continuity".getBytes(StandardCharsets.UTF_8));
        projectRoot = new FileWrapper(project);
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (MainActivity) instrumentation.startActivitySync(intent);
        assertTrue("Project restoration did not finish", device.wait(new Condition<UiDevice, Boolean>() {
            @Override
            public Boolean apply(UiDevice device) {
                return !activity.getDrawerViewModel().isLoading().getValue();
            }
        }, 10000));
        instrumentation.runOnMainSync(() -> {
            previousTab = activity.getDrawerViewModel().getCurrentDrawerTabIndex().getValue();
            previousServiceTab = activity.getDrawerViewModel().getCurrentServiceTabIndex().getValue();
            activity.getDrawerViewModel().addFileTreeTab(projectRoot, false);
            projectAdded = true;
        });
    }

    @Test
    public void projectSearchKeepsFocusedInputWhenToolbarHides() throws Exception {
        instrumentation.runOnMainSync(() ->
                new SearchCodeCommand().execute(new ActionContext(activity)));
        UiObject2 input = device.wait(Until.findObject(By.clazz("android.widget.EditText").focused(true)), 10000);
        assertNotNull("Project search did not gain focus", input);
        input.setText("keyboard");
        assertTrue(input.wait(Until.textEquals("keyboard"), 5000));
        device.waitForIdle();
        AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
        assertNotNull(root);
        AccessibilityNodeInfo originalInput = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        assertNotNull(originalInput);
        int originalWindow = originalInput.getWindowId();

        // Observe beyond the spring exit animation, including on devices with slowed animations.
        float animationScale = Global.getFloat(instrumentation.getTargetContext().getContentResolver(),
                Global.ANIMATOR_DURATION_SCALE, 1f);
        long observationMillis = (long) (2000 * Math.max(1f, animationScale));
        for (boolean visible : new boolean[] {false, true, false, true}) {
            instrumentation.runOnMainSync(() -> activity.getViewModel().setShowTopBar(visible));
            long started = SystemClock.uptimeMillis();
            assertTrue(device.wait(new Condition<UiDevice, Boolean>() {
                @Override
                public Boolean apply(UiDevice device) {
                    assertTrue(activity.getViewModel().getShowTopBar().getValue() == visible);
                    assertTrue("Original search input was disposed", originalInput.refresh());
                    assertTrue("Search input lost focus", originalInput.isFocused());
                    assertEquals("keyboard", originalInput.getText().toString());
                    AccessibilityNodeInfo currentRoot = instrumentation.getUiAutomation().getRootInActiveWindow();
                    assertNotNull(currentRoot);
                    AccessibilityNodeInfo focused = currentRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    assertEquals("Focused input was replaced", originalInput, focused);
                    assertEquals("Dialog window was replaced", originalWindow, currentRoot.getWindowId());
                    return SystemClock.uptimeMillis() - started >= observationMillis;
                }
            }, observationMillis + 5000));
        }
    }

    @After
    public void tearDown() throws Exception {
        instrumentation.runOnMainSync(() -> {
            GlobalDialogs.INSTANCE.getAddDialog().setValue(false);
            GlobalDialogs.INSTANCE.getFileSearchDialog().setValue(false);
            GlobalDialogs.INSTANCE.getCodeSearchDialog().setValue(false);
            if (activity != null) {
                if (projectAdded) {
                    activity.getDrawerViewModel().removeFileTreeTab(projectRoot, false);
                    if (previousTab >= 0) {
                        activity.getDrawerViewModel().selectDrawerTab(previousTab);
                    } else {
                        activity.getDrawerViewModel().unselectDrawerTab();
                    }
                    if (previousServiceTab >= 0) {
                        activity.getDrawerViewModel().selectServiceTab(previousServiceTab);
                    }
                }
                activity.finish();
            }
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            Settings.INSTANCE.setShown_disclaimer(disclaimer);
            Settings.INSTANCE.setIgnore_storage_permission(ignoreStoragePermission);
            Settings.INSTANCE.setSmart_toolbar(smartToolbar);
            Settings.INSTANCE.setFullscreen(fullscreen);
            Settings.INSTANCE.setUser_has_supported(supported);
            GlobalDialogs.INSTANCE.getAddDialog().setValue(addDialog);
            GlobalDialogs.INSTANCE.getFileSearchDialog().setValue(fileSearchDialog);
            GlobalDialogs.INSTANCE.getCodeSearchDialog().setValue(codeSearchDialog);
        });
        if (project != null) {
            Files.deleteIfExists(new File(project, "sample.txt").toPath());
            Files.deleteIfExists(project.toPath());
        }
    }
}

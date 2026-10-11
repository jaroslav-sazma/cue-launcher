package app.cue.launcher.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun seedHome() {
        device.seedHome()
    }

    @Test
    fun startup() = rule.collect(
        packageName = CUE_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait(cueHomeIntent())
        device.awaitCue()
    }

    @Test
    fun firstInteractions() = rule.collect(
        packageName = CUE_PACKAGE,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait(cueHomeIntent())
        device.awaitCue()
        device.widgetPanelRoundTrip()
        device.folderRoundTrip()
        device.stackRoundTrip()
        device.dynamicFolderRoundTrip()
        device.drawerRoundTrip()
        device.drawerSearchLaunch()
        device.leaveAndReturn()
    }
}

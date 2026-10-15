package org.blindassist.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    @Test fun launchesWithoutCameraPermission() { ActivityScenario.launch(MainActivity::class.java).use { } }
}

package com.krisoft.tridjayaelektronik.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records a Baseline Profile for the app's startup path.
 *
 * We capture cold start up to the first fully-composed screen (splash → login/main). This is the
 * highest-value stretch to pre-compile: it exercises the Compose runtime, the theme/navigation
 * graph, and the first screen's composition — exactly the code that would otherwise be JIT-compiled
 * on first run and cause the initial jank. Deeper journeys (product list scroll, CRM) sit behind the
 * login gate and can't be automated without credentials, so they're intentionally left out; startup
 * coverage delivers the bulk of the benefit.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.krisoft.tridjayaelektronik",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        // Let the splash → first real screen transition settle so navigation and the first
        // screen's composition are captured, not just the launcher activity shell.
        device.waitForIdle()
    }
}

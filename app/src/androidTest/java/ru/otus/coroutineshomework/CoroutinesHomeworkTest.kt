package ru.otus.coroutineshomework

import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class CoroutinesHomeworkTest {
    private var originalAutofillService: String? = null

    @Before fun disableSystemAutofillDuringTest() {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        originalAutofillService = Settings.Secure.getString(resolver, "autofill_service")
        setAutofillService(null)
    }

    @After fun restoreSystemAutofill() {
        setAutofillService(originalAutofillService)
    }

    @Test fun allScreensOpenInLightAndDarkThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = AppCompatDelegate.getDefaultNightMode()
        try {
            for (mode in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    await(scenario) { it.window.decorView.hasWindowFocus() }
                    onView(withId(R.id.time)).check(matches(isDisplayed()))
                    onView(withId(R.id.navigation_login)).perform(click())
                    onView(withId(R.id.login_button)).check(matches(isDisplayed()))
                    onView(withId(R.id.navigation_network)).perform(click())
                    onView(withId(R.id.numOfThreads)).check(matches(isDisplayed()))
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(original) }
        }
    }

    @Test fun timerStartsStopsResumesAndRestoresAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.window.decorView.hasWindowFocus() }
            onView(withId(R.id.time)).check(matches(withText("00:00.000")))
            onView(withId(R.id.btn_stop)).check(matches(not(isEnabled())))
            onView(withId(R.id.btn_start)).perform(click())
            await(scenario) { timerMillis(it) >= 1_100 }
            onView(withId(R.id.btn_stop)).perform(click())
            val stopped = timerText(scenario)
            val millis = toMillis(stopped)
            assertTrue(millis >= 1_100)
            assertTrue(stopped.matches(Regex("\\d{2,}:\\d{2}\\.\\d{3}")))
            scenario.recreate()
            await(scenario) { it.window.decorView.hasWindowFocus() }
            assertEquals(stopped, timerText(scenario))
            onView(withId(R.id.btn_start)).check(matches(isEnabled())).perform(click())
            await(scenario) { timerMillis(it) > millis + 200 }
            scenario.recreate()
            await(scenario) { it.window.decorView.hasWindowFocus() && timerMillis(it) > millis + 400 }
            onView(withId(R.id.btn_start)).check(matches(not(isEnabled())))
            onView(withId(R.id.btn_stop)).perform(click())
        }
    }

    @Test fun timerCanLeaveAndRecreateItsViewWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.window.decorView.hasWindowFocus() }
            onView(withId(R.id.btn_start)).perform(click())
            await(scenario) { timerMillis(it) > 200 }
            onView(withId(R.id.navigation_login)).perform(click())
            onView(withId(R.id.login_button)).check(matches(isDisplayed()))
            onView(withId(R.id.navigation_timer)).perform(click())
            onView(withId(R.id.time)).check(matches(isDisplayed()))
            val restored = timerText(scenario)
            await(scenario) { it.findViewById<TextView>(R.id.time).text.toString() != restored }
            onView(withId(R.id.btn_stop)).perform(click())
        }
    }

    @Test fun loginShowsErrorThenContentAndLogoutReturnsToForm() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.window.decorView.hasWindowFocus() }
            onView(withId(R.id.navigation_login)).perform(click())
            onView(withId(R.id.login)).perform(replaceText("wrong"))
            onView(withId(R.id.password)).perform(replaceText("wrong"), closeSoftKeyboard())
            onView(withId(R.id.login_button)).perform(click())
            await(scenario) {
                it.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.login_layout)
                    .error?.toString() == "Invalid credentials"
            }
            onView(withId(R.id.login)).perform(replaceText("admin"))
            onView(withId(R.id.password)).perform(replaceText("password"), closeSoftKeyboard())
            onView(withId(R.id.login_button)).perform(click())
            await(scenario) { it.findViewById<View>(R.id.loggingIn).isShown }
            onView(withId(R.id.login_button)).check(matches(not(isEnabled())))
            scenario.recreate()
            await(scenario) { it.window.decorView.hasWindowFocus() && it.findViewById<View>(R.id.name).isShown }
            onView(withId(R.id.name)).check(matches(withText("Admin")))
            onView(withId(R.id.logoutButton)).perform(click())
            await(scenario) { it.findViewById<View>(R.id.login_button).isShown }
            onView(withId(R.id.login_button)).check(matches(isEnabled()))
        }
    }

    @Test fun speedTestDisablesControlsAndFinishesAfterRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            await(scenario) { it.window.decorView.hasWindowFocus() }
            onView(withId(R.id.navigation_network)).perform(click())
            scenario.onActivity {
                it.findViewById<android.widget.AutoCompleteTextView>(R.id.numOfThreads).setText("5", false)
            }
            onView(withId(R.id.btnStart)).perform(click())
            onView(withId(R.id.btnStart)).check(matches(not(isEnabled())))
            scenario.recreate()
            await(scenario) { it.window.decorView.hasWindowFocus() && it.findViewById<View>(R.id.btnStart).isEnabled }
            onView(withId(R.id.numOfThreads)).check(matches(withText("5")))
            onView(withId(R.id.loading)).check(matches(not(isDisplayed())))
            onView(withId(R.id.result)).check(matches(isDisplayed()))
            var result = ""
            scenario.onActivity { result = it.findViewById<TextView>(R.id.result).text.toString() }
            assertNotEquals("", result)
        }
    }

    private fun timerText(scenario: ActivityScenario<MainActivity>): String {
        var text = ""
        scenario.onActivity { text = it.findViewById<TextView>(R.id.time).text.toString() }
        return text
    }

    private fun timerMillis(activity: MainActivity) =
        toMillis(activity.findViewById<TextView>(R.id.time).text.toString())

    private fun toMillis(text: String): Long {
        val (minutes, seconds, millis) = text.split(':', '.')
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis.toLong()
    }

    private fun await(
        scenario: ActivityScenario<MainActivity>,
        condition: (MainActivity) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(20)
        }
        throw AssertionError("UI condition not satisfied in 10 seconds")
    }

    private fun setAutofillService(service: String?) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.WRITE_SECURE_SETTINGS")
        try {
            Settings.Secure.putString(instrumentation.targetContext.contentResolver, "autofill_service", service)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}

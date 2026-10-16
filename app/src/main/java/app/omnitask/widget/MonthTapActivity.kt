package app.omnitask.widget

import android.app.Activity
import android.os.Build
import android.os.Bundle

/**
 * Takes a tap on a day, on ‹ › or on the title of the month widget, and finishes before it is drawn.
 *
 * The owner's phone (OPPO, ColorOS) freezes the app a few seconds after it was last used, even with its battery use
 * unrestricted, and holds what is sent to a frozen app: broadcasts, and the start of the foreground service the taps
 * went through before, waited about five seconds now and then and then arrived together. Starting an activity is a
 * user's action the phone carries out at once, so the app is woken for every tap. The activity has no window
 * (Theme.NoDisplay), lives in a task of its own that stays out of the recent apps, and finishes in onCreate; the tap
 * is shown by the composition [MonthLive] keeps.
 */
class MonthTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        noTransition()
        WidgetLog.attach(this)
        WidgetLog.add("tap received through the activity")
        intent?.let { MonthTapService.tapNow(applicationContext, it) }
        finish()
        noTransition()
    }

    // A tap that finds this activity still there comes here instead.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        MonthTapService.tapNow(applicationContext, intent)
    }

    @Suppress("DEPRECATION")
    private fun noTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }
}

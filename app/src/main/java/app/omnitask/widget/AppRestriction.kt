package app.omnitask.widget

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Whether the phone holds the app back in the background. With the app restricted (the Battery page of the app's
 * settings, or the phone maker's own lists), Android keeps the widget's taps for seconds and delivers them together, so
 * the widget seems to freeze and then jump; the owner's phone did this until the setting was changed.
 */
object AppRestriction {
    /** The app may not run in the background at all: taps to the widget wait. */
    fun restricted(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    /** Battery optimisation is on for the app (the "unrestricted" choice turns it off). */
    fun optimised(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != true

    fun describe(context: Context): String = "restricted=${restricted(context)} optimised=${optimised(context)}"

    /** The app's own page in the phone's settings, where Battery holds the choice. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

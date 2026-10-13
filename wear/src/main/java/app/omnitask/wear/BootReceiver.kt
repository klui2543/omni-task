package app.omnitask.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A reboot or an update of the app clears its alarms; the saved wake time goes back in. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) WakeAlarm.schedule(context)
    }
}

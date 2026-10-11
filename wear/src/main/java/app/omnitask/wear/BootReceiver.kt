package app.omnitask.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A reboot clears the system's alarms; the saved wake time goes back in. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) WakeAlarm.schedule(context)
    }
}

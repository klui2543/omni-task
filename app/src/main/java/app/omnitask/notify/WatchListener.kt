package app.omnitask.notify

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.omnitask.time.*

/**
 * A wake time set on the watch comes back here and becomes tonight's wake time, so the sleep card counts the
 * hours from it. It is saved straight into the app's preferences; an open app picks it up from there.
 */
class WatchListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != WatchAlarm.FROM_WATCH) return@forEach
            val text = DataMapItem.fromDataItem(e.dataItem).dataMap.getString("at") ?: return@forEach
            val at = runCatching { LocalDateTime.parse(text) }.getOrNull() ?: return@forEach
            save(this, at)
        }
    }

    companion object {
        fun save(context: Context, at: LocalDateTime) {
            // The night belongs to the evening before a morning wake.
            val evening = if (at.time < LocalTime.NOON) at.date.minusDays(1) else at.date
            val value = "$evening ${"%02d:%02d".format(at.hour, at.minute)}"
            context.getSharedPreferences("omnitask", Context.MODE_PRIVATE).edit()
                .putString(WatchAlarm.KEY_TONIGHT_WAKE, value)
                .putString(WatchAlarm.KEY_ALARM_SET, value)
                .putBoolean(WatchAlarm.KEY_WAKE_FROM_WATCH, true)
                .apply()
        }
    }
}

package app.omnitask.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/** Wakes when the phone writes or removes the wake alarm, even if the watch app is closed. */
class WakeListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.dataItem.uri.path != WakeAlarm.PATH) return@forEach
            if (e.type == DataEvent.TYPE_DELETED) WakeAlarm.set(this, null)
            else WakeAlarm.fromPhone(this, DataMapItem.fromDataItem(e.dataItem).dataMap)
        }
    }
}

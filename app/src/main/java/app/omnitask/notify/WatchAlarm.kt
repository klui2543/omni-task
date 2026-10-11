package app.omnitask.notify

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.datetime.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Sends tonight's wake alarm to Omni on the Galaxy Watch (the `wear` module), which rings it there; the phone
 * stays quiet. Wear OS keeps the data item and delivers it whenever the watch is back in reach.
 */
object WatchAlarm {
    private const val PATH = "/wake"
    private const val CAPABILITY = "omni_wake"

    /**
     * Writes [at] (null cancels) and says whether a watch with Omni is in reach right now. Blocks, so call it
     * off the main thread; false also when the phone has no Wear OS at all.
     */
    fun send(context: Context, at: LocalDateTime?, sleepMinutes: Long): Boolean = runCatching {
        val request = PutDataMapRequest.create(PATH).apply {
            dataMap.putString("at", at?.toString() ?: "")
            dataMap.putLong("sleep", sleepMinutes)
            // A new value every time, so sending the same time again still reaches the watch.
            dataMap.putLong("sent", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Tasks.await(Wearable.getDataClient(context).putDataItem(request), 10, TimeUnit.SECONDS)
        val reach = Tasks.await(Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE), 10, TimeUnit.SECONDS)
        reach.nodes.isNotEmpty()
    }.getOrDefault(false)
}

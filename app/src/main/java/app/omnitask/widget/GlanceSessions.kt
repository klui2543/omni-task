package app.omnitask.widget

import android.os.Bundle
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Reaches the session Glance keeps for a composition started with runComposition, which Glance marks internal:
 * finding it by its app widget id, and sending it the events that keep it alive and have it draw again. Done by
 * name, so a Glance release that moves these leaves the widget on its slower path instead of failing.
 */
internal object GlanceSessions {
    private val companion: Any? by lazy {
        runCatching { Class.forName("androidx.glance.appwidget.UnmanagedSessionReceiver").getField("Companion").get(null) }.getOrNull()
    }
    private val getSession by lazy { companion?.javaClass?.methods?.firstOrNull { it.name == "getSession" && it.parameterCount == 1 } }

    /** The running session for [widgetId], or null when none is (or Glance changed). */
    fun session(widgetId: Int): Any? = runCatching { getSession?.invoke(companion, widgetId) }.getOrNull()

    /** Tells [session] its state changed: it reads the state again and draws. */
    suspend fun updateGlance(session: Any) = call(session, "updateGlance")

    /** Gives [session] the launcher's new options (sizes) to draw for. */
    suspend fun updateAppWidgetOptions(session: Any, options: Bundle) = call(session, "updateAppWidgetOptions", options)

    private suspend fun call(target: Any, name: String, vararg args: Any?) {
        val method = target.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == args.size + 1 } ?: return
        suspendCoroutineUninterceptedOrReturn<Any?> { cont -> method.invoke(target, *args, cont) }
    }
}

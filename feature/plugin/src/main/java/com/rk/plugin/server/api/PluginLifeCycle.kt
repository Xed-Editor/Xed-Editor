package com.rk.plugin.server.api

import android.app.Activity
import androidx.annotation.Keep
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Keep
object PluginLifeCycle {
    enum class LifeCycleType {
        CREATE,
        DESTROY,
        PAUSED,
        RESUMED,
    }

    @Keep
    interface ActivityEvent {
        @Keep fun onEvent(id: String, activity: Activity)
    }

    private val eventMap = HashMap<LifeCycleType, MutableList<Pair<String, ActivityEvent>>>()
    private val listeners = HashSet<String>()

    // broadcast event
    @OptIn(DelicateCoroutinesApi::class)
    @Keep
    fun onActivityEvent(activity: Activity, type: LifeCycleType) {
        GlobalScope.launch(Dispatchers.Default) {
            eventMap[type]?.forEach { activityEvent ->
                Thread { activityEvent.second.onEvent(activityEvent.first, activity) }.start()
            }
        }
    }

    @Keep
    fun registerLifeCycle(id: String, type: LifeCycleType, activityEvent: ActivityEvent) {
        if (listeners.contains(id)) {
            return
        }

        if (eventMap[type] == null) {
            eventMap[type] = arrayListOf(Pair(id, activityEvent))
        } else {
            eventMap[type]?.add(Pair(id, activityEvent))
        }

        listeners.add(id)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Keep
    fun unregisterActivityEvent(id: String) {
        GlobalScope.launch(Dispatchers.Default) {
            synchronized(eventMap) {
                eventMap.values.forEach { value ->
                    value.toList().forEachIndexed { index, pair ->
                        if (pair.first == id) {
                            value.removeAt(index)
                            return@launch
                        }
                    }
                }
            }
        }
        listeners.remove(id)
    }
}

package com.vinsvova.alicescreensaver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Перезапускает таймер после перезагрузки планшета и после обновления приложения. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs(context).enabled) {
            RestTimerService.send(context, RestTimerService.ACTION_RELOAD)
        }
    }
}

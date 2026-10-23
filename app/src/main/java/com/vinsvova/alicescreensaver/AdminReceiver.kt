package com.vinsvova.alicescreensaver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Права администратора устройства нужны только для того, чтобы приложение
 * нельзя было удалить, пока родитель не отключит защиту.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Защита «Таймера отдыха» будет снята. Это должен делать только родитель."
}

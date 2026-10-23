package com.vinsvova.alicescreensaver

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Фоновая служба: считает время, пока экран включён и разблокирован,
 * и по истечении лимита показывает экран отдыха на заданное время.
 */
class RestTimerService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LockOverlay? = null
    private var lastTick = 0L
    private var playedMs = 0L
    private var warned = false
    private var lastNotificationText = ""
    private var receiverRegistered = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> prefs.screenOffAt = System.currentTimeMillis()
                Intent.ACTION_SCREEN_ON -> {
                    val offAt = prefs.screenOffAt
                    prefs.screenOffAt = 0L
                    // Если планшет пролежал выключенным дольше перерыва — ребёнок уже отдохнул.
                    if (offAt > 0 && prefs.restEndsAt == 0L &&
                        System.currentTimeMillis() - offAt >= prefs.restMinutes * 60_000L
                    ) {
                        resetPlay()
                    }
                    tick()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        power = getSystemService(PowerManager::class.java)
        keyguard = getSystemService(KeyguardManager::class.java)
        createChannel()

        playedMs = prefs.playedSeconds * 1000
        lastTick = SystemClock.elapsedRealtime()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true

        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification("Таймер запущен"),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!prefs.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_RELOAD -> {
                playedMs = prefs.playedSeconds * 1000
                warned = false
                overlay?.setMessage(prefs.message)
            }
            ACTION_REST_NOW -> startRest()
            ACTION_UNLOCK -> endRest()
        }
        lastNotificationText = ""
        tick()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }

    private fun tick() {
        val nowElapsed = SystemClock.elapsedRealtime()
        // Если устройство «спало», большой скачок не считаем игровым временем.
        val delta = (nowElapsed - lastTick).coerceIn(0L, 2000L)
        lastTick = nowElapsed

        val now = System.currentTimeMillis()

        // Блокировка по расписанию важнее всего остального.
        if (prefs.isBlockedNow(now)) {
            val until = formatClock(prefs.blockEnd)
            showOverlay()
            overlay?.setBlockMode(until)
            notify("Планшет заблокирован до $until")
            return
        }

        val restEnd = prefs.restEndsAt
        if (restEnd > 0) {
            if (now >= restEnd) {
                endRest()
            } else {
                val left = restEnd - now
                showOverlay()
                overlay?.setBlockMode(null)
                overlay?.updateCountdown(left)
                notify("Перерыв, осталось ${formatMinutes(left)}")
                return
            }
        }

        hideOverlay()

        if (power.isInteractive && !keyguard.isKeyguardLocked) {
            playedMs += delta
            prefs.playedSeconds = playedMs / 1000
        }

        val left = prefs.playMinutes * 60_000L - playedMs
        if (!warned && left in 1..60_000L) {
            warned = true
            Toast.makeText(this, "Через минуту перерыв", Toast.LENGTH_LONG).show()
        }
        if (left <= 0) {
            startRest()
            return
        }
        notify("До перерыва ${formatMinutes(left)}")
    }

    private fun startRest() {
        prefs.restEndsAt = System.currentTimeMillis() + prefs.restMinutes * 60_000L
        resetPlay()
        showOverlay()
        overlay?.updateCountdown(prefs.restEndsAt - System.currentTimeMillis())
    }

    private fun endRest() {
        prefs.restEndsAt = 0L
        resetPlay()
        hideOverlay()
        lastNotificationText = ""
    }

    private fun resetPlay() {
        playedMs = 0L
        prefs.playedSeconds = 0L
        warned = false
    }

    private fun showOverlay() {
        if (overlay?.isShown == true) return
        if (!Settings.canDrawOverlays(this)) {
            notify("Нет разрешения «Поверх других окон», экран отдыха не показан")
            return
        }
        val current = overlay ?: LockOverlay(this, prefs) { onParentUnlock() }.also { overlay = it }
        current.show(prefs.message)
    }

    private fun onParentUnlock() {
        if (prefs.isBlockedNow()) {
            // Родитель снимает блокировку по расписанию на время, потом она вернётся сама.
            prefs.blockPauseUntil = System.currentTimeMillis() + PARENT_PAUSE_MINUTES * 60_000L
            Toast.makeText(
                this,
                "Блокировка снята на $PARENT_PAUSE_MINUTES минут",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Разблокировано родителем", Toast.LENGTH_SHORT).show()
        }
        endRest()
    }

    private fun hideOverlay() {
        overlay?.hide()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notify(text: String) {
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_RELOAD = "com.vinsvova.alicescreensaver.RELOAD"
        const val ACTION_REST_NOW = "com.vinsvova.alicescreensaver.REST_NOW"
        const val ACTION_UNLOCK = "com.vinsvova.alicescreensaver.UNLOCK"

        private const val CHANNEL_ID = "rest_timer"
        private const val NOTIFICATION_ID = 1
        private const val PARENT_PAUSE_MINUTES = 15

        fun send(context: Context, action: String) {
            val intent = Intent(context, RestTimerService::class.java).setAction(action)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // На некоторых версиях Android запуск из фона может быть запрещён.
            }
        }
    }
}

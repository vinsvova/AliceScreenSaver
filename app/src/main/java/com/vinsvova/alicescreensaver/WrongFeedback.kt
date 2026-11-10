package com.vinsvova.alicescreensaver

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Вибрация и звук при неправильном ответе. */
object WrongFeedback {

    private var player: MediaPlayer? = null

    /** after — вызывается, когда звук доиграл (или сразу, если звука нет). */
    fun play(context: Context, prefs: Prefs, after: (() -> Unit)? = null) {
        if (prefs.wrongVibrate) vibrate(context)
        playSound(context, prefs.wrongSound, after)
    }

    fun hasVibrator(context: Context): Boolean = vibrator(context)?.hasVibrator() == true

    fun vibrate(context: Context) {
        val v = vibrator(context) ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 90, 180, 90, 260), -1))
        } catch (e: Exception) {
            // вибрация недоступна
        }
    }

    fun playSound(context: Context, sound: String, after: (() -> Unit)? = null) {
        val res = when (sound) {
            Prefs.SOUND_GROWL -> R.raw.wrong_growl
            Prefs.SOUND_HISS -> R.raw.wrong_hiss
            Prefs.SOUND_WILD -> R.raw.wrong_wild
            else -> {
                after?.invoke()
                return
            }
        }
        stop()
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val mp = MediaPlayer.create(context.applicationContext, res, attrs, 0)
            if (mp == null) {
                after?.invoke()
                return
            }
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                after?.invoke()
            }
            player = mp
            mp.start()
        } catch (e: Exception) {
            after?.invoke()
        }
    }

    fun stop() {
        player?.let {
            try {
                it.stop()
            } catch (e: Exception) {
            }
            it.release()
        }
        player = null
    }

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
}

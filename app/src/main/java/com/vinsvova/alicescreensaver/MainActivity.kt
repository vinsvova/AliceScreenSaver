package com.vinsvova.alicescreensaver

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Экран настроек для родителя. Открывается только после ввода PIN.
 * Стоит выйти из приложения — при следующем открытии PIN спросят снова.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var content: View
    private lateinit var status: TextView
    private lateinit var playEdit: EditText
    private lateinit var restEdit: EditText
    private lateinit var messageEdit: EditText
    private lateinit var overlayButton: Button
    private lateinit var notificationsButton: Button
    private lateinit var batteryButton: Button
    private lateinit var adminButton: Button

    private var unlocked = false
    private var pinDialogShowing = false
    private var openingSystemScreen = false

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 1000)
        }
    }

    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        content = findViewById(R.id.content)
        status = findViewById(R.id.status)
        playEdit = findViewById(R.id.play_minutes)
        restEdit = findViewById(R.id.rest_minutes)
        messageEdit = findViewById(R.id.message)
        overlayButton = findViewById(R.id.perm_overlay)
        notificationsButton = findViewById(R.id.perm_notifications)
        batteryButton = findViewById(R.id.perm_battery)
        adminButton = findViewById(R.id.perm_admin)

        findViewById<Button>(R.id.save_start).setOnClickListener { saveAndStart() }
        findViewById<Button>(R.id.rest_now).setOnClickListener { restNow() }
        findViewById<Button>(R.id.stop).setOnClickListener { confirmStop() }
        findViewById<Button>(R.id.change_pin).setOnClickListener { askNewPin(firstTime = false) }
        overlayButton.setOnClickListener { openOverlaySettings() }
        notificationsButton.setOnClickListener { requestNotifications() }
        batteryButton.setOnClickListener { openBatterySettings() }
        adminButton.setOnClickListener { toggleAdmin() }

        findViewById<TextView>(R.id.version).text = "Версия ${appVersion()}"
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) {
            showContent()
        } else {
            content.visibility = View.INVISIBLE
            if (prefs.hasPin()) askPin() else askNewPin(firstTime = true)
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(refresher)
        // Если родитель сам открыл системные настройки — не закрываем доступ.
        if (openingSystemScreen) openingSystemScreen = false else unlocked = false
    }

    // ---------- PIN ----------

    private fun askPin() {
        if (pinDialogShowing) return
        pinDialogShowing = true
        val input = pinField("PIN")
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Введите PIN родителя")
            .setView(wrap(input))
            .setCancelable(false)
            .setPositiveButton("Войти", null)
            .setNegativeButton("Выйти") { _, _ ->
                pinDialogShowing = false
                finish()
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val wait = prefs.lockoutSecondsLeft()
                when {
                    wait > 0 -> input.error = "Слишком много попыток. Подождите $wait с"
                    prefs.checkPin(input.text.toString()) -> {
                        pinDialogShowing = false
                        unlocked = true
                        dialog.dismiss()
                        showContent()
                    }
                    else -> {
                        input.text.clear()
                        val lock = prefs.lockoutSecondsLeft()
                        input.error = if (lock > 0) "Слишком много попыток. Подождите $lock с" else "Неверный PIN"
                    }
                }
            }
        }
        dialog.show()
    }

    private fun askNewPin(firstTime: Boolean) {
        if (pinDialogShowing) return
        pinDialogShowing = true
        val first = pinField("Новый PIN (от 4 цифр)")
        val second = pinField("Повторите PIN")
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (firstTime) "Придумайте PIN родителя" else "Новый PIN родителя")
            .setMessage(
                if (firstTime) "Он нужен, чтобы менять настройки, выключать таймер и досрочно снимать блокировку. Ребёнку его не показывайте."
                else null
            )
            .setView(wrap(first, second))
            .setCancelable(false)
            .setPositiveButton("Сохранить", null)
            .setNegativeButton(if (firstTime) "Выйти" else "Отмена") { _, _ ->
                pinDialogShowing = false
                if (firstTime) finish()
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = first.text.toString()
                when {
                    pin.length < 4 -> first.error = "Минимум 4 цифры"
                    pin != second.text.toString() -> second.error = "PIN не совпадает"
                    else -> {
                        prefs.setPin(pin)
                        pinDialogShowing = false
                        unlocked = true
                        dialog.dismiss()
                        showContent()
                        Toast.makeText(this, "PIN сохранён", Toast.LENGTH_SHORT).show()
                        if (firstTime) showWelcome()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showWelcome() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Что дальше")
            .setMessage(
                "1. В разделе «Разрешения» включите все пункты, обязательно «Поверх других окон».\n\n" +
                    "2. Укажите, сколько минут можно пользоваться планшетом и сколько длится перерыв.\n\n" +
                    "3. Нажмите «Сохранить и запустить».\n\n" +
                    "Кнопка «Перерыв сейчас» позволяет сразу проверить, как выглядит экран отдыха."
            )
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun pinField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }

    private fun wrap(vararg views: View): View {
        val pad = (24 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            views.forEach { addView(it) }
        }
    }

    // ---------- Основной экран ----------

    private fun showContent() {
        content.visibility = View.VISIBLE
        playEdit.setText(prefs.playMinutes.toString())
        restEdit.setText(prefs.restMinutes.toString())
        messageEdit.setText(prefs.message)
        refreshPermissions()
        handler.removeCallbacks(refresher)
        handler.post(refresher)
    }

    private fun refreshStatus() {
        val now = System.currentTimeMillis()
        val restEnd = prefs.restEndsAt
        status.text = when {
            !prefs.enabled -> "⏸ Таймер выключен"
            restEnd > now -> "🌿 Идёт перерыв, осталось ${formatDuration(restEnd - now)}"
            else -> {
                val left = (prefs.playMinutes * 60_000L - prefs.playedSeconds * 1000).coerceAtLeast(0)
                "▶ Таймер работает. До перерыва ${formatDuration(left)}"
            }
        }
    }

    private fun saveAndStart() {
        val play = playEdit.text.toString().toIntOrNull()
        val rest = restEdit.text.toString().toIntOrNull()
        if (play == null || play !in 1..600) {
            playEdit.error = "От 1 до 600 минут"
            return
        }
        if (rest == null || rest !in 1..240) {
            restEdit.error = "От 1 до 240 минут"
            return
        }
        prefs.playMinutes = play
        prefs.restMinutes = rest
        prefs.message = messageEdit.text.toString().trim().ifEmpty { Prefs.DEFAULT_MESSAGE }

        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Нужно разрешение")
                .setMessage("Чтобы показывать экран «${prefs.message}» поверх игр и видео, включите для приложения разрешение «Поверх других окон».")
                .setPositiveButton("Открыть настройки") { _, _ -> openOverlaySettings() }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }

        val wasEnabled = prefs.enabled
        prefs.enabled = true
        if (!wasEnabled) {
            prefs.playedSeconds = 0L
            prefs.restEndsAt = 0L
        }
        RestTimerService.send(this, RestTimerService.ACTION_RELOAD)
        Toast.makeText(this, "Таймер запущен", Toast.LENGTH_SHORT).show()
        refreshStatus()
    }

    private fun restNow() {
        if (!prefs.enabled) {
            Toast.makeText(this, "Сначала нажмите «Сохранить и запустить»", Toast.LENGTH_LONG).show()
            return
        }
        RestTimerService.send(this, RestTimerService.ACTION_REST_NOW)
    }

    private fun confirmStop() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Остановить таймер?")
            .setMessage("Перерывы перестанут включаться, пока вы снова не запустите таймер.")
            .setPositiveButton("Остановить") { _, _ ->
                prefs.enabled = false
                prefs.restEndsAt = 0L
                prefs.playedSeconds = 0L
                stopService(Intent(this, RestTimerService::class.java))
                refreshStatus()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- Разрешения ----------

    private fun refreshPermissions() {
        overlayButton.text = mark(Settings.canDrawOverlays(this)) + "Поверх других окон"

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            notificationsButton.visibility = View.VISIBLE
            notificationsButton.text = mark(granted) + "Уведомления"
        } else {
            notificationsButton.visibility = View.GONE
        }

        val power = getSystemService(PowerManager::class.java)
        batteryButton.text = mark(power.isIgnoringBatteryOptimizations(packageName)) + "Работа в фоне без ограничений"

        val dpm = getSystemService(DevicePolicyManager::class.java)
        adminButton.text = mark(dpm.isAdminActive(adminComponent)) + "Защита от удаления"
    }

    private fun mark(ok: Boolean) = if (ok) "✓  " else "✗  "

    private fun openOverlaySettings() {
        openSystemScreen(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            openAppSettings()
        } else {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
    }

    private fun openBatterySettings() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            openSystemScreen(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } else {
            val ok = openSystemScreen(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
            if (!ok) openSystemScreen(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun toggleAdmin() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        if (dpm.isAdminActive(adminComponent)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Снять защиту от удаления?")
                .setMessage("После этого приложение можно будет удалить обычным способом.")
                .setPositiveButton("Снять") { _, _ ->
                    dpm.removeActiveAdmin(adminComponent)
                    handler.postDelayed({ refreshPermissions() }, 500)
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
            openSystemScreen(intent)
        }
    }

    private fun openAppSettings() {
        openSystemScreen(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun openSystemScreen(intent: Intent): Boolean {
        return try {
            openingSystemScreen = true
            startActivity(intent)
            true
        } catch (e: Exception) {
            openingSystemScreen = false
            false
        }
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 10
    }
}

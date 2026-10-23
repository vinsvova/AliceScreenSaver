package com.vinsvova.alicescreensaver

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Полноэкранное окно «Отдохни» поверх всех приложений.
 * Кнопки «Назад», «Домой» и «Недавние» его не убирают: окно принадлежит службе,
 * а не приложению. Снять его можно только PIN-кодом родителя или дождавшись конца перерыва.
 */
class LockOverlay(
    private val context: Context,
    private val prefs: Prefs,
    private val onUnlock: () -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var root: FrameLayout? = null
    private var messageView: TextView? = null
    private var countdownView: TextView? = null
    private var parentPanel: View? = null
    private var parentButton: View? = null
    private var pinEdit: EditText? = null
    private var errorView: TextView? = null

    val isShown: Boolean get() = root != null

    fun show(message: String) {
        if (root != null) return
        val themed = ContextThemeWrapper(context, R.style.Theme_App)

        val container = object : FrameLayout(themed) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hideParentPanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        LayoutInflater.from(themed).inflate(R.layout.overlay_lock, container, true)

        messageView = container.findViewById<TextView>(R.id.lock_message).apply { text = message }
        countdownView = container.findViewById(R.id.lock_countdown)
        parentPanel = container.findViewById(R.id.parent_panel)
        parentButton = container.findViewById(R.id.parent_button)
        pinEdit = container.findViewById(R.id.parent_pin)
        errorView = container.findViewById(R.id.parent_error)

        parentButton?.setOnClickListener { showParentPanel() }
        container.findViewById<View>(R.id.parent_cancel).setOnClickListener { hideParentPanel() }
        container.findViewById<View>(R.id.parent_unlock).setOnClickListener { tryUnlock() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        )
        @Suppress("DEPRECATION")
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        container.isFocusableInTouchMode = true
        try {
            windowManager.addView(container, params)
        } catch (e: Exception) {
            return
        }
        root = container
        container.requestFocus()
        container.post { hideSystemBars(container) }
    }

    fun hide() {
        val view = root ?: return
        hideKeyboard()
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // окно уже удалено
        }
        root = null
    }

    fun setMessage(message: String) {
        messageView?.text = message
    }

    fun updateCountdown(leftMs: Long) {
        countdownView?.text = "Осталось ${formatDuration(leftMs)}"
    }

    private fun showParentPanel() {
        parentPanel?.visibility = View.VISIBLE
        parentButton?.visibility = View.GONE
        errorView?.text = ""
        pinEdit?.let { edit ->
            edit.text.clear()
            edit.requestFocus()
            edit.post {
                context.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun hideParentPanel() {
        hideKeyboard()
        pinEdit?.text?.clear()
        errorView?.text = ""
        parentPanel?.visibility = View.GONE
        parentButton?.visibility = View.VISIBLE
        root?.requestFocus()
        root?.let { hideSystemBars(it) }
    }

    private fun tryUnlock() {
        val wait = prefs.lockoutSecondsLeft()
        if (wait > 0) {
            errorView?.text = "Слишком много попыток. Подождите $wait с"
            return
        }
        val pin = pinEdit?.text?.toString().orEmpty()
        if (prefs.checkPin(pin)) {
            hideKeyboard()
            onUnlock()
        } else {
            pinEdit?.text?.clear()
            val lock = prefs.lockoutSecondsLeft()
            errorView?.text = if (lock > 0) "Слишком много попыток. Подождите $lock с" else "Неверный PIN"
        }
    }

    private fun hideKeyboard() {
        val edit = pinEdit ?: return
        context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(edit.windowToken, 0)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars(view: View) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.windowInsetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            view.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }
}

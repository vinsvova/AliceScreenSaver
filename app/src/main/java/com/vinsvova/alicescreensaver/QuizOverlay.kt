package com.vinsvova.alicescreensaver

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Полноэкранное окно с примерами поверх всех приложений.
 * Ответ вводится на собственной крупной цифровой клавиатуре.
 * Окно не закрывается, пока все примеры не решены верно (или родитель не введёт PIN).
 *
 * onDone получает список примеров, в которых ребёнок хотя бы раз ошибся или не успел.
 */
class QuizOverlay(
    private val context: Context,
    private val prefs: Prefs,
    private val examples: List<Example>,
    private val isRetry: Boolean,
    private val answerSeconds: Int,
    private val onDone: (List<Example>) -> Unit,
    private val onParentSkip: () -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val solved = BooleanArray(examples.size)
    private val missed = BooleanArray(examples.size)
    private val wrongNow = BooleanArray(examples.size)
    private val answers = Array(examples.size) { "" }
    private var active = 0
    private var finished = false
    private var secondsLeft = answerSeconds

    private var root: FrameLayout? = null
    private lateinit var catView: ImageView
    private lateinit var titleView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var timerView: TextView
    private lateinit var keypadView: LinearLayout
    private val fieldViews = mutableListOf<TextView>()

    private var parentPanel: View? = null
    private var parentButton: View? = null
    private var pinEdit: EditText? = null
    private var errorView: TextView? = null

    val isShown: Boolean get() = root != null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (finished || root == null) return
            // Пока экран выключен, время на ответ не уходит.
            if (power.isInteractive) {
                secondsLeft--
                if (secondsLeft <= 0) onTimeUp()
            }
            updateTimer()
            handler.postDelayed(this, 1000)
        }
    }

    fun show() {
        if (root != null) return
        val themed = ContextThemeWrapper(context, R.style.Theme_App)

        val container = object : FrameLayout(themed) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) hideParentPanel()
                    return true
                }
                // Ввод с физической клавиатуры, если она подключена.
                if (event.action == KeyEvent.ACTION_DOWN && pinEdit?.hasFocus() != true) {
                    when (event.keyCode) {
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                            onDigit(('0' + (event.keyCode - KeyEvent.KEYCODE_0)))
                            return true
                        }
                        KeyEvent.KEYCODE_DEL -> { onBackspace(); return true }
                        KeyEvent.KEYCODE_ENTER -> { check(); return true }
                    }
                }
                return super.dispatchKeyEvent(event)
            }
        }
        LayoutInflater.from(themed).inflate(R.layout.overlay_quiz, container, true)

        catView = container.findViewById(R.id.quiz_cat)
        titleView = container.findViewById(R.id.quiz_title)
        feedbackView = container.findViewById(R.id.quiz_feedback)
        timerView = container.findViewById(R.id.quiz_timer)
        keypadView = container.findViewById(R.id.quiz_keypad)

        titleView.text = if (isRetry) "Давай ещё раз эти примеры" else "Реши примеры"
        setFeedback("Котик ждёт ответ", R.color.rest_text_soft)
        catView.setImageResource(R.drawable.cat_neutral)

        buildExamples(container.findViewById(R.id.quiz_examples))
        buildKeypad()

        parentPanel = container.findViewById(R.id.parent_panel)
        parentButton = container.findViewById(R.id.parent_button)
        pinEdit = container.findViewById(R.id.parent_pin)
        errorView = container.findViewById(R.id.parent_error)
        parentButton?.setOnClickListener { showParentPanel() }
        container.findViewById<View>(R.id.parent_cancel).setOnClickListener { hideParentPanel() }
        container.findViewById<View>(R.id.parent_unlock).setOnClickListener { tryParentSkip() }

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

        refreshFields()
        updateTimer()
        handler.postDelayed(timerRunnable, 1000)
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        val view = root ?: return
        hideKeyboard()
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            // окно уже удалено
        }
        root = null
    }

    // ---------- Построение экрана ----------

    private fun buildExamples(container: LinearLayout) {
        examples.forEachIndexed { i, ex ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val text = TextView(context).apply {
                this.text = ex.text
                textSize = 56f
                setTextColor(color(R.color.rest_text))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
                minWidth = dp(230)
            }
            val field = TextView(context).apply {
                textSize = 56f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(color(R.color.rest_text))
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener {
                    if (!solved[i] && !finished) {
                        active = i
                        refreshFields()
                    }
                }
            }
            row.addView(text)
            row.addView(field, LinearLayout.LayoutParams(dp(140), dp(88)).apply { marginStart = dp(14) })
            container.addView(row)
            fieldViews.add(field)
        }
    }

    private fun buildKeypad() {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        )
        for (labels in rows) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (label in labels) {
                val isCheck = label == "✓"
                val key = TextView(context).apply {
                    text = label
                    textSize = if (isCheck) 36f else 32f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(color(if (isCheck) R.color.rest_background else R.color.rest_text))
                    background = keyBackground(color(if (isCheck) R.color.rest_accent else R.color.quiz_key))
                    isClickable = true
                    isFocusable = false
                    setOnClickListener {
                        when (label) {
                            "⌫" -> onBackspace()
                            "✓" -> check()
                            else -> onDigit(label[0])
                        }
                    }
                }
                row.addView(key, LinearLayout.LayoutParams(dp(92), dp(66)).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
            }
            keypadView.addView(row)
        }
    }

    // ---------- Ввод и проверка ----------

    private fun onDigit(d: Char) {
        if (finished) return
        val i = active
        if (solved[i]) return
        if (answers[i].length >= 2) return
        if (answers[i].isEmpty() && d == '0') return
        answers[i] += d
        wrongNow[i] = false
        refreshFields()
    }

    private fun onBackspace() {
        if (finished) return
        val i = active
        if (solved[i] || answers[i].isEmpty()) return
        answers[i] = answers[i].dropLast(1)
        refreshFields()
    }

    private fun check() {
        if (finished) return
        val unsolved = examples.indices.filter { !solved[it] }
        val filled = unsolved.filter { answers[it].isNotEmpty() }
        if (filled.isEmpty()) {
            setFeedback("Впиши ответ в окошко", R.color.rest_text_soft)
            return
        }

        var anyWrong = false
        for (i in filled) {
            if (answers[i].toIntOrNull() == examples[i].answer) {
                solved[i] = true
            } else {
                missed[i] = true
                wrongNow[i] = true
                answers[i] = ""
                anyWrong = true
            }
        }

        when {
            examples.indices.all { solved[it] } -> success()
            anyWrong -> {
                catView.setImageResource(R.drawable.cat_sad)
                shake(catView)
                setFeedback("Подумай ещё!", R.color.rest_error)
                active = examples.indices.first { !solved[it] && wrongNow[it] }
            }
            else -> {
                catView.setImageResource(R.drawable.cat_neutral)
                setFeedback("Верно! Теперь следующий пример", R.color.quiz_ok)
                active = examples.indices.first { !solved[it] }
            }
        }
        refreshFields()
    }

    private fun onTimeUp() {
        var any = false
        for (i in examples.indices) {
            if (!solved[i]) {
                missed[i] = true
                wrongNow[i] = true
                answers[i] = ""
                any = true
            }
        }
        if (!any) return
        catView.setImageResource(R.drawable.cat_sad)
        shake(catView)
        setFeedback("Время вышло! Подумай ещё!", R.color.rest_error)
        active = examples.indices.first { !solved[it] }
        secondsLeft = answerSeconds
        refreshFields()
    }

    private fun success() {
        finished = true
        handler.removeCallbacks(timerRunnable)
        catView.setImageResource(R.drawable.cat_happy)
        bounce(catView)
        titleView.text = "Молодец! Так держать!"
        titleView.setTextColor(color(R.color.rest_accent))
        titleView.textSize = 40f
        feedbackView.text = ""
        timerView.visibility = View.GONE
        keypadView.visibility = View.INVISIBLE
        parentButton?.visibility = View.GONE
        parentPanel?.visibility = View.GONE
        hideKeyboard()
        val missedList = examples.filterIndexed { i, _ -> missed[i] }
        handler.postDelayed({ onDone(missedList) }, SUCCESS_SHOW_MS)
    }

    private fun refreshFields() {
        fieldViews.forEachIndexed { i, field ->
            field.text = answers[i].ifEmpty { if (solved[i]) "" else "?" }.let {
                if (solved[i]) examples[i].answer.toString() else it
            }
            field.alpha = if (!solved[i] && answers[i].isEmpty()) 0.55f else 1f
            val (stroke, fill, width) = when {
                solved[i] -> Triple(color(R.color.quiz_ok), color(R.color.quiz_ok_fill), 3)
                wrongNow[i] && i == active -> Triple(color(R.color.rest_error), color(R.color.rest_card), 4)
                i == active && !finished -> Triple(color(R.color.rest_accent), color(R.color.rest_card), 4)
                wrongNow[i] -> Triple(color(R.color.rest_error), color(R.color.rest_card), 3)
                else -> Triple(color(R.color.rest_text_soft), color(R.color.rest_card), 2)
            }
            field.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(fill)
                setStroke(dp(width), stroke)
            }
            field.setTextColor(color(if (solved[i]) R.color.quiz_ok else R.color.rest_text))
        }
    }

    private fun updateTimer() {
        if (finished) return
        timerView.text = "⏱ $secondsLeft с"
        timerView.setTextColor(color(if (secondsLeft <= 10) R.color.rest_error else R.color.rest_accent))
    }

    private fun setFeedback(text: String, colorRes: Int) {
        feedbackView.text = text
        feedbackView.setTextColor(color(colorRes))
    }

    // ---------- Родитель ----------

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
        if (finished) return
        hideKeyboard()
        pinEdit?.text?.clear()
        errorView?.text = ""
        parentPanel?.visibility = View.GONE
        parentButton?.visibility = View.VISIBLE
        root?.requestFocus()
        root?.let { hideSystemBars(it) }
    }

    private fun tryParentSkip() {
        val wait = prefs.lockoutSecondsLeft()
        if (wait > 0) {
            errorView?.text = "Слишком много попыток. Подождите $wait с"
            return
        }
        val pin = pinEdit?.text?.toString().orEmpty()
        if (prefs.checkPin(pin)) {
            hideKeyboard()
            finished = true
            onParentSkip()
        } else {
            pinEdit?.text?.clear()
            val lock = prefs.lockoutSecondsLeft()
            errorView?.text = if (lock > 0) "Слишком много попыток. Подождите $lock с" else "Неверный PIN"
        }
    }

    // ---------- Вспомогательное ----------

    private fun shake(v: View) {
        ObjectAnimator.ofFloat(v, "translationX", 0f, 22f, -22f, 16f, -16f, 8f, 0f)
            .setDuration(450)
            .start()
    }

    private fun bounce(v: View) {
        ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.25f, 1f).setDuration(500).start()
        ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.25f, 1f).setDuration(500).start()
    }

    private fun keyBackground(fill: Int): RippleDrawable {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(fill)
        }
        return RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), shape, null)
    }

    private fun hideKeyboard() {
        val edit = pinEdit ?: return
        context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(edit.windowToken, 0)
    }

    private fun color(res: Int): Int = ContextCompat.getColor(context, res)

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

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

    companion object {
        /** Сколько показывать «Молодец!» с довольным котиком. */
        private const val SUCCESS_SHOW_MS = 3000L
    }
}

package com.vinsvova.alicescreensaver

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Typeface
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
 * Полноэкранное окно с заданиями поверх всех приложений.
 * Задания показываются по одному. Пока текущее не решено верно, следующее не откроется,
 * а окно не закроется (родитель может пропустить задания по PIN).
 *
 * onDone получает список заданий, в которых ребёнок хотя бы раз ошибся или не успел.
 */
class QuizOverlay(
    private val context: Context,
    private val prefs: Prefs,
    private val tasks: List<Task>,
    private val isRetry: Boolean,
    private val answerSeconds: Int,
    private val onDone: (List<Task>) -> Unit,
    private val onParentSkip: () -> Unit
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val missed = BooleanArray(tasks.size)
    private var index = 0
    private var answer = ""
    private var wrongNow = false
    private var correctNow = false
    private var inputLocked = false
    private var finished = false
    private var secondsLeft = answerSeconds

    private var root: FrameLayout? = null
    private lateinit var catView: ImageView
    private lateinit var titleView: TextView
    private lateinit var feedbackView: TextView
    private lateinit var timerView: TextView
    private lateinit var taskRow: LinearLayout
    private lateinit var taskView: TextView
    private lateinit var answerView: TextView
    private lateinit var keypadView: LinearLayout
    private lateinit var digitsPad: LinearLayout
    private lateinit var signsPad: LinearLayout

    private var parentPanel: View? = null
    private var parentButton: View? = null
    private var pinEdit: EditText? = null
    private var errorView: TextView? = null

    val isShown: Boolean get() = root != null

    private val current: Task get() = tasks[index]

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (finished || root == null) return
            // Пока экран выключен или идёт переход к следующему заданию, время не уходит.
            if (power.isInteractive && !inputLocked) {
                secondsLeft--
                if (secondsLeft <= 0) onTimeUp()
            }
            updateTimer()
            handler.postDelayed(this, 1000)
        }
    }

    fun show() {
        if (root != null || tasks.isEmpty()) return
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
                            onKey(('0' + (event.keyCode - KeyEvent.KEYCODE_0)).toString())
                            return true
                        }
                        KeyEvent.KEYCODE_EQUALS -> { onKey("="); return true }
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
        taskRow = container.findViewById(R.id.quiz_task_row)
        taskView = container.findViewById(R.id.quiz_task)
        answerView = container.findViewById(R.id.quiz_answer)
        keypadView = container.findViewById(R.id.quiz_keypad)

        buildKeypads()

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

        loadTask()
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

    // ---------- Задание ----------

    private fun loadTask() {
        val task = current
        answer = ""
        wrongNow = false
        correctNow = false
        inputLocked = false
        secondsLeft = answerSeconds

        val prefix = if (isRetry) "Повторим. " else ""
        titleView.text = if (tasks.size > 1) "${prefix}Задание ${index + 1} из ${tasks.size}" else "${prefix}Реши задание"
        catView.setImageResource(R.drawable.cat_neutral)
        setFeedback(
            when {
                task.sign -> "Нажми знак и ✓"
                index == 0 -> "Котик ждёт ответ"
                else -> ""
            },
            R.color.rest_text_soft
        )

        taskView.text = task.text
        taskView.textSize = when {
            task.plain -> 60f
            task.text.length <= 40 -> 36f
            task.text.length <= 80 -> 30f
            else -> 25f
        }
        // Короткий пример: поле ответа справа, как в тетради. Иначе — под заданием.
        val lp = answerView.layoutParams as LinearLayout.LayoutParams
        if (task.plain) {
            taskRow.orientation = LinearLayout.HORIZONTAL
            lp.topMargin = 0
            lp.marginStart = dp(16)
        } else {
            taskRow.orientation = LinearLayout.VERTICAL
            lp.topMargin = dp(12)
            lp.marginStart = 0
        }
        answerView.layoutParams = lp

        digitsPad.visibility = if (task.sign) View.GONE else View.VISIBLE
        signsPad.visibility = if (task.sign) View.VISIBLE else View.GONE

        refreshAnswer()
        updateTimer()
    }

    private fun onKey(key: String) {
        if (finished || inputLocked) return
        if (current.sign) {
            if (key !in setOf(">", "<", "=")) return
            answer = key
        } else {
            if (key.length != 1 || !key[0].isDigit()) return
            if (answer.length >= 3) return
            if (answer.isEmpty() && key == "0") return
            answer += key
        }
        wrongNow = false
        refreshAnswer()
    }

    private fun onBackspace() {
        if (finished || inputLocked || answer.isEmpty()) return
        answer = answer.dropLast(1)
        refreshAnswer()
    }

    private fun check() {
        if (finished || inputLocked) return
        if (answer.isEmpty()) {
            setFeedback(if (current.sign) "Выбери знак" else "Впиши ответ", R.color.rest_text_soft)
            return
        }
        if (answer == current.answer) onCorrect() else onWrong("Подумай ещё!")
    }

    private fun onCorrect() {
        correctNow = true
        refreshAnswer()
        if (index == tasks.lastIndex) {
            success()
            return
        }
        inputLocked = true
        catView.setImageResource(R.drawable.cat_happy)
        bounce(catView)
        setFeedback("Верно!", R.color.quiz_ok)
        handler.postDelayed({
            if (finished || root == null) return@postDelayed
            index++
            loadTask()
        }, NEXT_TASK_DELAY_MS)
    }

    private fun onWrong(message: String) {
        missed[index] = true
        wrongNow = true
        answer = ""
        catView.setImageResource(R.drawable.cat_sad)
        shake(catView)
        setFeedback(message, R.color.rest_error)
        WrongFeedback.play(context, prefs)
        refreshAnswer()
    }

    private fun onTimeUp() {
        secondsLeft = answerSeconds
        onWrong("Время вышло! Подумай ещё!")
    }

    private fun success() {
        finished = true
        handler.removeCallbacks(timerRunnable)
        catView.setImageResource(R.drawable.cat_happy)
        bounce(catView)
        titleView.text = ""
        feedbackView.text = "Молодец! Так держать!"
        feedbackView.setTextColor(color(R.color.rest_accent))
        feedbackView.textSize = 40f
        timerView.visibility = View.GONE
        keypadView.visibility = View.INVISIBLE
        parentButton?.visibility = View.GONE
        parentPanel?.visibility = View.GONE
        hideKeyboard()
        val missedList = tasks.filterIndexed { i, _ -> missed[i] }
        handler.postDelayed({ onDone(missedList) }, SUCCESS_SHOW_MS)
    }

    private fun refreshAnswer() {
        val (stroke, fill, width) = when {
            correctNow -> Triple(color(R.color.quiz_ok), color(R.color.quiz_ok_fill), 3)
            wrongNow -> Triple(color(R.color.rest_error), color(R.color.rest_card), 4)
            else -> Triple(color(R.color.rest_accent), color(R.color.rest_card), 4)
        }
        answerView.background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            setColor(fill)
            setStroke(dp(width), stroke)
        }
        answerView.text = answer.ifEmpty { "?" }
        answerView.alpha = if (answer.isEmpty()) 0.55f else 1f
        answerView.setTextColor(color(if (correctNow) R.color.quiz_ok else R.color.rest_text))
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

    // ---------- Клавиатура ----------

    private fun buildKeypads() {
        digitsPad = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        ).forEach { digitsPad.addView(keyRow(it, 92, 66, 32f)) }

        signsPad = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        signsPad.addView(keyRow(listOf("<", "=", ">"), 92, 92, 48f))
        signsPad.addView(keyRow(listOf("⌫", "✓"), 141, 66, 32f))

        keypadView.addView(digitsPad)
        keypadView.addView(signsPad)
    }

    private fun keyRow(labels: List<String>, widthDp: Int, heightDp: Int, size: Float): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (label in labels) {
            val isCheck = label == "✓"
            val key = TextView(context).apply {
                text = label
                textSize = if (isCheck) 36f else size
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(if (isCheck) R.color.rest_background else R.color.rest_text))
                background = keyBackground(color(if (isCheck) R.color.rest_accent else R.color.quiz_key))
                isClickable = true
                isFocusable = false
                setOnClickListener {
                    when (label) {
                        "⌫" -> onBackspace()
                        "✓" -> check()
                        else -> onKey(label)
                    }
                }
            }
            row.addView(key, LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
                setMargins(dp(5), dp(5), dp(5), dp(5))
            })
        }
        return row
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

        /** Пауза после верного ответа перед следующим заданием. */
        private const val NEXT_TASK_DELAY_MS = 1200L
    }
}

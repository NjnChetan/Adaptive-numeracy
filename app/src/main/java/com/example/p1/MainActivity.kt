package com.example.p1

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

class MainActivity : AppCompatActivity() {

    private val engines = List(4) { AdaptiveEngine() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContentView(R.layout.activity_main)
        val topBar    = findViewById<View>(R.id.topWhiteBar)
        val bottomBar = findViewById<View>(R.id.bottomWhiteBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.layoutParams.height = top.takeIf { it > 0 } ?: (10 * resources.displayMetrics.density).toInt()
            v.requestLayout(); insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.layoutParams.height = bottom.takeIf { it > 0 } ?: (10 * resources.displayMetrics.density).toInt()
            v.requestLayout(); insets
        }
        setupPanel(R.id.panel1, R.id.clip1, engines[0])
        setupPanel(R.id.panel2, R.id.clip2, engines[1])
        setupPanel(R.id.panel3, R.id.clip3, engines[2])
        setupPanel(R.id.panel4, R.id.clip4, engines[3])
    }

    private fun ssp(ratio: Float, visW: Int, visH: Int): Float =
        (ratio * minOf(visW, visH).toFloat()) / resources.displayMetrics.scaledDensity

    private fun setupPanel(panelId: Int, clipId: Int, engine: AdaptiveEngine) {
        val panel        = findViewById<View>(panelId) ?: return
        val clipView     = findViewById<View>(clipId)  ?: return
        val panelContent = panel.findViewById<View>(R.id.panelContent) ?: return
        val digitLayout  = panel.findViewById<View>(R.id.digitLayout)  ?: return
        val homeLayout   = panel.findViewById<View>(R.id.homeLayout)   ?: return
        val quizLayout   = panel.findViewById<View>(R.id.quizLayout)   ?: return

        val btn1Digit      = panel.findViewById<MaterialButton>(R.id.btn1Digit) ?: return
        val btn2Digit      = panel.findViewById<MaterialButton>(R.id.btn2Digit) ?: return
        val btn3Digit      = panel.findViewById<MaterialButton>(R.id.btn3Digit) ?: return
        val digitNavHome   = panel.findViewById<View>(R.id.digitNavHome)
        val digitNavRotate = panel.findViewById<View>(R.id.digitNavRotate)

        val langToggle = panel.findViewById<SwitchCompat>(R.id.langToggle)
        val btnAdd     = panel.findViewById<MaterialButton>(R.id.btnAdd)   ?: return
        val btnSub     = panel.findViewById<MaterialButton>(R.id.btnSub)   ?: return
        val btnMul     = panel.findViewById<MaterialButton>(R.id.btnMul)   ?: return
        val btnDiv     = panel.findViewById<MaterialButton>(R.id.btnDiv)   ?: return
        val startBtn   = panel.findViewById<MaterialButton>(R.id.startBtn) ?: return

        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)
        val questionText = panel.findViewById<TextView>(R.id.questionText) ?: return
        val option1      = panel.findViewById<MaterialButton>(R.id.option1) ?: return
        val option2      = panel.findViewById<MaterialButton>(R.id.option2) ?: return
        val option3      = panel.findViewById<MaterialButton>(R.id.option3) ?: return
        val option4      = panel.findViewById<MaterialButton>(R.id.option4) ?: return
        val cardBorder   = panel.findViewById<View>(R.id.cardBorder)
        val btnRow1      = panel.findViewById<View>(R.id.btnRow1)
        val btnRow2      = panel.findViewById<View>(R.id.btnRow2)

        val homeNavHome   = panel.findViewById<View>(R.id.homeNavHome)
        val homeNavRotate = panel.findViewById<View>(R.id.homeNavRotate)
        val navHome       = panel.findViewById<View>(R.id.navHome)
        val navRotate     = panel.findViewById<View>(R.id.navRotate)

        val opButtons     = listOf(btnAdd, btnSub, btnMul, btnDiv)
        val answerButtons = listOf(option1, option2, option3, option4)
        val colorOn  = Color.parseColor("#2B3A8C")
        val colorOff = Color.parseColor("#8A99CC")
        val digitButtons = listOf(btn1Digit, btn2Digit, btn3Digit)

        scoreText?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(Color.parseColor("#1A2560"))
        }

        val normalBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#E0E0E0"))
        }
        val correctBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#2E7D32"))
        }
        val wrongBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#C62828"))
        }

        var selectedOp: String? = null
        var correct = 0
        var total   = 0
        var isMarathi = false

        langToggle?.setOnCheckedChangeListener { _, checked -> isMarathi = checked }

        fun toMarathi(s: String): String {
            val m = charArrayOf('०','१','२','३','४','५','६','७','८','९')
            return s.map { if (it.isDigit()) m[it - '0'] else it }.joinToString("")
        }
        fun loc(s: String) = if (isMarathi) toMarathi(s) else s

        fun selectDigit(mode: Int) {
            engine.applyDigitMode(mode)
            selectedOp = null
            opButtons.forEach { it.setBackgroundColor(colorOff) }
            startBtn.isEnabled = false
            startBtn.setBackgroundColor(colorOff)
            digitButtons.forEachIndexed { i, b ->
                b.setBackgroundColor(if (i + 1 == mode) colorOn else colorOff)
            }
            digitLayout.visibility = View.GONE
            homeLayout.visibility  = View.VISIBLE
        }

        btn1Digit.setOnClickListener { selectDigit(1) }
        btn2Digit.setOnClickListener { selectDigit(2) }
        btn3Digit.setOnClickListener { selectDigit(3) }

        fun selectOp(btn: MaterialButton, op: String) {
            selectedOp = op
            engine.setOperation(op)
            opButtons.forEach { it.setBackgroundColor(colorOff) }
            btn.setBackgroundColor(colorOn)
            startBtn.isEnabled = true
            startBtn.setBackgroundColor(colorOn)
        }

        btnAdd.setOnClickListener { selectOp(btnAdd, "+") }
        btnSub.setOnClickListener { selectOp(btnSub, "-") }
        btnMul.setOnClickListener { selectOp(btnMul, "×") }
        btnDiv.setOnClickListener { selectOp(btnDiv, "÷") }

        fun loadQuestion() {
            // Disable answer buttons while generating to prevent double-taps
            answerButtons.forEach { it.isEnabled = false }

            lifecycleScope.launch {
                // --- Heavy work on background thread ---
                val boundary = engine.consumeBoundary()

                if (boundary != null) {
                    // Show boundary message on UI thread
                    withContext(Dispatchers.Main) {
                        btnRow1?.visibility = View.GONE
                        btnRow2?.visibility = View.GONE
                        cardBorder?.background = normalBorder
                        val msg = "\n\nPreparing practice..."
                        questionText.setTextColor(Color.parseColor("#2B3A8C"))
                        questionText.text = loc(msg)
                    }
                    // Wait 4 s then load next question (still on bg thread delay via coroutine)
                    kotlinx.coroutines.delay(4000)
                    withContext(Dispatchers.Main) {
                        btnRow1?.visibility = View.VISIBLE
                        btnRow2?.visibility = View.VISIBLE
                    }
                    loadQuestion()
                    return@launch
                }

                // generateQuestion() may spin in do-while loops — run on Default dispatcher
                val (question, answers) = withContext(Dispatchers.Default) {
                    engine.generateQuestion()
                }

                // --- Back on Main thread to update UI ---
                withContext(Dispatchers.Main) {
                    cardBorder?.background = normalBorder
                    questionText.setTextColor(Color.parseColor("#1A1A2E"))
                    questionText.text = loc(question)
                    scoreText?.text = "$correct/$total"

                    answerButtons.forEachIndexed { i, btn ->
                        val label = loc(answers[i].toString())
                        btn.text = label
                        val digits = answers[i].toString().length
                        val sp = when {
                            digits >= 5 -> 13f
                            digits == 4 -> 15f
                            digits == 3 -> 17f
                            else        -> 19f
                        }
                        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
                        btn.isEnabled = true
                        btn.setOnClickListener {
                            answerButtons.forEach { it.isEnabled = false }
                            val ok = answers[i] == engine.correctAnswer
                            lifecycleScope.launch {
                                // submitAnswer also calls KL-UCB / CUSUM — offload it
                                withContext(Dispatchers.Default) {
                                    engine.submitAnswer(answers[i])
                                }
                                withContext(Dispatchers.Main) {
                                    total++
                                    if (ok) {
                                        correct++
                                        cardBorder?.background = correctBorder
                                    } else {
                                        cardBorder?.background = wrongBorder
                                        questionText.text = loc(engine.correctAnswer.toString())
                                        questionText.setTextColor(Color.parseColor("#C62828"))
                                    }
                                    scoreText?.text = "$correct/$total"
                                }
                                kotlinx.coroutines.delay(900)
                                loadQuestion()
                            }
                        }
                    }
                }
            }
        }

        startBtn.setOnClickListener {
            if (selectedOp == null) return@setOnClickListener
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            loadQuestion()
        }

        homeNavHome?.setOnClickListener {
            homeLayout.visibility  = View.GONE
            digitLayout.visibility = View.VISIBLE
        }
        navHome?.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }
        digitNavHome?.setOnClickListener { /* already on digit screen */ }

        fun applyScaledText(visW: Int, visH: Int) {
            if (visW <= 0 || visH <= 0) return
            scoreText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, ssp(0.07f, visW, visH))
            answerButtons.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, ssp(0.07f, visW, visH)) }
        }

        var rotationStep = 0
        var cumulativeDeg = 0f

        fun applyRotation(w: Int, h: Int, step: Int) {
            val portrait = step == 1 || step == 3
            val lp = panelContent.layoutParams
            lp.width  = if (portrait) h else w
            lp.height = if (portrait) w else h
            panelContent.layoutParams = lp
            panelContent.translationX = if (portrait) (w - h) / 2f else 0f
            panelContent.translationY = if (portrait) (h - w) / 2f else 0f
            val visW = if (portrait) h else w
            val visH = if (portrait) w else h
            applyScaledText(visW, visH)
            panelContent.animate().rotation(cumulativeDeg).scaleX(1f).scaleY(1f).setDuration(350).start()
        }

        clipView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var lastW = 0; private var lastH = 0
            override fun onGlobalLayout() {
                val w = clipView.width; val h = clipView.height
                if ((w != lastW || h != lastH) && w != 0 && h != 0) {
                    lastW = w; lastH = h
                    if (h > w && (rotationStep == 1 || rotationStep == 3)) rotationStep = 0
                    applyRotation(w, h, rotationStep)
                }
            }
        })

        panelContent.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val w = panelContent.width; val h = panelContent.height
                if (w > 0 && h > 0) {
                    val portrait = rotationStep == 1 || rotationStep == 3
                    applyScaledText(if (portrait) h else w, if (portrait) w else h)
                    panelContent.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })

        val isPhone = resources.configuration.smallestScreenWidthDp < 600

        val doRotate = {
            val w = clipView.width; val h = clipView.height
            if (isPhone) {
                rotationStep = if (rotationStep == 0) 2 else 0
                cumulativeDeg = if (rotationStep == 2) 180f else 360f
                clipView.post { if (w != 0 && h != 0) applyRotation(w, h, rotationStep) }
            } else {
                rotationStep = if (h > w) { if (rotationStep == 0) 2 else 0 } else (rotationStep + 1) % 4
                cumulativeDeg += 90f
                clipView.post { if (w != 0 && h != 0) applyRotation(w, h, rotationStep) }
            }
        }

        homeNavRotate?.setOnClickListener { doRotate() }
        navRotate?.setOnClickListener { doRotate() }
        digitNavRotate?.setOnClickListener { doRotate() }
    }
}
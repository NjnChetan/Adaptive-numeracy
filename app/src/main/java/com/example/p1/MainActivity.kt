package com.example.p1

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val engines = List(4) { AdaptiveEngine() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw edge-to-edge so app content goes behind status bar / camera cutout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Allow drawing into the camera cutout area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)

        // Dynamically set the top white bar height to exactly the status bar
        // + camera cutout inset so content starts cleanly below the notch
        val topBar = findViewById<View>(R.id.topWhiteBar)
        val bottomBar = findViewById<View>(R.id.bottomWhiteBar)
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.layoutParams.height = top.takeIf { it > 0 }
                ?: (10 * resources.displayMetrics.density).toInt()
            v.requestLayout()
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.layoutParams.height = bottom.takeIf { it > 0 }
                ?: (10 * resources.displayMetrics.density).toInt()
            v.requestLayout()
            insets
        }

        setupPanel(R.id.panel1, R.id.clip1, engines[0])
        setupPanel(R.id.panel2, R.id.clip2, engines[1])
        setupPanel(R.id.panel3, R.id.clip3, engines[2])
        setupPanel(R.id.panel4, R.id.clip4, engines[3])
    }

    private fun setupPanel(panelId: Int, clipId: Int, engine: AdaptiveEngine) {
        val panel        = findViewById<View>(panelId) ?: return
        val clipView     = findViewById<View>(clipId)  ?: return
        val panelContent = panel.findViewById<View>(R.id.panelContent) ?: return
        val homeLayout   = panel.findViewById<View>(R.id.homeLayout)   ?: return
        val quizLayout   = panel.findViewById<View>(R.id.quizLayout)   ?: return

        // Home layout helpers
        val homeMiddle      = panel.findViewById<LinearLayout>(R.id.homeMiddle)
        val opContainer     = panel.findViewById<LinearLayout>(R.id.opContainer)
        val startBtnWrapper = panel.findViewById<LinearLayout>(R.id.startBtnWrapper)

        val langToggle = panel.findViewById<SwitchCompat>(R.id.langToggle)

        val btnAdd   = panel.findViewById<MaterialButton>(R.id.btnAdd)   ?: return
        val btnSub   = panel.findViewById<MaterialButton>(R.id.btnSub)   ?: return
        val btnMul   = panel.findViewById<MaterialButton>(R.id.btnMul)   ?: return
        val btnDiv   = panel.findViewById<MaterialButton>(R.id.btnDiv)   ?: return
        val startBtn = panel.findViewById<MaterialButton>(R.id.startBtn) ?: return

        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)
        val questionText = panel.findViewById<TextView>(R.id.questionText)   ?: return
        val feedbackText = panel.findViewById<TextView>(R.id.feedbackText)
        val option1      = panel.findViewById<MaterialButton>(R.id.option1)  ?: return
        val option2      = panel.findViewById<MaterialButton>(R.id.option2)  ?: return
        val option3      = panel.findViewById<MaterialButton>(R.id.option3)  ?: return
        val option4      = panel.findViewById<MaterialButton>(R.id.option4)  ?: return
        val cardBorder   = panel.findViewById<View>(R.id.cardBorder)

        val homeNavHome   = panel.findViewById<View>(R.id.homeNavHome)
        val homeNavRotate = panel.findViewById<View>(R.id.homeNavRotate)
        val navHome       = panel.findViewById<View>(R.id.navHome)
        val navRotate     = panel.findViewById<View>(R.id.navRotate)

        val opButtons     = listOf(btnAdd, btnSub, btnMul, btnDiv)
        val answerButtons = listOf(option1, option2, option3, option4)
        val colorOn       = Color.parseColor("#2B3A8C")
        val colorOff      = Color.parseColor("#8A99CC")

        scoreText?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 12f
            setColor(Color.parseColor("#1A2560"))
        }
        val wrongBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#C62828"))
        }
        val normalBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#E0E0E0"))
        }

        var selectedOp: String? = null
        var correct = 0; var total = 0
        var isMarathi = false

        // ── Language ──────────────────────────────────────────────────────────
        langToggle?.setOnCheckedChangeListener { _, checked -> isMarathi = checked }

        fun toMarathi(s: String): String {
            val m = charArrayOf('०','१','२','३','४','५','६','७','८','९')
            return s.map { if (it.isDigit()) m[it - '0'] else it }.joinToString("")
        }
        fun loc(s: String) = if (isMarathi) toMarathi(s) else s

        // ── Operation selection ───────────────────────────────────────────────
        fun selectOp(btn: MaterialButton, op: String) {
            selectedOp = op
            opButtons.forEach { it.setBackgroundColor(colorOff) }
            btn.setBackgroundColor(colorOn)
            startBtn.isEnabled = true
            startBtn.setBackgroundColor(colorOn)
        }

        btnAdd.setOnClickListener { selectOp(btnAdd, "+") }
        btnSub.setOnClickListener { selectOp(btnSub, "-") }
        btnMul.setOnClickListener { selectOp(btnMul, "×") }
        btnDiv.setOnClickListener { selectOp(btnDiv, "÷") }

        // ── Quiz ──────────────────────────────────────────────────────────────
        fun loadQuestion() {
            feedbackText?.visibility = View.GONE
            cardBorder?.background = normalBorder
            val (question, answers) = engine.generateQuestion()
            questionText.text = loc(formatQuestion(question))
            scoreText?.text = "$correct/$total"
            answerButtons.forEach { it.isEnabled = true }
            answerButtons.forEachIndexed { i, btn ->
                btn.text = loc(answers[i].toString())
                btn.setOnClickListener {
                    answerButtons.forEach { it.isEnabled = false }
                    val ok = answers[i] == engine.correctAnswer
                    engine.submitAnswer(answers[i])
                    total++
                    if (ok) correct++
                    else {
                        cardBorder?.background = wrongBorder
                        cardBorder?.postDelayed({ cardBorder.background = normalBorder }, 700)
                    }
                    scoreText?.text = "$correct/$total"
                    feedbackText?.text = loc(if (ok) "✓ Correct!" else "✗ Answer: ${engine.correctAnswer}")
                    feedbackText?.setTextColor(if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
                    feedbackText?.visibility = View.VISIBLE
                    questionText.postDelayed({ loadQuestion() }, 900)
                }
            }
        }

        startBtn.setOnClickListener {
            if (selectedOp == null) return@setOnClickListener
            engine.activeOps = setOf(selectedOp!!)
            engine.initZPD()
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            loadQuestion()
        }

        homeNavHome?.setOnClickListener { /* already on home screen */ }
        navHome?.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }

        // ── Home layout: always vertical (portrait-style) regardless of orientation
        fun applyHomeLayout(visW: Int, visH: Int) {
            val m  = homeMiddle      ?: return
            val oc = opContainer     ?: return
            val sw = startBtnWrapper ?: return

            m.orientation = LinearLayout.VERTICAL
            (oc.layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT; height = 0; weight = 1f
            }
            (sw.layoutParams as LinearLayout.LayoutParams).apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            sw.gravity = android.view.Gravity.CENTER_HORIZONTAL
            m.requestLayout()
        }

        // ── Text scaling ──────────────────────────────────────────────────────
        fun pxToSp(px: Float): Float = px / resources.displayMetrics.scaledDensity

        fun applyScaledText(cellH: Int) {
            val h = cellH.toFloat()
            questionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, pxToSp(h * 0.11f))
            scoreText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, pxToSp(h * 0.05f))
            feedbackText?.setTextSize(TypedValue.COMPLEX_UNIT_SP, pxToSp(h * 0.05f))
            opButtons.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, pxToSp(h * 0.09f)) }
            startBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, pxToSp(h * 0.06f))
            answerButtons.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, pxToSp(h * 0.07f)) }
        }

        // ── Rotation ──────────────────────────────────────────────────────────
        var rotationStep = 0

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
            applyScaledText(visH)
            applyHomeLayout(visW, visH)
            panelContent.animate()
                .rotation(step * 90f)
                .scaleX(1f).scaleY(1f)
                .setDuration(350).start()
        }

        clipView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var lastW = 0; private var lastH = 0
            override fun onGlobalLayout() {
                val w = clipView.width; val h = clipView.height
                if ((w != lastW || h != lastH) && w != 0 && h != 0) {
                    lastW = w; lastH = h
                    // If cell is now portrait but step is sideways (1 or 3), snap to 0
                    if (h > w && (rotationStep == 1 || rotationStep == 3)) rotationStep = 0
                    applyRotation(w, h, rotationStep)
                }
            }
        })

        val doRotate = {
            val w = clipView.width; val h = clipView.height
            val cellIsPortrait = h > w   // physical cell shape on screen
            if (cellIsPortrait) {
                // Portrait cell: only 0° and 180° (steps 0 and 2)
                rotationStep = if (rotationStep == 0) 2 else 0
            } else {
                // Landscape cell: full 4-direction cycle
                rotationStep = (rotationStep + 1) % 4
            }
            clipView.post {
                if (w != 0 && h != 0) applyRotation(w, h, rotationStep)
            }
        }

        homeNavRotate?.setOnClickListener { doRotate() }
        navRotate?.setOnClickListener { doRotate() }
    }

    private fun formatQuestion(question: String): String {
        if (question.startsWith("🎉")) return question
        val op = listOf("+", "-", "−", "×", "÷").firstOrNull { question.contains(" $it ") } ?: return question
        val parts = question.replace(" = ?", "").split(" $op ")
        if (parts.size != 2) return question
        val top = parts[0].trim(); val bot = parts[1].trim()
        val displayOp = op   // keep the original symbol (− for subtraction)
        val width = maxOf(top.length, bot.length + 1)
        return "${top.padStart(width)}\n${("$displayOp$bot").padStart(width)}"
    }
}
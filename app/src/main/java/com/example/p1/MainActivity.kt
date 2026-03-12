package com.example.p1

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val engines = List(4) { AdaptiveEngine() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No requestedOrientation — app rotates freely
        setContentView(R.layout.activity_main)

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

        val homeMiddle = panel.findViewById<LinearLayout>(R.id.homeMiddle)
        val opContainer = panel.findViewById<LinearLayout>(R.id.opContainer)
        val startBtnWrapper = panel.findViewById<LinearLayout>(R.id.startBtnWrapper)

        val langToggle      = panel.findViewById<SwitchCompat>(R.id.langToggle)
        val btnAdd          = panel.findViewById<MaterialButton>(R.id.btnAdd)   ?: return
        val btnSub          = panel.findViewById<MaterialButton>(R.id.btnSub)   ?: return
        val btnMul          = panel.findViewById<MaterialButton>(R.id.btnMul)   ?: return
        val btnDiv          = panel.findViewById<MaterialButton>(R.id.btnDiv)   ?: return
        val startBtn        = panel.findViewById<MaterialButton>(R.id.startBtn) ?: return

        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)
        val questionText = panel.findViewById<TextView>(R.id.questionText)   ?: return
        val feedbackText = panel.findViewById<TextView>(R.id.feedbackText)
        val option1      = panel.findViewById<MaterialButton>(R.id.option1)  ?: return
        val option2      = panel.findViewById<MaterialButton>(R.id.option2)  ?: return
        val option3      = panel.findViewById<MaterialButton>(R.id.option3)  ?: return
        val option4      = panel.findViewById<MaterialButton>(R.id.option4)  ?: return
        val cardBorder   = panel.findViewById<View>(R.id.cardBorder)

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

        langToggle?.setOnCheckedChangeListener { _, checked -> isMarathi = checked }

        fun toMarathi(s: String): String {
            val m = charArrayOf('०','१','२','३','४','५','६','७','८','९')
            return s.map { if (it.isDigit()) m[it - '0'] else it }.joinToString("")
        }
        fun loc(s: String) = if (isMarathi) toMarathi(s) else s

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
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            loadQuestion()
        }

        panel.findViewById<View>(R.id.navHome)?.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }

        // ── Op button layout: 2×2 vs 1×4 ────────────────────────────────────
        // In a short/wide cell (landscape phone) the 2×2 grid rows are too
        // small and buttons collapse to dots.
        // Fix: when cell width > height, flatten to a single horizontal row.
        // The opContainer stays vertical with weight, but we move all 4 buttons
        // into opRow1 and hide opRow2 (which becomes empty).

        // ── Home layout: tall cell → vertical stack, wide cell → horizontal split ─
        // visW/visH are the visible dimensions of the cell (after rotation applied)
        fun applyHomeLayout(visW: Int, visH: Int) {
            val m = homeMiddle ?: return
            val oc = opContainer ?: return
            val sw = startBtnWrapper ?: return
            val isWide = visW > visH * 1.2f   // wide = landscape phone cell

            if (isWide) {
                // Horizontal: opContainer (2×2) on left | startBtnWrapper on right
                m.orientation = LinearLayout.HORIZONTAL
                (oc.layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    weight = 1f
                }
                (sw.layoutParams as LinearLayout.LayoutParams).apply {
                    width = 0
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    weight = 1f
                }
                sw.gravity = android.view.Gravity.CENTER
            } else {
                // Vertical: opContainer fills, startBtnWrapper below
                m.orientation = LinearLayout.VERTICAL
                (oc.layoutParams as LinearLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = 0
                    weight = 1f
                }
                (sw.layoutParams as LinearLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    weight = 0f
                }
                sw.gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            m.requestLayout()
        }

        // Always 2×2 grid regardless of orientation

        // ── Text scaling ──────────────────────────────────────────────────────
        // All sizes derived from cell height so they stay proportional on any screen.
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
            applyScaledText(if (portrait) w else h)
            applyHomeLayout(if (portrait) h else w, if (portrait) w else h)
            panelContent.animate()
                .rotation(step * 90f)
                .scaleX(1f).scaleY(1f)
                .setDuration(350).start()
        }

        // Re-apply on device rotation
        clipView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var lastW = 0; private var lastH = 0
            override fun onGlobalLayout() {
                val w = clipView.width; val h = clipView.height
                if ((w != lastW || h != lastH) && w != 0 && h != 0) {
                    lastW = w; lastH = h
                    applyRotation(w, h, rotationStep)
                }
            }
        })

        val doRotate = {
            rotationStep = (rotationStep + 1) % 4
            clipView.post {
                val w = clipView.width; val h = clipView.height
                if (w != 0 && h != 0) applyRotation(w, h, rotationStep)
            }
        }

        panel.findViewById<View>(R.id.navRotate)?.setOnClickListener { doRotate() }
        panel.findViewById<View>(R.id.homeNavRotate)?.setOnClickListener { doRotate() }
    }

    private fun formatQuestion(question: String): String {
        if (question.startsWith("🎉")) return question
        val op = listOf("+", "-", "×", "÷").firstOrNull { question.contains(" $it ") } ?: return question
        val parts = question.replace(" = ?", "").split(" $op ")
        if (parts.size != 2) return question
        val top = parts[0].trim(); val bot = parts[1].trim()
        val width = maxOf(top.length, bot.length + 1)
        return "${top.padStart(width)}\n${("$op$bot").padStart(width)}"
    }
}
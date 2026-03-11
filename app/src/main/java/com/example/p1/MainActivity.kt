package com.example.p1

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val engines = List(4) { AdaptiveEngine() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        setContentView(R.layout.activity_main)

        setupPanel(R.id.panel1, R.id.clip1, engines[0])
        setupPanel(R.id.panel2, R.id.clip2, engines[1])
        setupPanel(R.id.panel3, R.id.clip3, engines[2])
        setupPanel(R.id.panel4, R.id.clip4, engines[3])
    }

    private fun setupPanel(panelId: Int, clipId: Int, engine: AdaptiveEngine) {
        val panel    = findViewById<View>(panelId) ?: return
        val clipView = findViewById<View>(clipId)  ?: return
        val panelContent = panel.findViewById<View>(R.id.panelContent) ?: return

        val homeLayout = panel.findViewById<View>(R.id.homeLayout) ?: return
        val quizLayout = panel.findViewById<View>(R.id.quizLayout) ?: return

        // ── Home screen views ─────────────────────────────────────────────────
        val langToggle = panel.findViewById<SwitchCompat>(R.id.langToggle)
        val btnAdd     = panel.findViewById<MaterialButton>(R.id.btnAdd)   ?: return
        val btnSub     = panel.findViewById<MaterialButton>(R.id.btnSub)   ?: return
        val btnMul     = panel.findViewById<MaterialButton>(R.id.btnMul)   ?: return
        val btnDiv     = panel.findViewById<MaterialButton>(R.id.btnDiv)   ?: return
        val startBtn   = panel.findViewById<MaterialButton>(R.id.startBtn) ?: return
        val opButtons  = listOf(btnAdd, btnSub, btnMul, btnDiv)

        // ── Quiz screen views ─────────────────────────────────────────────────
        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)
        val questionText = panel.findViewById<TextView>(R.id.questionText) ?: return
        val feedbackText = panel.findViewById<TextView>(R.id.feedbackText)
        val option1      = panel.findViewById<MaterialButton>(R.id.option1) ?: return
        val option2      = panel.findViewById<MaterialButton>(R.id.option2) ?: return
        val option3      = panel.findViewById<MaterialButton>(R.id.option3) ?: return
        val option4      = panel.findViewById<MaterialButton>(R.id.option4) ?: return
        val answerButtons = listOf(option1, option2, option3, option4)

        // ── Score badge ───────────────────────────────────────────────────────
        scoreText?.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(Color.parseColor("#1A2560"))
        }

        // ── State ─────────────────────────────────────────────────────────────
        var selectedOp: String? = null
        var correct = 0
        var total   = 0

        val colorSelected   = Color.parseColor("#2B3A8C")
        val colorUnselected = Color.parseColor("#8A99CC")

        fun selectOp(btn: MaterialButton, op: String) {
            selectedOp = op
            opButtons.forEach { it.setBackgroundColor(colorUnselected) }
            btn.setBackgroundColor(colorSelected)
            startBtn.isEnabled = true
            startBtn.setBackgroundColor(colorSelected)
        }

        btnAdd.setOnClickListener { selectOp(btnAdd, "+") }
        btnSub.setOnClickListener { selectOp(btnSub, "-") }
        btnMul.setOnClickListener { selectOp(btnMul, "×") }
        btnDiv.setOnClickListener { selectOp(btnDiv, "÷") }

        // ── Language toggle ───────────────────────────────────────────────────
        var isMarathi = false
        langToggle?.isEnabled = true
        langToggle?.setOnCheckedChangeListener { _, checked -> isMarathi = checked }

        // ── Quiz helpers ──────────────────────────────────────────────────────
        fun updateScore() { scoreText?.text = "$correct/$total" }
        fun setAnswersEnabled(enabled: Boolean) = answerButtons.forEach { it.isEnabled = enabled }

        val cardBorder = panel.findViewById<View>(R.id.cardBorder)
        val wrongBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#C62828"))
        }
        val normalBorder = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = 28f
            setColor(Color.WHITE); setStroke(6, Color.parseColor("#E0E0E0"))
        }

        fun flashWrong() {
            cardBorder?.background = wrongBorder
            cardBorder?.postDelayed({ cardBorder.background = normalBorder }, 700)
        }

        fun toMarathi(text: String): String {
            val m = charArrayOf('०','१','२','३','४','५','६','७','८','९')
            return text.map { if (it.isDigit()) m[it - '0'] else it }.joinToString("")
        }

        fun localise(s: String) = if (isMarathi) toMarathi(s) else s

        fun showFeedback(msg: String, ok: Boolean) {
            feedbackText?.text       = localise(msg)
            feedbackText?.setTextColor(if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
            feedbackText?.visibility = View.VISIBLE
        }

        fun hideFeedback() { feedbackText?.visibility = View.GONE }

        fun loadQuestion() {
            hideFeedback()
            cardBorder?.background = normalBorder
            val (question, answers) = engine.generateQuestion()
            questionText.text = localise(formatQuestion(question))
            setAnswersEnabled(true)

            answerButtons.forEachIndexed { i, btn ->
                btn.text = localise(answers[i].toString())
                btn.setOnClickListener {
                    setAnswersEnabled(false)
                    val ok = answers[i] == engine.correctAnswer
                    engine.submitAnswer(answers[i])
                    total++
                    if (ok) correct++ else flashWrong()
                    updateScore()
                    showFeedback(if (ok) "✓ Correct!" else "✗ Answer: ${engine.correctAnswer}", ok)
                    questionText.postDelayed({ loadQuestion() }, 900)
                }
            }
        }

        // ── Start ─────────────────────────────────────────────────────────────
        startBtn.setOnClickListener {
            if (selectedOp == null) return@setOnClickListener
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            updateScore()
            loadQuestion()
        }

        // ── Nav: Home ─────────────────────────────────────────────────────────
        panel.findViewById<View>(R.id.navHome)?.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }

        // ── Nav: Rotate — 4-direction cycle ───────────────────────────────────
        //
        // Cell is W × H (landscape, W > H). panelContent sits inside a
        // FrameLayout (panel) that is also W × H. By default panelContent is
        // laid out at position (0,0) with size W × H.
        //
        // Rotation pivot is the VIEW'S OWN CENTRE (pivotX = viewW/2, pivotY = viewH/2).
        //
        // For landscape steps (0°, 180°):
        //   panelContent size = W × H, pivot = (W/2, H/2)
        //   No translation needed — centre aligns with cell centre.
        //
        // For portrait steps (90°, 270°):
        //   We keep panelContent size = W × H (same as cell) and use
        //   scaleX = H/W, scaleY = H/W so after rotation:
        //     visual width  = H * (H/W) ... no wait, let's be precise:
        //
        //   Correct portrait approach — keep size W×H, scale = H/W:
        //     After 90° rotation the view's W axis is now vertical and H axis horizontal.
        //     Scale H/W applied uniformly:
        //       visual width  = W * (H/W) = H   → but cell width is W, so doesn't fill! ✗
        //
        //   Correct portrait approach — keep size W×H, scale = W/H:
        //       visual width  = W * (W/H) = W²/H  > W  → overflows ✗
        //
        //   The ONLY way to fill W×H with a rotated view whose own size is W×H:
        //     After 90° rotation: view's rendered width = H, rendered height = W.
        //     To fill cell (width W, height H): scaleX = W/H, scaleY = W/H.
        //     BUT: visual width = H*(W/H) = W ✓, visual height = W*(W/H) = W²/H > H ✗ overflows.
        //
        //   There is NO uniform scale that fills W×H from a rotated W×H view when W≠H.
        //   Solution: resize the view itself.
        //     Set panelContent to H × W (portrait: narrow width H, tall height W).
        //     Pivot = (H/2, W/2) — centre of the resized view.
        //     The centre of the cell is at (W/2, H/2) in panel coordinates.
        //     panelContent top-left is at (0, 0), so its centre is at (H/2, W/2).
        //     Cell centre is at (W/2, H/2).
        //     Translation needed:
        //       translationX = cellCentreX - viewCentreX = W/2 - H/2 = (W-H)/2
        //       translationY = cellCentreY - viewCentreY = H/2 - W/2 = (H-W)/2
        //     After 90° rotation from its own centre, visual footprint = W × H. ✓
        //     No scale needed.

        var rotationStep = 0

        fun applyOrientationStyle(portrait: Boolean) {
            questionText.textSize = if (portrait) 36f else 26f
            scoreText?.textSize   = if (portrait) 13f else 11f
            val d    = resources.displayMetrics.density
            val btnH = ((if (portrait) 54 else 42) * d).toInt()
            answerButtons.forEach { btn ->
                btn.layoutParams = btn.layoutParams.also { it.height = btnH }
                btn.textSize = if (portrait) 17f else 14f
            }
        }

        fun applyRotation(w: Int, h: Int, step: Int) {
            val portrait = (step == 1 || step == 3)
            val degrees  = step * 90f

            if (portrait) {
                // Resize to H × W so the rotated view exactly fills the W × H cell
                val lp = panelContent.layoutParams
                lp.width  = h
                lp.height = w
                panelContent.layoutParams = lp

                // After resize, panelContent top-left is still at (0,0).
                // Its centre is at (h/2, w/2) in panel coords.
                // Cell centre is at (w/2, h/2).
                // Translate so the view centre sits on the cell centre.
                val tx = (w - h) / 2f
                val ty = (h - w) / 2f
                panelContent.translationX = tx
                panelContent.translationY = ty
            } else {
                // Restore to W × H, no translation
                val lp = panelContent.layoutParams
                lp.width  = w
                lp.height = h
                panelContent.layoutParams = lp
                panelContent.translationX = 0f
                panelContent.translationY = 0f
            }

            applyOrientationStyle(portrait)

            panelContent.animate()
                .rotation(degrees)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .start()
        }

        panel.findViewById<View>(R.id.navRotate)?.setOnClickListener {
            rotationStep = (rotationStep + 1) % 4

            clipView.post {
                val w = clipView.width
                val h = clipView.height
                if (w == 0 || h == 0) return@post
                applyRotation(w, h, rotationStep)
            }
        }
    }

    // ── Question formatter ────────────────────────────────────────────────────

    private fun formatQuestion(question: String): String {
        if (question.startsWith("🎉")) return question
        val op = listOf("+", "-", "×", "÷").firstOrNull { question.contains(" $it ") }
            ?: return question
        val parts = question.replace(" = ?", "").split(" $op ")
        if (parts.size != 2) return question
        val top   = parts[0].trim()
        val bot   = parts[1].trim()
        val width = maxOf(top.length, bot.length + 1)
        return "${top.padStart(width)}\n${("$op$bot").padStart(width)}"
    }
}




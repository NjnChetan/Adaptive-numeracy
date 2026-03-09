package com.example.p1

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
        setContentView(R.layout.activity_main)

        setupPanel(R.id.panel1, engines[0])
        setupPanel(R.id.panel2, engines[1])
        setupPanel(R.id.panel3, engines[2])
        setupPanel(R.id.panel4, engines[3])
    }

    private fun setupPanel(panelId: Int, engine: AdaptiveEngine) {
        val panel = findViewById<View>(panelId) ?: return

        val homeLayout = panel.findViewById<View>(R.id.homeLayout) ?: return
        val quizLayout = panel.findViewById<View>(R.id.quizLayout) ?: return

        // ── Home screen views ─────────────────────────────────────────────────
        val langToggle = panel.findViewById<SwitchCompat>(R.id.langToggle)

        val btnAdd  = panel.findViewById<MaterialButton>(R.id.btnAdd)  ?: return
        val btnSub  = panel.findViewById<MaterialButton>(R.id.btnSub)  ?: return
        val btnMul  = panel.findViewById<MaterialButton>(R.id.btnMul)  ?: return
        val btnDiv  = panel.findViewById<MaterialButton>(R.id.btnDiv)  ?: return
        val startBtn = panel.findViewById<MaterialButton>(R.id.startBtn) ?: return

        val opButtons = listOf(btnAdd, btnSub, btnMul, btnDiv)

        // ── Quiz screen views ─────────────────────────────────────────────────
        val backBtn      = panel.findViewById<View>(R.id.backBtn)           ?: return
        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)
        val questionText = panel.findViewById<TextView>(R.id.questionText)  ?: return

        val option1 = panel.findViewById<MaterialButton>(R.id.option1) ?: return
        val option2 = panel.findViewById<MaterialButton>(R.id.option2) ?: return
        val option3 = panel.findViewById<MaterialButton>(R.id.option3) ?: return
        val option4 = panel.findViewById<MaterialButton>(R.id.option4) ?: return
        val answerButtons = listOf(option1, option2, option3, option4)

        // ── Score badge: GradientDrawable bypasses Material theme tinting ─────
        scoreText?.background = GradientDrawable().apply {
            shape        = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(Color.parseColor("#1A2560"))
        }

        // ── State ─────────────────────────────────────────────────────────────
        var selectedOp: String? = null   // "+", "-", "×", "÷"
        var correct    = 0
        var total      = 0

        // ── Colour constants ──────────────────────────────────────────────────
        val colorSelected   = Color.parseColor("#2B3A8C")   // dark navy  — chosen op
        val colorUnselected = Color.parseColor("#8A99CC")   // soft blue  — unchosen ops
        val colorStartOn    = Color.parseColor("#2B3A8C")   // dark navy  — start enabled
        val colorStartOff   = Color.parseColor("#8A99CC")   // soft blue  — start disabled

        // ── Operation button selection ────────────────────────────────────────
        fun selectOp(btn: MaterialButton, op: String) {
            selectedOp = op
            opButtons.forEach { it.setBackgroundColor(colorUnselected) }
            btn.setBackgroundColor(colorSelected)
            // Enable Start
            startBtn.isEnabled = true
            startBtn.setBackgroundColor(colorStartOn)
        }

        btnAdd.setOnClickListener { selectOp(btnAdd, "+") }
        btnSub.setOnClickListener { selectOp(btnSub, "-") }
        btnMul.setOnClickListener { selectOp(btnMul, "×") }
        btnDiv.setOnClickListener { selectOp(btnDiv, "÷") }

        // langToggle is wired up but translation is not yet implemented
        langToggle?.isEnabled = false

        // ── Quiz helpers ──────────────────────────────────────────────────────
        fun updateScore() { scoreText?.text = "$correct/$total" }

        fun setAnswersEnabled(enabled: Boolean) = answerButtons.forEach { it.isEnabled = enabled }

        fun loadQuestion() {
            val (question, answers) = engine.generateQuestion()
            questionText.text = formatQuestion(question)
            setAnswersEnabled(true)

            answerButtons.forEachIndexed { i, btn ->
                btn.text = answers[i].toString()
                btn.setOnClickListener {
                    setAnswersEnabled(false)
                    val feedback = engine.submitAnswer(answers[i])
                    total++
                    if (feedback.startsWith("Correct")) correct++
                    updateScore()
                    questionText.text = feedback
                    questionText.postDelayed({ loadQuestion() }, 800)
                }
            }
        }

        // ── Start button ──────────────────────────────────────────────────────
        startBtn.setOnClickListener {
            if (selectedOp == null) return@setOnClickListener
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            updateScore()
            loadQuestion()
        }

        // ── Back button (quiz → home) ─────────────────────────────────────────
        backBtn.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }
    }

    /**
     * Formats "282 + 204 = ?" into stacked column-addition style:
     *   " 282"
     *   "+204"
     */
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
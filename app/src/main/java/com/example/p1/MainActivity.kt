package com.example.p1

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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

        val homeLayout   = panel.findViewById<View>(R.id.homeLayout)      ?: return
        val quizLayout   = panel.findViewById<View>(R.id.quizLayout)      ?: return

        val startBtn     = panel.findViewById<View>(R.id.startBtn)         ?: return
        // backBtn is a TextView in question_panel.xml — cast as View to avoid ClassCastException
        val backBtn      = panel.findViewById<View>(R.id.backBtn)          ?: return

        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)

        // Force navy background in code — the Material theme overrides
        // android:backgroundTint on TextViews with colorPrimary (red) even
        // when backgroundTint="@null" is set in XML. A GradientDrawable set
        // in code bypasses the theme tinting pipeline entirely.
        scoreText?.background = GradientDrawable().apply {
            shape         = GradientDrawable.RECTANGLE
            cornerRadius  = 12f
            setColor(Color.parseColor("#1A2560"))
        }
        val questionText = panel.findViewById<TextView>(R.id.questionText) ?: return

        val option1      = panel.findViewById<Button>(R.id.option1) ?: return
        val option2      = panel.findViewById<Button>(R.id.option2) ?: return
        val option3      = panel.findViewById<Button>(R.id.option3) ?: return
        val option4      = panel.findViewById<Button>(R.id.option4) ?: return

        val buttons = listOf(option1, option2, option3, option4)

        var correct = 0
        var total   = 0

        fun updateScore() {
            scoreText?.text = "$correct/$total"
        }

        fun setButtonsEnabled(enabled: Boolean) =
            buttons.forEach { it.isEnabled = enabled }

        fun loadQuestion() {
            val (question, answers) = engine.generateQuestion()
            questionText.text = formatQuestion(question)
            setButtonsEnabled(true)

            buttons.forEachIndexed { i, btn ->
                btn.text = answers[i].toString()
                btn.setOnClickListener {
                    setButtonsEnabled(false)
                    val feedback = engine.submitAnswer(answers[i])
                    total++
                    if (feedback.startsWith("Correct")) correct++
                    updateScore()
                    questionText.text = feedback
                    questionText.postDelayed({ loadQuestion() }, 800)
                }
            }
        }

        startBtn.setOnClickListener {
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            updateScore()
            loadQuestion()
        }

        backBtn.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }
    }

    /**
     * Formats "282 + 204 = ?" into stacked column-addition style:
     *   " 282"
     *  "+204"
     */
    private fun formatQuestion(question: String): String {
        if (!question.contains("+") || question.startsWith("🎉")) return question
        val parts = question.replace(" = ?", "").split(" + ")
        if (parts.size != 2) return question
        val top   = parts[0].trim()
        val bot   = parts[1].trim()
        val width = maxOf(top.length, bot.length + 1)
        return "${top.padStart(width)}\n${("+$bot").padStart(width)}"
    }
}
package com.example.p1

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val engines = List(4) { AdaptiveEngine() }
    private val panelRotations = mutableMapOf<Int, Int>()

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

        val homeLayout = panel.findViewById<View>(R.id.homeLayout)
        val quizLayout = panel.findViewById<View>(R.id.quizLayout)

        val navSettings = panel.findViewById<TextView>(R.id.navSettings)
        val navHome = panel.findViewById<TextView>(R.id.navHome)

        val langToggle = panel.findViewById<SwitchCompat>(R.id.langToggle)

        val btnAdd = panel.findViewById<MaterialButton>(R.id.btnAdd)
        val btnSub = panel.findViewById<MaterialButton>(R.id.btnSub)
        val btnMul = panel.findViewById<MaterialButton>(R.id.btnMul)
        val btnDiv = panel.findViewById<MaterialButton>(R.id.btnDiv)
        val startBtn = panel.findViewById<MaterialButton>(R.id.startBtn)

        val scoreText = panel.findViewById<TextView>(R.id.scoreText)
        val questionText = panel.findViewById<TextView>(R.id.questionText)
        val feedbackText = panel.findViewById<TextView>(R.id.feedbackText)

        val questionCard = panel.findViewById<CardView>(R.id.questionCard)

        val option1 = panel.findViewById<MaterialButton>(R.id.option1)
        val option2 = panel.findViewById<MaterialButton>(R.id.option2)
        val option3 = panel.findViewById<MaterialButton>(R.id.option3)
        val option4 = panel.findViewById<MaterialButton>(R.id.option4)

        val btnRow1 = panel.findViewById<LinearLayout>(R.id.btnRow1)
        val btnRow2 = panel.findViewById<LinearLayout>(R.id.btnRow2)

        val answerButtons = listOf(option1, option2, option3, option4)
        val opButtons = listOf(btnAdd, btnSub, btnMul, btnDiv)

        var selectedOp: String? = null
        var correct = 0
        var total = 0
        var currentLanguage = "en"

        val colorSelected = Color.parseColor("#2B3A8C")
        val colorUnselected = Color.parseColor("#8A99CC")

        panelRotations[panelId] = 0

        // PANEL ROTATION
        navSettings?.setOnClickListener {

            val currentRotation = panelRotations[panelId] ?: 0
            val newRotation = if (currentRotation == 0) 180 else 0

            panelRotations[panelId] = newRotation

            panel.animate()
                .rotation(newRotation.toFloat())
                .setDuration(300)
                .start()
        }

        // HOME BUTTON
        navHome?.setOnClickListener {
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }

        // LANGUAGE TOGGLE (ONLY THIS PANEL)
        langToggle?.setOnCheckedChangeListener { _, isChecked ->
            currentLanguage = if (isChecked) "mr" else "en"
        }

        fun selectOp(btn: MaterialButton, op: String) {

            selectedOp = op

            opButtons.forEach {
                it.setBackgroundColor(colorUnselected)
            }

            btn.setBackgroundColor(colorSelected)

            startBtn.isEnabled = true
        }

        btnAdd.setOnClickListener { selectOp(btnAdd, "+") }
        btnSub.setOnClickListener { selectOp(btnSub, "-") }
        btnMul.setOnClickListener { selectOp(btnMul, "×") }
        btnDiv.setOnClickListener { selectOp(btnDiv, "÷") }

        fun updateScore() {
            scoreText?.text = "$correct/$total"
        }

        fun setAnswersEnabled(enabled: Boolean) {
            answerButtons.forEach { it.isEnabled = enabled }
        }

        fun blinkBorder(panel: View, color: Int) {

            val borderView = panel.findViewById<View>(R.id.cardBorder)
            val drawable = borderView.background as GradientDrawable

            val normalColor = Color.parseColor("#E0E0E0")

            val animator = ValueAnimator.ofArgb(normalColor, color)
            animator.duration = 120
            animator.repeatMode = ValueAnimator.REVERSE
            animator.repeatCount = 5

            animator.addUpdateListener {
                val c = it.animatedValue as Int
                drawable.setStroke(8, c)
            }

            animator.start()
        }

        fun shakeCard(card: CardView?) {

            card?.animate()?.translationX(40f)?.setDuration(60)?.withEndAction {

                card.animate().translationX(-40f).setDuration(60).withEndAction {

                    card.animate().translationX(20f).setDuration(60).withEndAction {

                        card.animate().translationX(0f).setDuration(60)
                    }
                }
            }
        }

        fun loadQuestion() {

            val (question, answers) = engine.generateQuestion()

            if (currentLanguage == "mr") {
                questionText.text = convertToMarathiDigits(question)
            } else {
                questionText.text = question
            }

            feedbackText?.visibility = View.GONE

            setAnswersEnabled(true)

            answerButtons.forEachIndexed { i, btn ->

                btn.text = answers[i].toString()

                btn.setOnClickListener {

                    setAnswersEnabled(false)

                    val selectedAnswer = answers[i]
                    val feedback = engine.submitAnswer(selectedAnswer)

                    total++

                    if (feedback.startsWith("Correct")) {

                        correct++

                        feedbackText?.apply {
                            visibility = View.VISIBLE
                            text = "✔ Correct!"
                            setTextColor(Color.parseColor("#2E7D32"))
                        }

                        blinkBorder(panel, Color.parseColor("#2E7D32"))

                    } else {

                        val correctAnswer = engine.correctAnswer

                        feedbackText?.apply {
                            visibility = View.VISIBLE
                            text = "✘ Incorrect — $correctAnswer"
                            setTextColor(Color.RED)
                        }

                        blinkBorder(panel, Color.parseColor("#DC143C"))
                        shakeCard(questionCard)
                    }

                    updateScore()

                    panel.postDelayed({
                        loadQuestion()
                    }, 1200)
                }
            }
        }

        startBtn.setOnClickListener {

            if (selectedOp == null) return@setOnClickListener

            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE

            updateScore()
            loadQuestion()
        }
    }

    // MARATHI DIGIT CONVERTER
    private fun convertToMarathiDigits(text: String): String {

        val map = mapOf(
            '0' to '०',
            '1' to '१',
            '2' to '२',
            '3' to '३',
            '4' to '४',
            '5' to '५',
            '6' to '६',
            '7' to '७',
            '8' to '८',
            '9' to '९'
        )

        val result = StringBuilder()

        for (c in text) {
            result.append(map[c] ?: c)
        }

        return result.toString()
    }
}
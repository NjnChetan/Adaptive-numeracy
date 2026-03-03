package com.example.p1

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

        val panel = findViewById<View>(panelId)

        val questionText = panel.findViewById<TextView>(R.id.questionText)
        val option1 = panel.findViewById<Button>(R.id.option1)
        val option2 = panel.findViewById<Button>(R.id.option2)
        val option3 = panel.findViewById<Button>(R.id.option3)
        val option4 = panel.findViewById<Button>(R.id.option4)

        val buttons = listOf(option1, option2, option3, option4)

        fun loadQuestion() {
            val (question, answers) = engine.generateQuestion()
            questionText.text = question

            for (i in buttons.indices) {
                buttons[i].text = answers[i].toString()
                buttons[i].setOnClickListener {
                    val feedback = engine.submitAnswer(answers[i])
                    questionText.text = feedback

                    questionText.postDelayed({
                        loadQuestion()
                    }, 800)
                }
            }
        }

        loadQuestion()
    }}

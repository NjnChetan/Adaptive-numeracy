package com.example.p1

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.graphics.Typeface
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
    private lateinit var sessionLogger: SessionLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionLogger = SessionLogger(this)
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

    override fun onDestroy() {
        super.onDestroy()
        sessionLogger.endSession()
    }

    private fun ssp(ratio: Float, visW: Int, visH: Int): Float =
        (ratio * minOf(visW, visH).toFloat()) / resources.displayMetrics.scaledDensity

    private fun setupPanel(panelId: Int, clipId: Int, engine: AdaptiveEngine) {
        val panel        = findViewById<View>(panelId) ?: return
        val clipView     = findViewById<View>(clipId)  ?: return
        val panelContent = panel.findViewById<View>(R.id.panelContent) ?: return
        val homeLayout   = panel.findViewById<View>(R.id.homeLayout)   ?: return
        val quizLayout    = panel.findViewById<View>(R.id.quizLayout)   ?: return
        val summaryLayout = panel.findViewById<View>(R.id.summaryLayout) ?: return

        val btn1Digit      = panel.findViewById<MaterialButton>(R.id.btn1Digit) ?: return
        val btn2Digit      = panel.findViewById<MaterialButton>(R.id.btn2Digit) ?: return
        val btn3Digit      = panel.findViewById<MaterialButton>(R.id.btn3Digit) ?: return

        val langToggle = panel.findViewById<SwitchCompat>(R.id.langToggle)
        val btnAdd     = panel.findViewById<MaterialButton>(R.id.btnAdd)   ?: return
        val btnSub     = panel.findViewById<MaterialButton>(R.id.btnSub)   ?: return
        val btnMul     = panel.findViewById<MaterialButton>(R.id.btnMul)   ?: return
        val btnDiv     = panel.findViewById<MaterialButton>(R.id.btnDiv)   ?: return
        val startBtn   = panel.findViewById<MaterialButton>(R.id.startBtn) ?: return

        val scoreText    = panel.findViewById<TextView>(R.id.scoreText)
        val conceptsContainer = panel.findViewById<android.widget.LinearLayout>(R.id.conceptsContainer)
        val questionText = panel.findViewById<TextView>(R.id.questionText) ?: return
        val option1      = panel.findViewById<MaterialButton>(R.id.option1) ?: return
        val option2      = panel.findViewById<MaterialButton>(R.id.option2) ?: return
        val option3      = panel.findViewById<MaterialButton>(R.id.option3) ?: return
        val option4      = panel.findViewById<MaterialButton>(R.id.option4) ?: return
        val cardBorder   = panel.findViewById<View>(R.id.cardBorder)
        val btnRow1      = panel.findViewById<View>(R.id.btnRow1)
        val btnRow2      = panel.findViewById<View>(R.id.btnRow2)
        val finishBtn    = panel.findViewById<MaterialButton>(R.id.finishBtn)

        val homeNavRotate = panel.findViewById<View>(R.id.homeNavRotate)
        val navHome       = panel.findViewById<View>(R.id.navHome)
        val navRotate     = panel.findViewById<View>(R.id.navRotate)

        val summaryNavHome         = panel.findViewById<View>(R.id.summaryNavHome)
        val summaryScoreText       = panel.findViewById<TextView>(R.id.summaryScoreText)
        val summaryPercentText     = panel.findViewById<TextView>(R.id.summaryPercentText)
        val circularProgressBar    = panel.findViewById<android.widget.ProgressBar>(R.id.circularProgressBar)
        val summaryCorrectCount    = panel.findViewById<TextView>(R.id.summaryCorrectCount)
        val summaryIncorrectCount  = panel.findViewById<TextView>(R.id.summaryIncorrectCount)
        val performanceContainer   = panel.findViewById<android.widget.LinearLayout>(R.id.performanceContainer)

        val opButtons     = listOf(btnAdd, btnSub, btnMul, btnDiv)
        val answerButtons = listOf(option1, option2, option3, option4)
        val colorOn  = Color.parseColor("#2B3A8C")
        val colorOff = Color.parseColor("#8A99CC")
        val digitButtons = listOf(btn1Digit, btn2Digit, btn3Digit)

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
        var selectedDigitMode: Int? = null
        var correct = 0
        var total   = 0
        var isMarathi = false

        // --- Session Statistics ---
        val conceptStats = mutableMapOf<String, Pair<Int, Int>>() // Name -> (Correct, Total)
        val masteredLevels = mutableSetOf<String>()

        fun toMarathi(s: String): String {
            val m = charArrayOf('०','१','२','३','४','५','६','७','८','९')
            return s.map { if (it.isDigit()) m[it - '0'] else it }.joinToString("")
        }
        fun loc(s: String): String {
            if (!isMarathi) return s
            if (s.startsWith("Q: ")) return "प्रश्न: " + toMarathi(s.substring(3))
            val translated = when(s) {
                "Select Level" -> "पातळी निवडा"
                "Select Operation" -> "क्रिया निवडा"
                "Start" -> "सुरू करा"
                "⟳ Rotate" -> "⟳ फिरवा"
                "Finish" -> "संपवा"
                "Excellent performance!" -> "उत्कृष्ट कामगिरी!"
                "Score" -> "गुण"
                "Correct" -> "बरोबर"
                "Incorrect" -> "चूक"
                "How you did by topic" -> "विषयानुसार तुमची कामगिरी"
                "⌂ Home" -> "⌂ मुख्य पान"
                " Mastered!" -> " प्राविण्य मिळवले!"
                "\n\nPractice Starts" -> "\n\nसराव सुरू"
                "✓ Good" -> "✓ छान"
                "OK" -> "ठीक आहे"
                else -> s
            }
            return toMarathi(translated)
        }

        fun updateStaticTexts() {
            val levelTitle = panel.findViewById<TextView>(R.id.levelTitle)
            val opTitle = panel.findViewById<TextView>(R.id.opTitle)
            val homeNavRotate = panel.findViewById<TextView>(R.id.homeNavRotate)
            val summaryNavHome = panel.findViewById<TextView>(R.id.summaryNavHome)
            
            levelTitle?.text = loc("Select Level")
            opTitle?.text = loc("Select Operation")
            startBtn.text = loc("Start")
            homeNavRotate?.text = loc("⟳ Rotate")
            finishBtn?.text = loc("Finish")
            summaryNavHome?.text = loc("⌂ Home")
            
            panel.findViewById<TextView>(R.id.summaryScoreTitle)?.text = loc("Score")
            panel.findViewById<TextView>(R.id.summaryCorrectTitle)?.text = loc("Correct")
            panel.findViewById<TextView>(R.id.summaryIncorrectTitle)?.text = loc("Incorrect")
            panel.findViewById<TextView>(R.id.summaryTopicTitle)?.text = loc("How you did by topic")
            panel.findViewById<TextView>(R.id.summaryExcellentText)?.text = loc("Excellent performance!")
        }

        langToggle?.setOnCheckedChangeListener { _, checked -> 
            isMarathi = checked 
            updateStaticTexts()
        }

        fun updateStartButton() {
            val ready = selectedOp != null && selectedDigitMode != null
            startBtn.isEnabled = ready
            startBtn.setBackgroundColor(if (ready) colorOn else colorOff)
        }

        fun selectDigit(mode: Int) {
            selectedDigitMode = mode
            digitButtons.forEachIndexed { i, b ->
                b.setBackgroundColor(if (i + 1 == mode) colorOn else colorOff)
            }
            updateStartButton()
        }

        btn1Digit.setOnClickListener { selectDigit(1) }
        btn2Digit.setOnClickListener { selectDigit(2) }
        btn3Digit.setOnClickListener { selectDigit(3) }

        fun selectOp(btn: MaterialButton, op: String) {
            selectedOp = op
            opButtons.forEach { it.setBackgroundColor(colorOff) }
            btn.setBackgroundColor(colorOn)
            updateStartButton()
        }

        btnAdd.setOnClickListener { selectOp(btnAdd, "+") }
        btnSub.setOnClickListener { selectOp(btnSub, "-") }
        btnMul.setOnClickListener { selectOp(btnMul, "×") }
        btnDiv.setOnClickListener { selectOp(btnDiv, "÷") }

        fun showSummary() {
            sessionLogger.endSession()
            quizLayout.visibility = View.GONE
            summaryLayout.visibility = View.VISIBLE

            val perc = if (total > 0) (correct * 100) / total else 0
            summaryScoreText?.text = loc("$correct/$total")
            summaryPercentText?.text = loc("$perc%")
            circularProgressBar?.progress = perc
            summaryCorrectCount?.text = loc("$correct")
            summaryIncorrectCount?.text = loc("${total - correct}")

            performanceContainer?.removeAllViews()
            val sortedConcepts = conceptStats.keys.sorted()

            sortedConcepts.forEach { concept ->
                val stats = conceptStats[concept] ?: Pair(0, 0)
                val cCorrect = stats.first
                val cTotal = stats.second
                val cPerc = if (cTotal > 0) (cCorrect * 100) / cTotal else 0

                val row = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, 12, 0, 12)
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE); cornerRadius = 16f
                        setStroke(1, Color.parseColor("#F0F2F5"))
                    }
                    val lp = android.widget.LinearLayout.LayoutParams(-1, -2)
                    lp.setMargins(0, 0, 0, 12)
                    layoutParams = lp
                    setPadding(16, 12, 16, 12)
                }

                val header = android.widget.LinearLayout(this@MainActivity).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val nameLabel = TextView(this@MainActivity).apply {
                    text = loc(concept)
                    textSize = 12f; setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#1A1A2E"))
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                }

                // Badge
                val badge = TextView(this@MainActivity).apply {
                    val isGood = cPerc >= 80
                    text = if (isGood) "✓ Good" else "OK"
                    textSize = 10f; setTypeface(null, Typeface.BOLD)
                    setPadding(8, 2, 8, 2)
                    setTextColor(if (isGood) Color.parseColor("#2E7D32") else Color.parseColor("#1A2560"))
                    background = GradientDrawable().apply {
                        cornerRadius = 12f
                        setColor(if (isGood) Color.parseColor("#E8F5E9") else Color.parseColor("#E8EAF6"))
                    }
                }

                val scoreLabel = TextView(this@MainActivity).apply {
                    text = loc("$cCorrect/$cTotal")
                    textSize = 10f; setTextColor(Color.parseColor("#909BA6"))
                    setPadding(8, 0, 0, 0)
                }

                header.addView(nameLabel); header.addView(badge); header.addView(scoreLabel)

                // Progress Bar
                val progressContainer = android.widget.FrameLayout(this@MainActivity).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(-1, (6 * resources.displayMetrics.density).toInt()).apply {
                        setMargins(0, 8, 0, 0)
                    }
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F0F2F5")); cornerRadius = 8f
                    }
                }

                val progressFill = View(this@MainActivity).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(0, -1)
                    background = GradientDrawable().apply {
                        setColor(if (cPerc >= 80) Color.parseColor("#2E7D32") else Color.parseColor("#2B3A8C"))
                        cornerRadius = 8f
                    }
                    post {
                        val lp = layoutParams; lp.width = (progressContainer.width * (cPerc / 100f)).toInt()
                        layoutParams = lp
                    }
                }

                progressContainer.addView(progressFill)
                row.addView(header); row.addView(progressContainer)
                performanceContainer?.addView(row)
            }
        }

        fun showMastered() {
            btnRow1?.visibility = View.GONE
            btnRow2?.visibility = View.GONE
            cardBorder?.background = normalBorder
            questionText.setTextColor(Color.parseColor("#2B3A8C"))
            questionText.text = loc(" Mastered!")
            
            lifecycleScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(2000)
                showSummary()
            }
        }

        fun loadQuestion() {
            answerButtons.forEach { it.isEnabled = false }

            lifecycleScope.launch(Dispatchers.Default) {

                // ── All engine reads MUST happen on Dispatchers.Default ──────
                // ── to avoid data races with submitAnswer() ──────────────────

                // ── All mastered ────────────────────────────────────────────
                if (engine.consumeAllMastered()) {
                    withContext(Dispatchers.Main) { showMastered() }
                    return@launch
                }

                // ── Boundary found → "Practice Starts" alert ───────────────
                val boundary = engine.consumeBoundary()

                if (boundary != null) {
                    // Log K-BOUNDARY (file I/O is fine on Default)
                    sessionLogger.logKBoundary(boundary.toList())

                    if (engine.consumeAllMastered()) {
                        withContext(Dispatchers.Main) { showMastered() }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        btnRow1?.visibility = View.GONE
                        btnRow2?.visibility = View.GONE
                        cardBorder?.background = normalBorder
                        questionText.setTextColor(Color.parseColor("#2B3A8C"))
                        questionText.text = loc("\n\nPractice Starts")
                    }
                    kotlinx.coroutines.delay(5000)
                    withContext(Dispatchers.Main) {
                        btnRow1?.visibility = View.VISIBLE
                        btnRow2?.visibility = View.VISIBLE
                    }
                    // Re-enter loadQuestion() via Main to avoid deep recursion on Default
                    withContext(Dispatchers.Main) { loadQuestion() }
                    return@launch
                }

                // ── Generate next question (already on Default) ────────────
                val (question, answers) = engine.generateQuestion()

                if (engine.consumeAllMastered()) {
                    withContext(Dispatchers.Main) { showMastered() }
                    return@launch
                }

                val currentConcept = engine.currentKCName

                withContext(Dispatchers.Main) {
                    cardBorder?.background = normalBorder
                    questionText.setTextColor(Color.parseColor("#1A1A2E"))
                    questionText.text = loc(question)
                    scoreText?.text = loc("Q: ${total + 1}")

                    conceptsContainer?.removeAllViews()
                    val tv = TextView(this@MainActivity).apply {
                        text = loc(currentConcept)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(12, 4, 12, 4)
                        setTextColor(Color.parseColor("#2B3A8C"))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 8f
                            setStroke(4, Color.parseColor("#2B3A8C"))
                            setColor(Color.TRANSPARENT)
                        }
                    }
                    conceptsContainer?.addView(tv)

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
                            // Capture question metadata before submitAnswer
                            val qKCName = engine.currentKCName
                            val qKCId = engine.currentKCId
                            val qText = engine.lastQuestionText
                            val qCorrectAns = engine.correctAnswer
                            val qNum1 = engine.lastNum1
                            val qNum2 = engine.lastNum2
                            val isAssessment = engine.currentPhase == AdaptiveEngine.Phase.ASSESSMENT
                            val qNo = if (isAssessment) engine.detectionQuestionNo else engine.practiceQuestionNo

                            lifecycleScope.launch(Dispatchers.Default) {
                                // submitAnswer on Default — it does CUSUM/BKT computation
                                engine.submitAnswer(answers[i])

                                // Track stats for summary
                                val stats = conceptStats.getOrDefault(qKCName, Pair(0, 0))
                                conceptStats[qKCName] = Pair(stats.first + (if (ok) 1 else 0), stats.second + 1)

                                // CSV logging (file I/O fine on Default)
                                val misconception = if (!ok) {
                                    DistractorGenerator.getMisconception(qKCId, qNum1, qNum2, qCorrectAns, answers[i])
                                } else ""

                                if (isAssessment) {
                                    sessionLogger.logDetection(qNo, qKCName, qText, qCorrectAns, answers[i], ok, misconception)
                                } else {
                                    sessionLogger.logPractice(qNo, qKCName, qText, qCorrectAns, answers[i], ok, misconception)
                                }

                                // Consume events — all on Default (safe, no race)
                                engine.consumeMasteryEvent()?.let { evt ->
                                    sessionLogger.logMastery(qNo, evt.conceptName, evt.correctnessRecord)
                                    masteredLevels.add(evt.conceptName)
                                }
                                engine.consumeZpdUpdate()?.let { added ->
                                    sessionLogger.logZpdUpdate(added)
                                }

                                // Check if all mastered
                                if (engine.consumeAllMastered()) {
                                    withContext(Dispatchers.Main) {
                                        total++
                                        if (ok) correct++
                                        showMastered()
                                    }
                                    return@launch
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
                                }
                                kotlinx.coroutines.delay(900)
                                // Switch to Main before calling loadQuestion() to reset the stack
                                withContext(Dispatchers.Main) { loadQuestion() }
                            }
                        }
                    }
                }
            }
        }

        startBtn.setOnClickListener {
            if (selectedOp == null) return@setOnClickListener
            engine.startSession(selectedOp!!, selectedDigitMode ?: 1)
            
            // Start CSV logging session
            sessionLogger.startSession()
            val lang = if (isMarathi) "Marathi" else "English"
            val opName = when (selectedOp) {
                "+" -> "Addition"; "-" -> "Subtraction"
                "×" -> "Multiplication"; "÷" -> "Division"
                else -> selectedOp ?: ""
            }
            sessionLogger.logSettings(lang, opName, selectedDigitMode ?: 1)

            correct = 0; total = 0
            conceptStats.clear()
            masteredLevels.clear()
            homeLayout.visibility = View.GONE
            quizLayout.visibility = View.VISIBLE
            loadQuestion()
        }

        finishBtn?.setOnClickListener { showSummary() }

        navHome?.setOnClickListener {
            sessionLogger.endSession()
            quizLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }
        summaryNavHome?.setOnClickListener {
            summaryLayout.visibility = View.GONE
            homeLayout.visibility = View.VISIBLE
        }

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

        // Default to Level 1
        selectDigit(1)
    }
}
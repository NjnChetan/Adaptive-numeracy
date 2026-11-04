# Adaptive Practices of Numeracy Skill — Full Testing Context

Audience: a fresh chat whose job is **to test this app**. Code arrives via git; this file is intent + exact expected behavior + test targets + suspected bugs. Treat sections marked **EXACT** as the spec derived from current code. Where code and intent might differ, it is flagged as a **SUSPECT** (§14) — verify, don't assume.

---

## 1. What the app is

- Offline Kotlin Android app, package `com.example.p1`. Personalized math tutor for primary-school kids.
- Target: low-resource classrooms, one teacher / many students, mixed levels. No internet, no accounts, no backend.
- **4 independent student panels on one screen.** `MainActivity` holds `List(4){AdaptiveEngine()}`; each panel has its own engine, language, selected op/level, score, concept stats. Tablet: 4 students sit around it, each panel rotatable to face its student. Phone: panels toggle 0°/180°.
- Bilingual: English / Marathi (per-panel toggle; digits become Devanagari).
- Dual purpose: working educational tool AND portfolio/interview project (must be robust, explainable).
- Pilot-testing: sessions logged to CSV for later analysis (§10).
- Owner: Chetan (MCA student, targeting QA/test-automation roles). Prefers brief, direct communication; report findings concisely, give exact file/function names.

## 2. Testing goals (priority order)

1. **Wrong math / wrong or duplicate answer options** (worst bug).
2. Adaptive-logic errors: assessment trees, ZPD, bandit selection, mastery detection, unlocking, state bleed.
3. Crashes / hangs / stuck screens.
4. Logging correctness (it is the pilot's data output).
5. UI/UX: flows, rotation, Marathi, layout on phone/tablet.
6. Polish.

## 3. User flow (EXACT)

Per panel, Home screen (`homeLayout`):
1. **Select Level**: buttons 1 / 2 / 3 (`btn1Digit/2/3`). Default = 1 preselected on panel creation.
2. **Select Operation**: + − × ÷. **Start is disabled until an op is selected** (grey `#8A99CC`; enabled `#2B3A8C`).
3. Language switch (`langToggle`, Marathi when checked).
4. **Start** → `engine.startSession(op, level)`, `sessionLogger.startSession()`, `logSettings(lang, opName, level)`; counters reset; `quizLayout` visible; `loadQuestion()`.
5. Quiz screen: question card, 4 option buttons (2 rows), concept chip, `Q: n` score label, Finish button, nav Home, nav Rotate.
6. **Finish** → summary screen (`summaryLayout`): score `c/t`, percent, circular progress, correct count, incorrect count, per-concept rows. Summary Home → home.
7. Quiz nav Home → home (calls `sessionLogger.endSession()`).
8. Home has "⟳ Rotate" (`homeNavRotate`); quiz has `navRotate`.

Answer feedback: tap option → all options disabled → correct: green border, wait **900 ms**; wrong: red border, question text replaced by correct answer in red, wait **2500 ms** → next question.

### Screen-state messages (all timings EXACT)
| Event | Message (EN) | Duration | Then |
|---|---|---|---|
| Level 3 first load (phase ASSESSMENT & detectionQuestionNo==0) | "\n\nLet's test what you know!" | 4000 ms | first assessment question |
| Assessment finished (boundary found) | "\n\nPractice Starts" | 5000 ms | first practice question |
| KC mastered | overlay card "Well Done!" + "You learned this!" (fade 300 ms, card anim 400 ms) | 2500 ms | next question |
| All KCs mastered via practice | "🎉 All concepts mastered!" | 2000 ms | home (counters reset) |
| Assessment proves all mastered | "Super! You have learned it all!" (green) | 3000 ms | home (calls `endSession()`, counters reset) |

Both "all mastered" paths **skip the summary screen**.

## 4. Levels, phases, active KCs (EXACT: `AdaptiveEngine.activeKCIds()`)

| Op | Level 1 | Level 2 | Level 3 |
|---|---|---|---|
| + | 1,2 | 1–6 | 1–9 |
| − | 10,11 | 10,11,12,13,15 (14 excluded) | 10–17 |
| × | 18,19 | 20,21,22,23,24 | 25,26,27,28 |
| ÷ | 29,30,31 | 32,33,34 | 35,36,37 |

- Level 1 & 2 start directly in `Phase.LEARNING`. **Level 3 starts in `Phase.ASSESSMENT`** (boundary detection) then switches to LEARNING.
- Prerequisites outside the active set count as mastered (`masteredInLevel`). E.g. × L2: 20 has prereq 18 (outside) → treated mastered → initial ZPD = {20}. ÷ L3: initial ZPD = {35}. × L3: initial ZPD = {25}.
- Boundary assessment is the headline Level-3 feature; must never silently disappear.

## 5. Knowledge-component graph (EXACT: `KnowledgeModel.kt`)

Names shown in UI chip = node code (e.g. `2A1C`). Full name in logs/`KnowledgeRepository`.

### Addition (1–9)
| ID | Code | Meaning | Prereqs |
|---|---|---|---|
| 1 | 1A | 1d+1d no carry | — |
| 2 | 1AC | 1d+1d carry | 1 |
| 3 | 2A1 | 2d+1d no carry | 1 |
| 4 | 2A1C | 2d+1d carry | 2,3 |
| 5 | 2A2 | 2d+2d no carry | 3 |
| 6 | 2A2C | 2d+2d double carry | 4 |
| 7 | 3A | 3d+3d no carry | 5 |
| 8 | 3AC | 3d+2d single carry | 4,7 |
| 9 | 3AC2 | 3d+3d ≥2 carries | 8 |

### Subtraction (10–17)
| ID | Code | Meaning | Prereqs |
|---|---|---|---|
| 10 | 1S | 1d no borrow | — |
| 11 | 2S1 | 2d−1d no borrow | 10 |
| 12 | 2S1B | 2d−1d borrow | 11 |
| 13 | 2S2 | 2d−2d no borrow | 11 |
| 14 | 3S | 3d no borrow | 13 |
| 15 | 2S2B | 2d−2d borrow | 12 |
| 16 | 3SB | 3d single borrow | 14,15 |
| 17 | 3SB2 | 3d ≥2 borrows | 16 |

### Multiplication (18–28)
| ID | Code | Meaning | Prereqs |
|---|---|---|---|
| 18 | T5 | tables up to 5 | — |
| 19 | T10 | tables 6–9 | 18 |
| 20 | 2M1 | 2d×1d no carry | 18 |
| 21 | 2M1C | 2d×1d carry | 19,20 |
| 22 | 3M1 | 3d×1d no carry | 20 |
| 23 | 3M1C | 3d×1d single carry | 21,22 |
| 24 | 3M1C2 | 3d×1d ≥2 carries | 23 |
| 25 | 2M2 | 2d×2d no carry | 24 |
| 26 | 2M2C | 2d×2d carry | 25 |
| 27 | 3M2 | 3d×2d no carry | 25 |
| 28 | 3M2C | 3d×2d carry | 27,26 |

### Division (29–37) — answer = quotient (floor)
| ID | Code | Meaning | Prereqs |
|---|---|---|---|
| 29 | 2D1 | 2d÷1d exact | — |
| 30 | 3D1 | 3d÷1d exact | 29 |
| 31 | 2D1R | 2d÷1d remainder | 29 |
| 32 | 3D1R | 3d÷1d remainder | 30,31 |
| 33 | 3D1Z | 3d÷1d, zero in quotient | 32 |
| 34 | 4D1R | 4d÷1d remainder | 32 |
| 35 | 3D2 | 3d÷2d exact | 30 |
| 36 | 3D2R | 3d÷2d remainder | 35,32 |
| 37 | 4D2R | 4d÷2d remainder | 36,34 |

`getOperationType(id)`: 10–17 "-", 18–28 "×", 29–37 "÷", else "+".

KC fields `lowTransition/highTransition/guessProbability/slipProbability` (and `fpr`, `beta` in the engine) are **vestigial** — no BKT/CUSUM math uses them now. Don't test them for behavior.

## 6. Question generation (EXACT: `generateRawNumbersForKC`)

Display: `"$num1 $sym $num2 = ?"`, sym ∈ `+ − × ÷`. Panel formatting: portrait → stacked (`␣28` / `+5`, right-aligned), landscape → inline `28+5`. Logged question text = `"$num1$op$num2"` with ASCII `-` for subtraction, e.g. `28+5`, `45-9`, `12×3`, `84÷4`.

`nDigit(n)` = uniform n-digit number (no leading zero). `randDigits(n,maxD)` = n digits each uniform 1..maxD (never 0).

### Addition
| KC | num1 | num2 | Predicate |
|---|---|---|---|
| 1 | 1..9 | 1..9 | sum < 10 |
| 2 | 1..9 | 1..9 | sum ≥ 10 |
| 3 | 2-digit | 1..9 | no carry |
| 4 | 2-digit | 1..9 | has carry |
| 5 | 2-digit | 2-digit | no carry |
| 6 | 2-digit | 2-digit | carryCount == 2 |
| 7 | 3-digit | 3-digit | no carry |
| 8 | 3-digit | 2-digit | carryCount == 1 |
| 9 | 3-digit | 3-digit | carryCount ≥ 2 |

### Subtraction
| KC | num1 | num2 | Predicate |
|---|---|---|---|
| 10 | 2..9 | 1..9 | b < a |
| 11 | 2-digit | 1..9 | no borrow |
| 12 | 2-digit | 1..9 | borrow, a−b ≥ 1 |
| 13 | 2-digit | 2-digit | no borrow, a>b, a−b ≥ 20 |
| 14 | 3-digit | 1–3 digit (random length) | no borrow, a>b, (b<100 or a−b ≥ 100) |
| 15 | 2-digit | 2-digit | borrow, a>b |
| 16 | 3-digit | 1–3 digit | borrowCount == 1, a>b, gap rule |
| 17 | 3-digit | 1–3 digit | borrowCount ≥ 2, a>b, gap rule |

### Multiplication
| KC | Generation |
|---|---|
| 18 | one factor 2..5, other 2..9, random order (no ×1) |
| 19 | both factors 6..9 |
| 20 | b ∈ 2..4; a = 2 digits each in 1..(9/b) (every digit product <10, no zero digit) |
| 21 | 2-digit × (2..9); ones digit ≠ 0; mulCarryCount ≥ 1 |
| 22 | b ∈ 2..3; a = 3 digits each in 1..(9/b) (no carry, no zero) |
| 23 | 3-digit × (2..9); ones ≠ 0; mulCarryCount == 1 |
| 24 | 3-digit × (2..9); ones ≠ 0; mulCarryCount ≥ 2 |
| 25 | randDigits(2,3) × randDigits(2,3); every long-multiplication column sum ≤ 9 |
| 26 | 2-digit × 2-digit; neither a multiple of 10; columns do carry |
| 27 | randDigits(3,3) × randDigits(2,3); columns no carry |
| 28 | 3-digit × 2-digit; neither multiple of 10; columns do carry |

`mulCarryCount` counts every column where `digit*b + carry ≥ 10` (including the leading column).

### Division (answer = `a / b` integer division)
| KC | a | b | Predicate |
|---|---|---|---|
| 29 | 2-digit | 2..9 | a%b==0 and q ≥ 2 |
| 30 | 3-digit | 2..9 | a%b==0 |
| 31 | 2-digit | 2..9 | a%b≠0 and q ≥ 2 |
| 32 | 3-digit | 2..9 | a%b≠0 |
| 33 | 3-digit | 2..9 | quotient has a trailing zero (10≤q<1000, q%10==0) OR a zero tens digit (q≥100, (q/10)%10==0). May be exact OR with remainder |
| 34 | 4-digit | 2..9 | a%b≠0 |
| 35 | 3-digit | 2-digit | b%10≠0, a%b==0, q ≥ 2 |
| 36 | 3-digit | 2-digit | b%10≠0, a%b≠0, q ≥ 2 |
| 37 | 4-digit | 2-digit | b%10≠0, a%b≠0, q ≥ 10 |

### Generator mechanics
- `safePair` retries up to **10 000** times; if still failing it logs `"gave up after 10000 attempts"` and **returns the last (invalid) pair**. Test hard KCs for this (25, 27, 33, 12, 13, 14, 16, 17, 35).
- **No-repeat window** (last 5 pairs) only for KCs **1, 2, 10, 18, 19** (up to 100 retries). For + and × a∘b = b∘a counts as repeat; for − order matters.
- Correct answer: `+`, `-` (num1−num2, never negative), `×`, `÷` (floor quotient). Remainder KCs show **no remainder instruction** (see §14).

## 7. Distractors (EXACT: `DistractorGenerator`)

Guarantees (`generate()`): exactly **3 distractors**, all ≥ 0, ≠ correct, distinct; shuffled together with the correct answer → **4 unique options**. If short, filled with `correct+1, +2, …`. Test: never fewer than 4 unique options, never negative, correct appears exactly once.

Addition (`a = max, b = min`):
- KC1: correct ±1, ±2. KC3: ±1 + wrong-place-value. KC5/7: multi-digit off-by-one (±1 at each digit place) + wrong place value.
- KC2,4,6,8,9: **carryConfusion** (digit sums mod 10 with carries dropped; correct+10; correct−10) + off-by-one variants.
- Wrong place value: `num1 + num2*10^k` for k up to digit-length difference.

Subtraction:
- KC10: ±1,±2 and **num1+num2** (add instead of subtract). KC11: wrong place value (`num1 − num2×10/100`, `num2−num1`) + ±1 + num1+num2.
- KC12,15,16,17: **borrowConfusion** (per-digit |d1−d2| ignoring borrow; correct+10; correct−10; num1+num2) + multi-digit off-by-one (+ wrong place value for 16,17).
- KC13,14: num1+num2 + multi-digit off-by-one + wrong place value.

Multiplication:
- KC18,19: table neighbours (`a(b±1)`, `(a±1)b`), `a+b`, ±10, ±1.
- KC20–28 (`multiplicationDistractors` + `pickMul`): prefer distractors with **same last digit AND same digit count** as the correct answer: forgot-every-carry product, carry dropped / counted twice (`±co*place*10`), 2-digit multiplier: forgot shift, over-shift (×100), dropped carries variants, plus `correct ± 10/20/100/200`. Fallbacks relax constraints in steps. Test: the correct answer's last digit is NOT unique among options (for KC 20–28) whenever possible.

Division (`divisionDistractors`): rounded-up (q+1 when remainder), q±1, ±2, ±10, ±100, dropped zero, zero→1, ×10, ÷10; same digit-length as quotient preferred.

### Misconception labels (`getMisconception`, used in CSV, EXACT order)
- "" if correct.
- × → `getMisconceptionForOp("×")`: `Add-instead-of-multiply`, `Forgot-shift`, `Over-shift`, `Forgot-carry` (single-digit multiplier no-carry product), `Table-neighbour`, `Carry/Place-error` (diff multiple of 10), `Off-by-one`, else `Other-error`.
- ÷: `Rounded-up` (remainder case, selected == q+1), `Dropped-zero`, else `Multiply-instead-of-divide`, `Subtract-instead-of-divide`, `Returned-divisor`, `Off-by-one`, `Place-value-error`, `Other-error`.
- −: `Add-instead-of-subtract` (selected = num1+num2); `Wrong-place-value`; for KC 12,15,16,17: diff ±10/±100 → `Borrow-not-reduced` / `Extra-borrow`; `Forgot-borrow` check near the end.
- +: `Subtract-instead-of-add`; for KC 2,4,6,8,9: diff −10/−100 → `Forgot-carry`, +10/+100 → `Extra-carry`.
- Then generic: `Off-by-one`, `Off-by-two`, `Carry/Borrow-error` (|diff| 10 or 100), `Single-digit-error` (same length, one digit differs), `Other-error`.

## 8. Adaptive engine behavior (EXACT)

### 8.1 Boundary assessment (Level 3 only)
State machine in `BoundaryDetector`: response string of `1`(pass)/`0`(fail) per node, one tree per operation. Result is `Ask(kcId)` or `Terminal(solvable, unsolvable, boundary)`.

**Per-node protocol (`updateAssessmentNode`)**: first answer wrong → node FAIL immediately. First answer correct → **a second question on the same KC** is asked (`pendingConfirmKC`); second correct → PASS, second wrong → FAIL. Only then is `1`/`0` appended to the path. So a passing node costs 2 questions, failing node 1 or 2 (fail on confirm).

Assessment questions: not counted in score/summary, counter `detectionQuestionNo` (increments for every question incl. confirmations), logged as `DETECTION`, UI chip is purple "🔍 Boundary Test • <KC>". The `Q:` label does not change.

On Terminal (`handleTerminalAssessment`):
- `newlyFoundBoundary` = boundary KC names (shown via `K-BOUNDARY` log; includes KCs outside the level filter for ×/÷ — see §14).
- `toPremaster` = solvable KCs that are not boundary, **plus** boundary KCs whose in-level prereqs are all in `solvable`. Each is marked mastered, arm + detector removed. Boundary KCs not premastered: arms/detectors removed (then `initZPD()` re-adds ZPD arms).
- Phase → LEARNING, `initZPD()`. If every in-level KC is mastered → `newlyAllMastered` + `assessmentProvedAllMastered` → "Super!" screen.
- UI: "Practice Starts" 5 s then practice.

Terminal states are **not applied to unsolvable KCs** — they stay unmastered and become ZPD when their prereqs are mastered.

#### Addition tree (`getAdditionState`) — root Ask(4)
Format: `path → Ask(kc)` or `path → T solvable | unsolvable | boundary`.
```
""      → Ask 4
1       → Ask 7
11      → Ask 6
111     → Ask 8
1111    → Ask 9
11111   → T {1-9} | {} | {9}
11110   → T {1-8} | {9} | {8}
1110    → T {1-7} | {8,9} | {4,7}
110     → Ask 8
1101    → Ask 9
11011   → T {1,2,3,4,5,7,8,9} | {6} | {4}
11010   → T {1,2,3,4,5,7,8} | {6,9} | {4,8}
1100    → T {1,2,3,4,5,7} | {6,8,9} | {4,7}
10      → Ask 5
101     → Ask 6
1011    → T {1-6} | {7,8,9} | {4,5}
1010    → T {1-5} | {6,7,8,9} | {4,5}
100     → Ask 6
1001    → T {1,2,3,4,6} | {5,7,8,9} | {3,4}
1000    → T {1,2,3,4} | {5-9} | {3,4}
0       → Ask 3
01      → Ask 2
011     → Ask 5
0111    → Ask 7
01111   → T {1,2,3,5,7} | {4,6,8,9} | {2,3,7}
01110   → T {1,2,3,5} | {4,6,7,8,9} | {2,3,5}
0110    → T {1,2,3} | {4-9} | {2,3}
010     → Ask 5
0101    → Ask 7
01011   → T {1,3,5,7} | {2,4,6,8,9} | {1,3,7}
01010   → T {1,3,5} | {2,4,6,7,8,9} | {1,3,5}
0100    → T {1,3} | {2,4-9} | {1,3}
00      → Ask 1
001     → Ask 2
0011    → T {1,2} | {3-9} | {1,2}
0010    → T {1} | {2-9} | {1}
000     → T {} | {1-9} | {1}
```

#### Subtraction tree — root Ask(12)
```
""      → Ask 12
1       → Ask 14
11      → Ask 16
111     → Ask 17
1111    → T {10-17} | {} | {17}
1110    → T {10-16} | {17} | {16}
110     → Ask 15
1101    → T {10,11,12,13,14,15} | {16,17} | {14,15}
1100    → T {10,11,12,13,14} | {15,16,17} | {12,14}
10      → Ask 13
101     → Ask 15
1011    → T {10,11,12,13,15} | {14,16,17} | {13,15}
1010    → T {10,11,12,13} | {14,15,16,17} | {12,13}
100     → Ask 15
1001    → T {10,11,12,15} | {13,14,16,17} | {11,15}
1000    → T {10,11,12} | {13-17} | {11,12}
0       → Ask 11
01      → Ask 13
011     → Ask 14
0111    → T {10,11,13,14} | {12,15,16,17} | {11,14}
0110    → T {10,11,13} | {12,14,15,16,17} | {11,13}
010     → T {10,11} | {12-17} | {11}
00      → Ask 10
001     → T {10} | {11-17} | {10}
000     → T {} | {10-17} | {10}
```

#### Multiplication tree — root Ask(23)
```
""      → Ask 23
1       → Ask 26
11      → Ask 27
111     → Ask 28
1111    → T {18-28} | {} | {}
1110    → T {18-27} | {28} | {26,27}
110     → T {18-26} | {27,28} | {25,26}
10      → Ask 25
101     → Ask 27
1011    → T {18-25,27} | {26,28} | {25,27}
1010    → T {18-25} | {26,27,28} | {25}
100     → Ask 24
1001    → T {18-24} | {25-28} | {24}
1000    → T {18-23} | {24-28} | {23}
0       → Ask 19
01      → Ask 21
011     → Ask 22
0111    → T {18-22} | {23-28} | {21,22}
0110    → T {18-21} | {22-28} | {20,21}
010     → Ask 20
0101    → Ask 22
01011   → T {18,19,20,22} | {21,23-28} | {19,20,22}
01010   → T {18,19,20} | {21-28} | {19,20}
0100    → T {18,19} | {20-28} | {18,19}
00      → Ask 20
001     → Ask 22
0011    → T {18,20,22} | {19,21,23-28} | {18,20,22}
0010    → T {18,20} | {19,21-28} | {18,20}
000     → Ask 18
0001    → T {18} | {19-28} | {18}
0000    → T {} | {18-28} | {18}
```

#### Division tree — root Ask(32)
```
""      → Ask 32
1       → Ask 36
11      → Ask 33
111     → Ask 34
1111    → Ask 37
11111   → T {29-37} | {} | {}
11110   → T {29-36} | {37} | {36,34}
1110    → T {29-33,35,36} | {34,37} | {32,36}
110     → Ask 34
1101    → Ask 37
11011   → T {29-32,34-37} | {33} | {32}
11010   → T {29-32,34,35,36} | {33,37} | {32,36,34}
1100    → T {29-32,35,36} | {33,34,37} | {32,36}
10      → Ask 35
101     → Ask 33
1011    → Ask 34
10111   → T {29-35} | {36,37} | {35,32,34}
10110   → T {29-33,35} | {34,36,37} | {35,32}
1010    → Ask 34
10101   → T {29-32,34,35} | {33,36,37} | {35,32,34}
10100   → T {29-32,35} | {33,34,36,37} | {35,32}
100     → Ask 33
1001    → Ask 34
10011   → T {29-34} | {35,36,37} | {30,32,34}
10010   → T {29-33} | {34-37} | {30,32}
1000    → Ask 34
10001   → T {29-32,34} | {33,35,36,37} | {30,32,34}
10000   → T {29-32} | {33-37} | {30,32}
0       → Ask 30
01      → Ask 31
011     → Ask 35
0111    → T {29,30,31,35} | {32,33,34,36,37} | {31,30,35}
0110    → T {29,30,31} | {32-37} | {31,30}
010     → Ask 35
0101    → T {29,30,35} | {31-34,36,37} | {29,30,35}
0100    → T {29,30} | {31-37} | {29,30}
00      → Ask 29
001     → Ask 31
0011    → T {29,31} | {30,32-37} | {29,31}
0010    → T {29} | {30-37} | {29}
000     → T {} | {29-37} | {29}
```
Any response string not in the tree throws `IllegalArgumentException` — walk all reachable strings in tests.

### 8.2 ZPD
`getZPD` = active KCs not mastered whose in-level prereqs are all mastered, sorted by ID. After every terminal, ZPD should be non-empty unless everything is mastered (deadlock check).

### 8.3 Concept selection (LEARNING)
- Called **every practice question** (`chooseKC` in `generateLearningQuestion`): ZPD arms ensured, `bandit.selectConcept(zpd, practiceQuestionNo)`.
- Priority: arms with `timesPlayed == 0` (first in ZPD order) → else max KL-UCB.
- KL-UCB: `t = max(ts − timeAdded, 2)`, `val = ln(1 + t·ln²t) / timesPlayed`, UCB via KL root-finding (Halley with random start, binary-search fallback; returns 1.0 if `kl(est, 0.999) < val`). **Non-deterministic** (random start) but should converge to the same value.
- `focusKC / minFocus / frustrationLimit(12)`: focus tracking exists but doesn't lock the concept; 12 consecutive wrongs only resets counters (log warning).

### 8.4 Mastery (`CUSUMDetector`, actually a sliding window)
- Per-KC detector created when arm added; `correctnessRecord` appended per answer.
- Needs **n ≥ 5** attempts; then mastered if **≥4 of last 5 correct** OR **last 4 all correct**. (Streak shortcut can't fire before n=5 because of the minAttempts guard.)
- `getProgress` = correct-in-last-5 / 5 (unused by UI).
- On mastery (`onMastery`): `MasteryEvent` (logged `MASTERY` with the full C/NC record), KC marked mastered, arm/detector removed, children whose in-level prereqs all mastered are added to ZPD (log `ZPD-UPDATE`), all-mastered check.

### 8.5 Learning-phase completion
Zero ZPD and all level KCs mastered → `newlyAllMastered` → "🎉 All concepts mastered!" → home. (`generateLearningQuestion` still builds one question from `filterIds.last()` before the UI notices.)

## 9. UI spec (EXACT where listed)

### Layout/behavior
- Edge-to-edge; top/bottom white bars sized from status/nav bars (10 dp fallback).
- Question card: monospace bold auto-size 24–160 sp. Border: normal grey `#E0E0E0`, correct green `#2E7D32`, wrong red `#C62828`.
- Option font: ≥5 digits 13 sp, 4 → 15, 3 → 17, else 19.
- Concept chip: practice = blue `#2B3A8C` chip with KC code; assessment = purple badge "🔍 Boundary Test • <code>".
- Score label: `Q: <total+1>` (practice questions only), in Marathi "प्रश्न: …".
- Op grid: 2×2 in portrait panel, 1×4 in landscape panel; rebuilt on rotation.
- Rotation: tablet (smallestScreenWidthDp ≥ 600) cycles 0/90/180/270° (default landscape); phone toggles 0°/180° (default portrait). Question text reformats (stacked vs inline) on rotate.
- Summary: concept rows sorted alphabetically by code; badge "✓ Good" if ≥80% else "OK"; progress bar green ≥80 else blue; only **practice** answers counted.
- `isPhone` decides rotation behavior; all four panels are independent.

### Marathi translations (`loc()`, EXACT)
Select Level→पातळी निवडा · Select Operation→क्रिया निवडा · Start→सुरू करा · ⟳ Rotate→⟳ फिरवा · Finish→संपवा · Excellent performance!→उत्कृष्ट कामगिरी! · Score→गुण · Correct→बरोबर · Incorrect→चूक · How you did by topic→विषयानुसार तुमची कामगिरी · ⌂ Home→⌂ मुख्य पान · Mastered!→प्राविण्य मिळवले! · Practice Starts→सराव सुरू · ✓ Good→छान · OK→ठीक आहे · 🔍 Boundary Test→🔍 सीमा चाचणी · "Well Done! "→"शाब्बास! " · You learned this!→तुम्ही हे शिकलात! · Now let's try something new.→आता नवीन काही करूया. · 🎉 All concepts mastered!→🎉 सर्व संकल्पना शिकल्या! · Super! You have learned it all!→खूप छान! तुम्ही सर्व शिकलात! · Let's test what you know!→चला, तुमचे ज्ञान तपासूया! · `Q: n`→`प्रश्न: n`. All digits (questions, options, scores) → Devanagari (०–९). Language toggle only refreshes **static** texts, not the question currently on screen.

## 10. Session logging (EXACT: `SessionLogger`)

- Directory: app-specific external storage `Android/data/com.example.p1/files/session_logs/`; file `session_yyyyMMdd_HHmmss.csv`. Pull with `adb pull /sdcard/Android/data/com.example.p1/files/session_logs`.
- Header: `log-type,timestamp,field-1,…,field-7` (rows padded to 9 columns; timestamp `yyyy-MM-dd HH:mm:ss`; CSV-escaped).
- Rows:
  - `SETTINGS`: language (`English`/`Marathi`), operation (`Addition/Subtraction/Multiplication/Division`), level.
  - `PRACTICE` / `DETECTION`: questionNo, concept code, question (`28+5`), correctAnswer, answerSelected, `C`/`NC`, misconception. Assessment questions (incl. confirmations) → `DETECTION`; practice → `PRACTICE`.
  - `MASTERY`: practice questionNo, concept code, record like `NC|C|C|C|C`.
  - `ZPD-UPDATE`: unlocked concepts `A|B`.
  - `K-BOUNDARY`: boundary concept codes `A|B`.
- Expected: one file per Start, chronological, every answered question logged exactly once, misconception labelled for wrong answers only (blank for correct).
- `endSession()` is called from: Home (quiz), Finish→summary, "Super!" screen, and `onDestroy`. Not called from `showMastered()`.

## 11. Per-panel vs shared state

- Per panel: engine, language, selected op/level, `correct/total`, `conceptStats`, rotation, `masteredLevels`.
- **Shared**: one `SessionLogger` for all four panels (single writer/file).

## 12. Test plan

### A. Math & options (JVM unit tests, ≥500–1000 samples per KC)
- Operands satisfy §6 predicates; correct answer = operation result; no negatives for −.
- 4 unique, non-negative options; correct present once. For KC 20–28 same digit-count/last-digit property.
- No-repeat window for KC 1,2,10,18,19. Commutative handling for +,×.
- Watch for `"gave up after 10000 attempts"` in logs.
- `getMisconception` returns expected label for each synthetic wrong answer type.

### B. Assessment trees
- Enumerate every response path for + − × ÷: no `IllegalArgumentException`, terminals partition the op's KCs (`solvable ∪ unsolvable` = all KCs of op, disjoint, boundary ⊆ solvable or the root-fail case), prerequisite-closed solvable sets.
- Simulate student profiles (all pass / fail root / mixed) at Level 3 for each op; verify confirmation-question protocol (correct → same KC again; wrong → immediate fail), `DETECTION` rows, boundary log, "Practice Starts", premastering result, resulting ZPD, no deadlock.
- All-pass terminal → "Super!" screen for each op (+ `11111`, − `1111`, × `1111`, ÷ `11111`).

### C. Learning loop
- Mastery triggers exactly per §8.4 (4/5 and last-4 streak, never before 5 attempts); `MASTERY` record matches answers.
- Unlock order matches graph; `ZPD-UPDATE` correct; multi-prereq nodes (4,8,16,21,23,28,32,36,37) unlock only when all in-level prereqs mastered.
- Bandit variety: never-played arms first, no concept starved, no infinite single-KC lock.
- Full completion for every op × level (1,2,3) → "All concepts mastered".
- Restart after completion/Home: all state reset (mastered set, arms, detectors, recent pairs, counters).

### D. UI / flows
- Every op × level start; Start disabled with no op; default level 1.
- Finish → summary numbers correct (practice only), badge thresholds, Home returns cleanly.
- Rotation (tablet 4 steps / phone 2 steps), mid-question, mid-overlay, during 900/2500 ms delays; op grid layout switch; stacked vs inline question.
- Rapid taps (options disabled after first tap), Home mid-delay then Start again, Finish mid-delay.
- Marathi toggle before Start, mid-session, per panel; overlay text; digits.
- Four panels running simultaneously with different ops/levels/languages.

### E. Logging
- Real file inspection for each scenario above; check rows, order, escaping, multi-panel behavior.

### F. Robustness
- Backgrounding, device rotation / Activity recreation (engines are plain fields; state lost), low-end/small phone, large tablet, rapid repeated sessions.

## 13. Suggested scenarios (scripted)
1. **L1 + happy path**: answer all correct → mastery of 1 after 5, then 2; completion message.
2. **L2 − mixed**: wrong answers on borrow questions; verify misconception labels `Borrow-not-reduced`, `Forgot-borrow`, `Add-instead-of-subtract`.
3. **L3 + all fail**: path `000` → boundary {1}, ZPD={1}.
4. **L3 + all pass** (2 correct per node) → Super.
5. **L3 × / ÷ weak student**: fail root (23 / 32) and deeper; check what is practiced (see §14 #3).
6. **Mastery boundary**: pattern C,C,C,C,NC (should not master before 5th); NC,C,C,C,C (master at 5); C,C,C,C then … (check minAttempts vs streak).
7. **Four-panel concurrency** with logging.
8. **Marathi full run** each op.

## 14. SUSPECTS / likely bugs (verify first)

1. **Shared logger, 4 panels**: `startSession()` replaces the writer without closing the old one (file-handle leak); any panel's `endSession()` nulls the writer so **other panels' later rows are silently dropped**; rows carry no panel/student id, so interleaved sessions are indistinguishable.
2. **Marathi gaps**: `loc("Well Done!")` vs key `"Well Done! "` (trailing space) → overlay title stays English; badge text ("✓ Good"/"OK") not passed through `loc` in summary; concept codes get `toMarathi` (mixed Devanagari digits + Latin letters, e.g. `२A१`); level buttons and op buttons are not translated; toggle doesn't re-render the current question.
3. **Level 3 × and ÷ weak-student gap**: L3 active sets are {25–28} / {35–37}, lower KCs count as mastered via `masteredInLevel`. A student who fails T10/3M1C etc. in the assessment still gets 2M2/3D2 practice, with no remedial lower-level practice; K-BOUNDARY logs include out-of-level KCs (e.g. {21,22}).
4. **Remainder questions**: answer = floor quotient, prompt shows only `a ÷ b = ?` (KC 31,32,34,36,37; 33 sometimes) — ambiguous for students.
5. **Mastery streak shortcut** can't fire at n=4 (minAttempts guard first); memory notes claim it does — confirm intent.
6. **Bandit clock mismatch**: arms created with `timeAdded = ts` (engine counter, increments on *every* answer incl. assessment) but selection uses `practiceQuestionNo`. After Level 3 assessment `ts − timeAdded` can be ≤0 → clamped to 2 → UCB bonus artificially small/flat. Also `chooseKC` runs before `practiceQuestionNo++` (off-by-one).
7. **No tie-break shuffle** in `selectConcept` (`maxByOrNull`, unplayed-first both pick lowest ID); notes say a shuffle fix existed. Also no starvation guard (notes mention lag ≥3). Test ZPD with ≥2 concepts for always-lowest-ID bias.
8. **Pending coroutines not cancelled**: delayed `loadQuestion()` / "all mastered" reset can fire after Home/Finish or after a new Start (stale UI, double load, reset counters of the new session).
9. **Generator fallback** returns predicate-violating pairs after 10 000 failed tries (check KC 25/27/33/13/14/16/17).
10. **Terminal with empty boundary** (× `1111`, ÷ `11111`): `newlyFoundBoundary` is an empty set (non-null) → verify no crash, correct "Super!" path, no stray "Practice Starts".
11. **KC 33** can produce an exact division (no remainder) or remainder; naming says "with 0 in quotient" only.
12. Vestigial code: guess/slip/transition fields, `beta`, `fpr`, `kcAncestors`, `focusKC` — dead/unused; overview docs mention BKT but there's no BKT in code.
13. `Log.d/Log.i` in engine: JVM unit tests touching `AdaptiveEngine` need `unitTests.isReturnDefaultValues = true` or Robolectric.
14. Level 3 + tree anomalies to double-check against the intended design: + `1110` boundary {4,7} and `1100` boundary {4,7} look duplicated/odd; boundary sets in ÷ (e.g. `11110` → {36,34}) include nodes on different branches.

## 15. Tooling & source map

- Android Studio, Kotlin, Gradle: `./gradlew assembleDebug | assembleRelease | testDebugUnitTest`. GitHub Actions: CI on push/PR; release workflow on tag with keystore signing.
- Prefer JVM unit tests for generators, distractors, trees, detector, ZPD; device/emulator (phone + tablet) for UI, rotation, logging; `adb logcat` tags: `AdaptiveSystem`, `KLUCBBandit`, `SessionLogger`.
- Files: `AdaptiveEngine.kt` (phases, ZPD, assessment handling, mastery, generation), `BoundaryAssessmentEngine.kt` (`BoundaryDetector`, trees, ID↔code maps), `KnowledgeModel.kt` (KC graph, ZPD), `Klucbbandit.kt` (KL-UCB), `Cusumdetector.kt` (sliding window), `Distractorgenerator.kt` (options + misconception labels), `SessionLogger.kt` (CSV), `MainActivity.kt` (all UI, 4 panels; layouts `activity_main`, `question_panel.xml`).
- Key view ids: `panel1–4`, `clip1–4`, `panelContent`, `homeLayout`, `quizLayout`, `summaryLayout`, `btn1Digit/2/3`, `btnAdd/Sub/Mul/Div`, `startBtn`, `langToggle`, `questionText`, `option1–4`, `cardBorder`, `conceptsContainer`, `scoreText`, `finishBtn`, `masteryOverlay/masteryCard`.

## 16. Bug report format

Op, level, KC, panel, language, steps, expected vs actual, CSV excerpt / logcat. Tags: `[MATH]`, `[ADAPTIVE]`, `[UI]`, `[LOG]`, `[CRASH]`, `[I18N]`. Keep it short; include file + function name.

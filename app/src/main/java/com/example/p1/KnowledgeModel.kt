package com.example.p1


data class KnowledgeComponent(
    val id: Int,
    val name: String,
    val prerequisites: List<Int>,
    val lowTransition: Double,
    val highTransition: Double,
    val guessProbability: Double,
    val slipProbability: Double
)

object KnowledgeRepository {

    val components = mapOf(

        1 to KnowledgeComponent(1,
            "One digit addition without overflow",
            emptyList(),
            0.3, 0.3, 0.2, 0.05),

        2 to KnowledgeComponent(2,
            "One digit addition with overflow",
            listOf(1),
            0.01, 0.3, 0.2, 0.05),

        3 to KnowledgeComponent(3,
            "Two digit addition without carry and no overflow",
            listOf(1),
            0.01, 0.3, 0.1, 0.05),

        4 to KnowledgeComponent(4,
            "Two digit addition without carry and overflow",
            listOf(2,3),
            0.01, 0.3, 0.1, 0.05),

        5 to KnowledgeComponent(5,
            "Two digit addition with carry and no overflow",
            listOf(2),
            0.01, 0.2, 0.1, 0.075),

        6 to KnowledgeComponent(6,
            "Two digit addition with carry and overflow",
            listOf(5),
            0.01, 0.2, 0.1, 0.075),

        7 to KnowledgeComponent(7,
            "Three digit addition without carry and no overflow",
            listOf(3),
            0.01, 0.3, 0.05, 0.075),

        8 to KnowledgeComponent(8,
            "Three digit addition without carry and overflow",
            listOf(4,7),
            0.01, 0.1, 0.05, 0.075),

        9 to KnowledgeComponent(9,
            "Three digit addition with carry and no overflow",
            listOf(5),
            0.01, 0.15, 0.05, 0.1),

        10 to KnowledgeComponent(10,
            "Three digit addition with carry and overflow",
            listOf(9),
            0.01, 0.15, 0.05, 0.1),

        11 to KnowledgeComponent(11,
            "Four digit addition without carry and no overflow",
            listOf(7),
            0.01, 0.1, 0.02, 0.1),

        12 to KnowledgeComponent(12,
            "Four digit addition without carry and overflow",
            listOf(8,11),
            0.01, 0.2, 0.02, 0.1),

        13 to KnowledgeComponent(13,
            "Four digit addition with carry and no overflow",
            listOf(9),
            0.01, 0.1, 0.02, 0.15),

        14 to KnowledgeComponent(14,
            "Four digit addition with carry and overflow",
            listOf(13),
            0.01, 0.1, 0.02, 0.15)
    )
}
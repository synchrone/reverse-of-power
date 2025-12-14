package com.game.remoteclient.models

data class QuizQuestion(
    val questionText: String,
    val answers: List<String>,
    val timeLimit: Int = 30
) {
    init {
        require(answers.size == 4) { "Quiz must have exactly 4 answers" }
    }
}

data class QuizAnswer(
    val questionId: String,
    val answerIndex: Int,
    val timestamp: Long = System.currentTimeMillis()
)

package com.example.ai.model

sealed class AiIntent {
    object Transaction : AiIntent()
    object Query : AiIntent()
    object Unknown : AiIntent()
}
